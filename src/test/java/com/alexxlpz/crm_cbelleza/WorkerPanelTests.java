package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.ClientVisitSummary;
import com.alexxlpz.crm_cbelleza.services.DashboardService;
import com.alexxlpz.crm_cbelleza.services.DashboardService.TodayAppointment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Panel del trabajador: «Hoy», barra con los apartados del panel, resumen de la ficha y «Darle cita». */
@SpringBootTest
@AutoConfigureMockMvc
class WorkerPanelTests {

    private static final String LUCIA = "lucia.romero@cbelleza.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private DashboardService dashboardService;

    private User worker(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    private User newClient() {
        return users.save(User.builder().name("Clienta " + UUID.randomUUID())
                .email(UUID.randomUUID() + "@ejemplo.com").role(Role.CLIENT).build());
    }

    /** Hoy en la hora del centro (Madrid) a la hora dada, para que no dependa de la zona del equipo que pasa los tests. */
    private static LocalDateTime todayAt(int hour, int minute) {
        return LocalDateTime.of(LocalDate.now(ZoneId.of("Europe/Madrid")), LocalTime.of(hour, minute));
    }

    private Appointment save(User client, User worker, LocalDateTime at, AppointmentStatus status) {
        Center center = worker.getCenter();
        Treatment treatment = treatments.findByCenterId(center.getId()).getFirst();
        return appointments.saveAndFlush(Appointment.builder()
                .dateTime(at).treatment(treatment).center(center).client(client).worker(worker)
                .status(status).price(treatment.getPrice()).workerMessage("").createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    void todayListShowsTheDaysAppointmentsWithALinkToTheCard() throws Exception {
        User client = newClient();
        save(client, worker(TestUsers.WORKER_CENTER_1), todayAt(23, 30), AppointmentStatus.CONFIRMED);

        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"hoy\"")))
                .andExpect(content().string(containsString(client.getName())))
                .andExpect(content().string(containsString("/worker/clients/" + client.getId())));
    }

    @Test
    void todayCountsNoShowsAndLeavesOutCancelledAndOtherCenters() {
        User carlos = worker(TestUsers.WORKER_CENTER_1);
        User client = newClient();
        save(client, carlos, todayAt(23, 0).minusDays(30), AppointmentStatus.NO_SHOW);
        Appointment today = save(client, carlos, todayAt(23, 30), AppointmentStatus.CONFIRMED);
        Appointment cancelled = save(newClient(), carlos, todayAt(23, 0), AppointmentStatus.CANCELLED);
        Appointment otherCenter = save(newClient(), worker(TestUsers.WORKER_CENTER_2), todayAt(23, 0), AppointmentStatus.CONFIRMED);

        List<TodayAppointment> list = dashboardService.todayFor(carlos.getCenter());
        TodayAppointment line = list.stream().filter(a -> a.id().equals(today.getId())).findFirst().orElseThrow();
        assertEquals(1, line.clientNoShows());
        assertEquals("Confirmada", line.statusLabel());
        assertTrue(list.stream().noneMatch(a -> a.id().equals(cancelled.getId())));
        assertTrue(list.stream().noneMatch(a -> a.id().equals(otherCenter.getId())));
        // Por hora
        for (int i = 1; i < list.size(); i++) {
            assertTrue(!list.get(i).start().isBefore(list.get(i - 1).start()));
        }
    }

    /** La trabajadora estándar empieza viendo solo las suyas; el dueño, todo el centro. */
    @Test
    void standardWorkerStartsWithHerOwnAppointments() throws Exception {
        save(newClient(), worker(TestUsers.WORKER_CENTER_1), todayAt(23, 30), AppointmentStatus.CONFIRMED);

        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, LUCIA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-only-mine=\"true\"")))
                .andExpect(content().string(containsString("data-today-filter=\"mine\"")));
        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("data-only-mine=\"false\"")));
    }

    @Test
    void workerBarShowsPanelSectionsInsteadOfPublicLinks() throws Exception {
        mvc.perform(get("/worker/inventory").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/worker/calendar\" class=\"nav-menu-link\"")))
                .andExpect(content().string(containsString("aria-current=\"page\">Inventario</a>")))
                .andExpect(content().string(not(containsString("class=\"nav-menu-link\">Registra tu Centro"))));
        // La web pública sigue con sus enlaces
        mvc.perform(get("/centers"))
                .andExpect(content().string(containsString("Registra tu Centro")));
    }

    @Test
    void clientCardShowsSummaryAndBookButton() throws Exception {
        Long sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow().getId();
        mvc.perform(get("/worker/clients/" + sofia).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gasto total")))
                .andExpect(content().string(containsString("/worker/calendar?nuevaCita=" + sofia)));
    }

    /** El gasto total solo lo ve el dueño: a la trabajadora estándar le llega tapado, sin el importe en la página. */
    @Test
    void totalSpentIsHiddenFromStandardWorkers() throws Exception {
        User client = newClient();
        save(client, worker(TestUsers.WORKER_CENTER_1), todayAt(10, 0).minusDays(3), AppointmentStatus.COMPLETED);
        String price = String.format(java.util.Locale.ROOT, "%.2f",
                treatments.findByCenterId(worker(TestUsers.WORKER_CENTER_1).getCenter().getId()).getFirst().getPrice())
                .replace('.', ',') + " €";

        mvc.perform(get("/worker/clients/" + client.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString(">" + price + "</dd>")))
                .andExpect(content().string(not(containsString("*****,** €"))));
        mvc.perform(get("/worker/clients/" + client.getId()).with(TestUsers.as(users, LUCIA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("*****,** €")))
                .andExpect(content().string(not(containsString(">" + price + "</dd>"))));
    }

    /** «Darle cita» solo abre «Nueva cita» con clientes de la cartera del propio centro. */
    @Test
    void bookFromCardOnlyPreselectsClientsOfTheCenter() throws Exception {
        Long sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow().getId();
        mvc.perform(get("/worker/calendar").param("nuevaCita", sofia.toString())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CALENDAR_NEW_FOR = {\"id\":" + sofia + ",")));

        User stranger = newClient();
        save(stranger, worker(TestUsers.WORKER_CENTER_2), todayAt(10, 0).minusDays(3), AppointmentStatus.COMPLETED);
        mvc.perform(get("/worker/calendar").param("nuevaCita", stranger.getId().toString())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CALENDAR_NEW_FOR = null")))
                .andExpect(content().string(not(containsString(stranger.getName()))));
    }

    @Test
    void visitSummaryCountsCompletedVisitsAndPurchases() {
        Treatment manicure = Treatment.builder().name("Manicura").price(30.0).duration(45).build();
        Treatment facial = Treatment.builder().name("Facial").price(50.0).duration(60).build();
        LocalDate today = LocalDate.of(2026, 11, 14);
        List<Appointment> history = List.of(
                visit(manicure, today.minusDays(40), AppointmentStatus.COMPLETED, 30.0),
                visit(facial, today.minusDays(20), AppointmentStatus.COMPLETED, 50.0),
                visit(manicure, today.minusDays(5), AppointmentStatus.COMPLETED, 28.0),
                visit(facial, today.minusDays(2), AppointmentStatus.NO_SHOW, 50.0),
                visit(facial, today.plusDays(3), AppointmentStatus.CONFIRMED, 50.0));
        List<ProductSale> sales = List.of(ProductSale.builder().quantity(2).unitPrice(10.0).build());

        ClientVisitSummary summary = ClientVisitSummary.of(history, sales, today);
        assertEquals(3, summary.visits());
        assertEquals(128.0, summary.totalSpent(), 0.001);
        assertEquals("Hace 5 días", summary.lastVisitLabel());
        assertEquals("Manicura", summary.favoriteTreatment());
        assertEquals(2, summary.favoriteCount());

        ClientVisitSummary once = ClientVisitSummary.of(history.subList(1, 2), List.of(), today);
        assertNull(once.favoriteTreatment(), "con una sola visita no hay «lo que más repite»");
        assertEquals("Hace 2 semanas", once.lastVisitLabel());
    }

    private static Appointment visit(Treatment treatment, LocalDate day, AppointmentStatus status, double price) {
        return Appointment.builder().treatment(treatment).dateTime(day.atTime(10, 0)).status(status).price(price).build();
    }
}
