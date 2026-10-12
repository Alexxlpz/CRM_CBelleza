package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.forms.NewClientForm;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un trabajador no puede ver ni modificar datos de otro centro (antes bastaba con cambiar el id en la URL). */
@SpringBootTest
@AutoConfigureMockMvc
class CenterIsolationTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private InventoryRepository inventory;
    @Autowired
    private ClientCardService clientCards;

    private Long centerOf(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getCenter().getId();
    }

    @Test
    void workerCannotApproveAppointmentOfAnotherCenter() throws Exception {
        Appointment foreign = appointments.findByCenterIdOrderByDateTimeDesc(centerOf(TestUsers.WORKER_CENTER_1)).getFirst();
        var statusBefore = appointments.findById(foreign.getId()).orElseThrow().getStatus();

        mvc.perform(post("/worker/appointments/" + foreign.getId() + "/approve").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().isNotFound());

        assertEquals(statusBefore, appointments.findById(foreign.getId()).orElseThrow().getStatus());
    }

    @Test
    void workerCannotWriteTreatmentSummaryOfAnotherCenter() throws Exception {
        Appointment foreign = appointments.findByCenterIdOrderByDateTimeDesc(centerOf(TestUsers.WORKER_CENTER_1)).getFirst();

        mvc.perform(post("/worker/appointments/" + foreign.getId() + "/summary").param("summary", "Intruso").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().isNotFound());

        assertEquals(null, appointments.findById(foreign.getId()).orElseThrow().getTreatmentSummary());
    }

    @Test
    void workerCannotChangeStockOfAnotherCenter() throws Exception {
        Inventory foreign = inventory.findByCenterId(centerOf(TestUsers.WORKER_CENTER_1)).getFirst();
        int stockBefore = foreign.getStock();

        mvc.perform(post("/worker/inventory/" + foreign.getId() + "/adjust-stock").param("change", "5").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/worker/inventory/" + foreign.getId() + "/delete").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().isNotFound());

        assertEquals(stockBefore, inventory.findById(foreign.getId()).orElseThrow().getStock());
    }

    @Test
    void workerCannotOpenClientCardOfAnotherCenter() throws Exception {
        Long clientOfCenter1 = clientCards.createClientManually(centerOf(TestUsers.WORKER_CENTER_1),
                new NewClientForm("Cliente Aislado", "+34 699 000 111", null, "Solo en el centro 1"), null).clientId();
        mvc.perform(get("/worker/clients/" + clientOfCenter1).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());
        mvc.perform(get("/worker/clients/" + clientOfCenter1).with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().isNotFound());
    }

    /** Fallo #3: dar de alta un cliente con el teléfono o correo de un cliente de otro centro no enlaza su cuenta. */
    @Test
    void manualClientNeverLinksAccountsOfOtherCenters() {
        var sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        // Un centro en el que Sofía no es clienta (ni ficha ni citas)
        Long center2 = java.util.stream.Stream.of(TestUsers.WORKER_CENTER_2, "marco@cbelleza.com", "ana@cbelleza.com")
                .map(this::centerOf)
                .filter(center -> appointments.findByCenterIdOrderByDateTimeDesc(center).stream()
                        .noneMatch(a -> a.getClient() != null && a.getClient().getId().equals(sofia.getId())))
                .findFirst()
                .orElseThrow();

        var created = clientCards.createClientManually(center2,
                new NewClientForm("Otra Persona", sofia.getPhone(), sofia.getEmail(), null), null);

        org.junit.jupiter.api.Assertions.assertNotEquals(sofia.getId(), created.clientId(), "se crea un cliente local");
        org.junit.jupiter.api.Assertions.assertTrue(created.emailSkipped(), "el correo en uso no se guarda");
        assertEquals("Otra Persona", users.findById(created.clientId()).orElseThrow().getName());
    }

    @Test
    void workerCanAdjustOwnStock() throws Exception {
        Inventory own = inventory.findByCenterId(centerOf(TestUsers.WORKER_CENTER_1)).getFirst();
        mvc.perform(post("/worker/inventory/" + own.getId() + "/adjust-stock").param("change", "1").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());
        assertEquals(own.getStock() + 1, inventory.findById(own.getId()).orElseThrow().getStock());
    }
}
