package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.NotificationDismissalRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.ClientNotificationService;
import com.alexxlpz.crm_cbelleza.services.ClientNotificationService.ClientNotification;
import com.alexxlpz.crm_cbelleza.services.NotificationDismissalService;
import com.alexxlpz.crm_cbelleza.services.NotificationKeys;
import com.alexxlpz.crm_cbelleza.services.NotificationService;
import com.alexxlpz.crm_cbelleza.services.NotificationService.NotificationItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Quitar avisos de la campana: la «x» de cada aviso y «Limpiar todas». Se recuerda por usuario,
 * y un aviso que cambia (u otro igual más adelante) vuelve a salir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationDismissTests {

    private static final String LUCIA = "lucia.romero@cbelleza.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private InventoryRepository inventory;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private NotificationService workerBell;
    @Autowired
    private ClientNotificationService clientBell;
    @Autowired
    private NotificationDismissalService dismissals;
    @Autowired
    private NotificationDismissalRepository dismissalRows;
    @Autowired
    private Clock clock;

    private User carlos;
    private User lucia;
    private Inventory product;
    private String soldOut;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        lucia = users.findByEmailIgnoreCase(LUCIA).orElseThrow();
        product = inventory.findByCenterId(carlos.getCenter().getId()).getFirst();
        product.setStock(0);
        inventory.saveAndFlush(product);
        soldOut = "¡Agotado! " + product.getProduct().getName();
    }

    private NotificationItem soldOutItem() {
        return workerBell.getNotificationsForCenter(carlos.getCenter().getId()).stream()
                .filter(n -> n.message().startsWith(soldOut)).findFirst().orElseThrow();
    }

    private List<NotificationItem> bellOf(User worker) {
        return dismissals.visible(worker.getId(), workerBell.getNotificationsForCenter(worker.getCenter().getId()), NotificationItem::key);
    }

    @Test
    void theBellHasClearAllAndADismissButtonPerNotification() throws Exception {
        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Limpiar todas")))
                .andExpect(content().string(containsString("data-notification-key=\"" + soldOutItem().key() + "\"")))
                .andExpect(content().string(containsString("data-dismiss-url=\"/worker/notifications/dismiss\"")))
                .andExpect(content().string(containsString("Marcar como visto")));
    }

    @Test
    void aWorkerDismissesOneNotificationOnlyForHimself() throws Exception {
        String key = soldOutItem().key();
        mvc.perform(post("/worker/notifications/dismiss").param("key", key)
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(1));

        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(key))));
        assertTrue(bellOf(carlos).stream().noneMatch(n -> n.key().equals(key)));
        // Su compañera del mismo centro lo sigue viendo
        assertTrue(bellOf(lucia).stream().anyMatch(n -> n.key().equals(key)));
    }

    @Test
    void clearAllEmptiesTheBell() throws Exception {
        List<String> keys = bellOf(carlos).stream().map(NotificationItem::key).toList();
        assertFalse(keys.isEmpty());
        mvc.perform(post("/worker/notifications/dismiss").param("key", keys.toArray(String[]::new))
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(keys.size()));

        assertTrue(bellOf(carlos).isEmpty());
        // Repetirlo no duplica nada
        assertEquals(0, dismissals.dismiss(carlos.getId(), keys));
    }

    @Test
    void aNotificationThatChangesComesBack() {
        dismissals.dismiss(carlos.getId(), List.of(soldOutItem().key()));
        assertTrue(bellOf(carlos).stream().noneMatch(n -> n.message().startsWith(soldOut)));

        // Repone y vuelve a bajar: «Stock bajo: quedan 2 ud» es otro aviso
        product.setStock(2);
        inventory.saveAndFlush(product);
        assertTrue(bellOf(carlos).stream().anyMatch(n -> n.message().startsWith("Stock bajo: quedan 2 ud de " + product.getProduct().getName())));
    }

    @Test
    void forgetsDismissalsOfNotificationsThatNoLongerExist() {
        String key = soldOutItem().key();
        dismissals.dismiss(carlos.getId(), List.of(key));
        product.setStock(50);
        inventory.saveAndFlush(product);

        bellOf(carlos);
        assertFalse(dismissalRows.findKeysByUserId(carlos.getId()).contains(key));

        // Si se vuelve a agotar, el aviso sale otra vez
        product.setStock(0);
        inventory.saveAndFlush(product);
        assertTrue(bellOf(carlos).stream().anyMatch(n -> n.key().equals(key)));
    }

    @Test
    void invalidKeysAreIgnoredAndEachRoleUsesItsOwnPath() throws Exception {
        mvc.perform(post("/worker/notifications/dismiss").param("key", "no-es-una-huella", "' OR 1=1")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(0));
        mvc.perform(post("/worker/notifications/dismiss").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(0));
        mvc.perform(post("/client/notifications/dismiss").param("key", soldOutItem().key())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/worker/notifications/dismiss").param("key", soldOutItem().key())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isForbidden());
        assertTrue(dismissalRows.findKeysByUserId(carlos.getId()).isEmpty());
    }

    @Test
    void aClientDismissesAReminderFromHisBell() throws Exception {
        User sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        Center center = carlos.getCenter();
        Treatment treatment = treatments.findByCenterId(center.getId()).getFirst();
        LocalDateTime tomorrow = CenterClock.now(clock, center).toLocalDate().plusDays(1).atTime(12, 0);
        appointments.saveAndFlush(Appointment.builder()
                .dateTime(tomorrow).treatment(treatment).center(center).client(sofia)
                .status(AppointmentStatus.CONFIRMED).price(treatment.getPrice()).createdAt(tomorrow.minusDays(3))
                .build());
        ClientNotification reminder = clientBell.forClient(sofia.getId()).items().stream()
                .filter(n -> n.message().startsWith("Mañana a las 12:00 tienes " + treatment.getName())).findFirst().orElseThrow();
        assertEquals(NotificationKeys.of(reminder.message(), reminder.url()), reminder.key());

        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-notification-key=\"" + reminder.key() + "\"")))
                .andExpect(content().string(containsString("data-dismiss-url=\"/client/notifications/dismiss\"")));

        mvc.perform(post("/client/notifications/dismiss").param("key", reminder.key())
                        .with(TestUsers.as(users, TestUsers.CLIENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(1));

        var bell = clientBell.forClient(sofia.getId());
        assertTrue(bell.items().stream().noneMatch(n -> n.key().equals(reminder.key())));
        assertEquals(bell.items().stream().filter(ClientNotification::unseen).count(), bell.unseen());
    }
}
