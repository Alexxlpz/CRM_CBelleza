package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.DuplicateClientException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.CenterForm;
import com.alexxlpz.crm_cbelleza.forms.NewClientForm;
import com.alexxlpz.crm_cbelleza.forms.ProfileForm;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.AgendaBlockService;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.InventoryService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errores de la revisión del 26 nov 2026: el aviso de stock bajo daba error al guardarse, campos de texto sin tope,
 * la ficha de un cliente que había borrado su cuenta seguía abierta y el alta manual abría a otro cliente con el
 * mismo teléfono diciendo «dado de alta». Sin {@code @Transactional}, como en la aplicación: así salen los errores
 * de carga perezosa fuera de la transacción.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewNov26Tests {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private InventoryRepository inventories;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private CenterService centerService;
    @Autowired
    private UserService userService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private AgendaBlockService agendaBlocks;
    @Autowired
    private ClientCardService clientCards;

    /** Clientes creados aquí: al acabar se dan por borrados para que su teléfono no choque con otros tests. */
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void forgetCreatedClients() {
        for (Long id : created) {
            users.findById(id).ifPresent(client -> {
                client.setName("Cliente eliminado");
                client.setPhone(null);
                client.setEmail(null);
                client.setDeletedAt(LocalDateTime.now());
                users.save(client);
            });
        }
    }

    private User carlos() {
        return users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
    }

    private Long centerId() {
        return carlos().getCenter().getId();
    }

    /** Un móvil español al azar (612 345 678), para no coincidir con otros clientes de la cartera. */
    private static String randomPhone() {
        return "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private Long newClient(String name, String phone, String email) {
        Long id = clientCards.createClientManually(centerId(), new NewClientForm(name, phone, email, null), carlos().getId())
                .clientId();
        created.add(id);
        return id;
    }

    /* ---------- Aviso de stock bajo ---------- */

    @Test
    void lowStockAlertSavesWithoutError() throws Exception {
        Inventory item = inventories.findByCenterId(centerId()).getFirst();
        Integer original = item.getLowStockAlert();
        try {
            mvc.perform(post("/worker/inventory/" + item.getId() + "/low-stock").param("units", "3").with(csrf())
                            .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("successMessage", containsString("3 unidades o menos")));
            assertEquals(3, inventories.findById(item.getId()).orElseThrow().getLowStockAlert());
        } finally {
            inventoryService.setLowStockAlert(centerId(), item.getId(), original);
        }
    }

    /* ---------- Topes de texto: mensaje en vez de error 500 ---------- */

    @Test
    void longTextsAreRejectedWithAMessage() {
        Long centerId = centerId();
        String centerName = carlos().getCenter().getName();

        BusinessRuleException name = assertThrows(BusinessRuleException.class, () -> centerService.updateCenter(centerId,
                new CenterForm("C".repeat(121), null, null, null, null, null, null, null)));
        assertTrue(name.getMessage().contains("120"));
        BusinessRuleException address = assertThrows(BusinessRuleException.class, () -> centerService.updateCenter(centerId,
                new CenterForm(null, "Calle ".repeat(50), null, null, null, null, null, null)));
        assertTrue(address.getMessage().contains("255"));
        assertEquals(centerName, centerService.getCenterById(centerId).getName());

        User sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        assertThrows(BusinessRuleException.class, () -> userService.updateProfile(sofia.getId(),
                new ProfileForm("S".repeat(101), null, null, null, null)));
        assertEquals(sofia.getName(), users.findById(sofia.getId()).orElseThrow().getName());

        assertThrows(BusinessRuleException.class, () -> newClient("N".repeat(121), randomPhone(), null));

        LocalDate monday = LocalDate.now(MADRID).plusWeeks(20).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        BusinessRuleException reason = assertThrows(BusinessRuleException.class, () -> agendaBlocks.create(centerId,
                carlos().getId(), null, true, monday.atTime(9, 0).toString(), monday.atTime(10, 0).toString(), "R".repeat(256)));
        assertTrue(reason.getMessage().contains("255"));

        Center center = carlos().getCenter();
        Appointment request = bookingService.requestBooking(sofia.getId(), center.getId(),
                treatments.findByCenterId(center.getId()).getFirst().getId(), LocalDateTime.of(monday, LocalTime.of(11, 0)));
        try {
            BusinessRuleException note = assertThrows(BusinessRuleException.class,
                    () -> bookingService.reject(center.getId(), request.getId(), "M".repeat(501)));
            assertTrue(note.getMessage().contains("500"));
        } finally {
            appointments.deleteById(request.getId());
        }
    }

    /* ---------- Cliente que ha borrado su cuenta ---------- */

    @Test
    void deletedClientCardCannotBeOpened() throws Exception {
        Long id = newClient("Clienta Que Se Va", randomPhone(), null);
        mvc.perform(get("/worker/clients/" + id).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());

        User client = users.findById(id).orElseThrow();
        client.setName("Cliente eliminado");
        client.setPhone(null);
        client.setDeletedAt(LocalDateTime.now());
        users.save(client);

        mvc.perform(get("/worker/clients/" + id).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/worker/clients/" + id + "/card").param("healthConsent", "true").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isNotFound());
        assertThrows(ResourceNotFoundException.class, () -> clientCards.requireClientOfCenter(centerId(), id));
    }

    /* ---------- Alta manual con el teléfono o el correo de alguien de la cartera ---------- */

    @Test
    void manualClientWithTheSamePhoneAsks() throws Exception {
        String phone = randomPhone();
        Long existing = newClient("Marta López", phone, null);
        // El teléfono se guarda como en el registro
        assertEquals("+34 " + phone.substring(0, 3) + " " + phone.substring(3, 6) + " " + phone.substring(6),
                users.findById(existing).orElseThrow().getPhone());

        // Escrito de otra forma sigue siendo el mismo: no se da de alta y se avisa con quién coincide
        DuplicateClientException duplicate = assertThrows(DuplicateClientException.class,
                () -> newClient("Sofía López", "+34 " + phone, null));
        assertEquals(existing, duplicate.getClientId());
        assertTrue(duplicate.isSamePhone());

        mvc.perform(post("/worker/clients/create").param("name", "Sofía López").param("phone", phone).with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(redirectedUrl("/worker/clients"))
                .andExpect(flash().attribute("duplicate", Map.of("id", existing, "name", "Marta López", "samePhone", true)));

        // «Dar de alta igualmente»: es otra persona con el mismo teléfono
        String location = mvc.perform(post("/worker/clients/create").param("name", "Sofía López").param("phone", phone)
                        .param("confirmDuplicate", "true").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("dado de alta")))
                .andReturn().getResponse().getRedirectedUrl();
        Long second = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
        created.add(second);
        assertNotEquals(existing, second);
        assertEquals("Sofía López", users.findById(second).orElseThrow().getName());
    }

    @Test
    void manualClientWithTheSameEmailAsks() {
        String email = "cliente." + System.nanoTime() + "@ejemplo.com";
        Long existing = newClient("Ana Ruiz", randomPhone(), email);
        DuplicateClientException duplicate = assertThrows(DuplicateClientException.class,
                () -> newClient("Ana R.", randomPhone(), email.toUpperCase()));
        assertEquals(existing, duplicate.getClientId());
        assertTrue(!duplicate.isSamePhone());
    }

    @Test
    void manualClientNeedsValidEmailAndPhone() {
        BusinessRuleException email = assertThrows(BusinessRuleException.class,
                () -> newClient("Lola Gil", randomPhone(), "lola-arroba-gmail"));
        assertTrue(email.getMessage().startsWith("Revisa el correo"));
        assertThrows(BusinessRuleException.class, () -> newClient("Lola Gil", "12", null));
        assertThrows(BusinessRuleException.class, () -> newClient("Lola Gil", "llámame", null));
    }
}
