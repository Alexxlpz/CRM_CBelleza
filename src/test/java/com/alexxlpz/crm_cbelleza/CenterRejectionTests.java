package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.forms.NewClientForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rechazar un alta desde el enlace de administración: se borra el centro con su equipo y lo que hayan preparado. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CenterRejectionTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CenterRegistrationService registrationService;

    @Autowired
    private ClientCardService clientCards;

    @Autowired
    private CenterRepository centers;

    @Autowired
    private UserRepository users;

    @Autowired
    private TreatmentRepository treatments;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void approvalPageOffersToReject() throws Exception {
        var registration = register();
        mvc.perform(get("/register-center/approve/" + registration.approvalToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/register-center/approve/" + registration.approvalToken() + "/reject")))
                .andExpect(content().string(containsString("Rechazar registro")));
    }

    @Test
    void rejectingDeletesTheCenterItsTeamAndWhatTheyPrepared() throws Exception {
        long centersBefore = centers.count();
        long usersBefore = users.count();
        var registration = register();
        Long centerId = registration.centerId();
        Center center = centers.findById(centerId).orElseThrow();
        Long ownerId = users.findByEmailIgnoreCase("alba@estudioalba.es").orElseThrow().getId();

        // Lo que el equipo pudo preparar antes de la revisión
        Treatment treatment = treatments.save(Treatment.builder().name("Manicura rusa").price(30.0).duration(60)
                .type(TreatmentType.MANICURA_PEDICURA).center(center).build());
        jdbc.update("INSERT INTO products (name, price, center_id) VALUES ('Crema propia', 12.0, ?)", centerId);
        Long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
        jdbc.update("INSERT INTO inventories (center_id, product_id, stock) VALUES (?, ?, 3)", centerId, productId);
        Long manualClient = clientCards.createClientManually(centerId,
                new NewClientForm("Marta Apuntada", "+34 600 999 888", null, "Piel sensible"), ownerId).clientId();
        jdbc.update("INSERT INTO appointments (date_time, treatment_id, center_id, client_id, worker_id, status, price) "
                        + "VALUES (?, ?, ?, ?, ?, 'CONFIRMED', 30.0)",
                LocalDateTime.now().plusDays(2), treatment.getId(), centerId, manualClient, ownerId);
        // Una clienta con cuenta propia también tiene ficha aquí: su cuenta no es del centro
        Long sofia = TestUsers.details(users, TestUsers.CLIENT).getId();
        jdbc.update("INSERT INTO client_cards (center_id, client_id) VALUES (?, ?)", centerId, sofia);

        mvc.perform(post("/register-center/approve/" + registration.approvalToken() + "/reject").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Registro rechazado")))
                .andExpect(content().string(containsString("Estudio Alba")));

        assertFalse(centers.existsById(centerId));
        assertTrue(users.findByEmailIgnoreCase("alba@estudioalba.es").isEmpty());
        assertTrue(users.findByEmailIgnoreCase("nerea@estudioalba.es").isEmpty());
        assertFalse(users.existsById(manualClient), "la clienta apuntada a mano solo era de este centro");
        assertTrue(users.existsById(sofia), "las cuentas de clientes se quedan");
        assertEquals(centersBefore, centers.count());
        assertEquals(usersBefore, users.count());
        for (String table : List.of("treatments", "inventories", "appointments", "client_cards", "products",
                "center_opening_hours")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centerId),
                    table);
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_invitations WHERE user_id = ?", Integer.class, ownerId));

        // El enlace ya no vale: ni para verlo, ni para publicarlo, ni para rechazarlo otra vez
        mvc.perform(get("/register-center/approve/" + registration.approvalToken())).andExpect(status().isNotFound());
        mvc.perform(post("/register-center/approve/" + registration.approvalToken()).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/register-center/approve/" + registration.approvalToken() + "/reject").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPublishedCenterCannotBeRejected() throws Exception {
        var registration = register();
        registrationService.publish(registration.approvalToken());
        mvc.perform(post("/register-center/approve/" + registration.approvalToken() + "/reject").with(csrf()))
                .andExpect(status().isNotFound());
        assertTrue(centers.existsById(registration.centerId()));
    }

    @Test
    void aWrongLinkRejectsNothing() throws Exception {
        long before = centers.count();
        mvc.perform(post("/register-center/approve/no-es-un-token/reject").with(csrf())).andExpect(status().isNotFound());
        assertEquals(before, centers.count());
    }

    private CenterRegistrationService.Registration register() {
        return registrationService.register(new CenterRegistrationForm("Estudio Alba", "B12345674", "34", "611 223 344",
                "Calle Mayor 12, 28013 Madrid", 40.4155, -3.7074, "Europe/Madrid", 1,
                TestHours.DAYS, TestHours.FROM, TestHours.TO, "Alba Ruiz", "alba@estudioalba.es",
                List.of("Nerea Gil"), List.of("nerea@estudioalba.es"), "OFF", 1, 100, null, List.of(), null, true));
    }
}
