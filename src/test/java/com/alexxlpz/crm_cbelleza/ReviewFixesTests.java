package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.DashboardService;
import com.alexxlpz.crm_cbelleza.services.InventoryService;
import com.alexxlpz.crm_cbelleza.services.NotificationService;
import com.alexxlpz.crm_cbelleza.services.NotificationService.NotificationItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errores de la revisión del 16 nov 2026: solicitudes sin contestar cuya hora ya pasó, choque al aceptar una propuesta
 * y el botón «+» del inventario en el tope.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewFixesTests {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private InventoryRepository inventories;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private TransactionTemplate transactions;

    private User worker(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    private User newClient() {
        String id = UUID.randomUUID().toString();
        return users.save(User.builder().name("Clienta " + id).email(id + "@ejemplo.com").password("x")
                .role(Role.CLIENT).build());
    }

    private Appointment save(User client, Center center, LocalDateTime at, AppointmentStatus status) {
        Treatment treatment = treatments.findByCenterId(center.getId()).getFirst();
        return appointments.saveAndFlush(Appointment.builder()
                .dateTime(at).treatment(treatment).center(center).client(client)
                .status(status).price(treatment.getPrice()).workerMessage("").createdAt(LocalDateTime.now())
                .build());
    }

    /** Un lunes lejano a la hora indicada, para no chocar con las citas de ejemplo. */
    private static LocalDateTime farMonday(int weeksAhead, int hour) {
        LocalDate monday = LocalDate.now(MADRID).plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return LocalDateTime.of(monday, LocalTime.of(hour, 0));
    }

    /* ---------- Solicitudes que nadie contestó ---------- */

    @Test
    void pastUnansweredRequestIsNotCountedNorAccepted() {
        User carlos = worker(TestUsers.WORKER_CENTER_1);
        Center center = carlos.getCenter();
        int pendingBefore = dashboardService.statsFor(center.getId()).pendingAppointments();
        List<String> alertsBefore = notificationService.getNotificationsForCenter(center.getId()).stream()
                .map(NotificationItem::message).toList();

        Appointment past = save(newClient(), center, LocalDateTime.now(MADRID).minusDays(2).withNano(0), AppointmentStatus.PENDING);
        try {
            // Ni el contador del panel ni la campana la cuentan (igual que «Por aprobar», que nunca la enseñaba)
            assertEquals(pendingBefore, dashboardService.statsFor(center.getId()).pendingAppointments());
            assertEquals(alertsBefore, notificationService.getNotificationsForCenter(center.getId()).stream()
                    .map(NotificationItem::message).toList());
            assertTrue(dashboardService.pendingFor(center).stream().noneMatch(p -> p.id().equals(past.getId())));

            // No se acepta ni se le proponen horas
            BusinessRuleException approve = assertThrows(BusinessRuleException.class,
                    () -> bookingService.approve(center.getId(), past.getId(), carlos.getId(), carlos.getId(), null));
            assertTrue(approve.getMessage().contains("ya ha pasado"));
            assertThrows(BusinessRuleException.class, () -> bookingService.propose(center.getId(), past.getId(),
                    carlos.getId(), List.of(farMonday(10, 11).toString()), null));

            // En los historiales sale como «Sin respuesta», no como «Pendiente»
            Appointment reloaded = appointments.findById(past.getId()).orElseThrow();
            assertTrue(reloaded.isUnanswered());
            assertEquals("Sin respuesta", reloaded.getStatusLabel());
            assertEquals("badge-cancelled", reloaded.getStatusBadge());
            assertEquals(AppointmentStatus.PENDING, reloaded.getStatus());
        } finally {
            appointments.deleteById(past.getId());
        }
    }

    @Test
    void futureRequestStillCountsAsPending() {
        Center center = worker(TestUsers.WORKER_CENTER_1).getCenter();
        int pendingBefore = dashboardService.statsFor(center.getId()).pendingAppointments();
        Appointment future = save(newClient(), center, farMonday(12, 10), AppointmentStatus.PENDING);
        try {
            assertEquals(pendingBefore + 1, dashboardService.statsFor(center.getId()).pendingAppointments());
            assertFalse(future.isUnanswered());
            assertEquals("Pendiente", future.getStatusLabel());
            assertEquals("badge-pending", future.getStatusBadge());
        } finally {
            appointments.deleteById(future.getId());
        }
    }

    @Test
    void clientHistoryShowsUnansweredRequest() throws Exception {
        User client = newClient();
        Center center = worker(TestUsers.WORKER_CENTER_1).getCenter();
        Appointment past = save(client, center, LocalDateTime.now(MADRID).minusDays(3).withNano(0), AppointmentStatus.PENDING);
        try {
            mvc.perform(get("/client/appointments").with(TestUsers.as(users, client.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Sin respuesta")));
        } finally {
            appointments.deleteById(past.getId());
        }
    }

    /* ---------- Aceptar una propuesta ---------- */

    @Test
    void acceptingAProposalChecksTheClientIsFree() {
        User carlos = worker(TestUsers.WORKER_CENTER_1);
        Long centerId = carlos.getCenter().getId();
        User client = newClient();
        Appointment request = bookingService.requestBooking(client.getId(), centerId,
                treatments.findByCenterId(centerId).getFirst().getId(), farMonday(14, 10));
        LocalDateTime proposed = farMonday(14, 12);
        bookingService.propose(centerId, request.getId(), carlos.getId(), List.of(proposed.toString()), null);
        // Mientras tanto, la clienta ha cogido cita en otro centro justo a esa hora
        Appointment elsewhere = save(client, worker(TestUsers.WORKER_CENTER_2).getCenter(), proposed, AppointmentStatus.CONFIRMED);
        Long proposalId = transactions.execute(tx ->
                appointments.findById(request.getId()).orElseThrow().getProposals().getFirst().getId());

        BusinessRuleException clash = assertThrows(BusinessRuleException.class,
                () -> bookingService.acceptProposal(client.getId(), request.getId(), proposalId));
        assertTrue(clash.getMessage().startsWith("Ya tienes una cita a esa hora"));
        assertEquals(AppointmentStatus.PROPOSED, appointments.findById(request.getId()).orElseThrow().getStatus());

        // Si anula la otra, ya puede aceptarla
        elsewhere.setStatus(AppointmentStatus.CANCELLED);
        appointments.saveAndFlush(elsewhere);
        bookingService.acceptProposal(client.getId(), request.getId(), proposalId);
        assertEquals(AppointmentStatus.CONFIRMED, appointments.findById(request.getId()).orElseThrow().getStatus());
    }

    /* ---------- Inventario en el tope ---------- */

    @Test
    void incrementButtonIsOffAtTheStockLimit() throws Exception {
        Long centerId = worker(TestUsers.WORKER_CENTER_1).getCenter().getId();
        Inventory item = inventories.findByCenterId(centerId).getFirst();
        int original = item.getStock();
        item.setStock(InventoryService.MAX_STOCK);
        inventories.saveAndFlush(item);
        try {
            String html = mvc.perform(get("/worker/inventory").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            int button = html.indexOf("class=\"btn-adjust btn-increment\" data-id=\"" + item.getId() + "\"");
            assertTrue(button > 0);
            assertTrue(html.substring(button, html.indexOf('>', button)).contains("disabled"));

            mvc.perform(post("/worker/inventory/" + item.getId() + "/adjust-stock").param("change", "-1")
                            .with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock").value(InventoryService.MAX_STOCK - 1))
                    .andExpect(jsonPath("$.maxStock").value(InventoryService.MAX_STOCK));
        } finally {
            Inventory restored = inventories.findById(item.getId()).orElseThrow();
            restored.setStock(original);
            inventories.saveAndFlush(restored);
        }
    }
}
