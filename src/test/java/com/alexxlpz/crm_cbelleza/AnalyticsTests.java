package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductSaleRepository;
import com.alexxlpz.crm_cbelleza.repositories.ReviewRepository;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.AnalyticsReport;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.ClientRow;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.Kpi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pantalla «Análisis»: cifras del mes, clientes fieles y clientes a recuperar. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalyticsTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AnalyticsService analytics;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private InventoryRepository inventories;
    @Autowired
    private ProductSaleRepository sales;
    @Autowired
    private ReviewRepository reviews;
    @Autowired
    private Clock clock;

    private User carlos;
    private Center center;
    private Treatment treatment;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        center = centers.findById(carlos.getCenter().getId()).orElseThrow();
        treatment = treatments.findByCenterId(center.getId()).getFirst();
        now = CenterClock.now(clock, center);
    }

    @Test
    void pageRendersWithMonthFiguresAndPeriodSelector() throws Exception {
        mvc.perform(get("/worker/analytics").param("meses", "6").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Análisis del centro")))
                .andExpect(content().string(containsString("Clientes más fieles")))
                .andExpect(content().string(containsString("Últimos 6 meses")));
    }

    @Test
    void dashboardLinksToAnalytics() throws Exception {
        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/worker/analytics\"")));
    }

    @Test
    void unknownPeriodFallsBackToTwelveMonths() {
        assertEquals(12, AnalyticsService.normalizeMonths(7));
        assertEquals(12, AnalyticsService.normalizeMonths(null));
        assertEquals(3, AnalyticsService.normalizeMonths(3));
    }

    @Test
    void productSalesCountAsRevenueOfTheMonth() {
        double before = revenueKpi(analytics.reportFor(center.getId(), 12)).value();
        Inventory item = inventories.findByCenterId(center.getId()).getFirst();
        sales.save(ProductSale.builder().center(center).client(newClient("Compra Rápida", "611800001"))
                .product(item.getProduct()).worker(carlos).quantity(2).unitPrice(10.0)
                .soldAt(now.minusSeconds(1)).build());

        AnalyticsReport report = analytics.reportFor(center.getId(), 12);

        assertEquals(before + 20, revenueKpi(report).value(), 0.001);
    }

    @Test
    void newClientCountsOnceAndAppearsAmongTopClients() {
        User client = newClient("Inés Fiel", "611800002");
        // Seis visitas en el periodo: más que cualquier cliente demo
        for (int i = 1; i <= 6; i++) {
            visit(client, now.minusDays(i * 20L), AppointmentStatus.COMPLETED);
        }

        AnalyticsReport report = analytics.reportFor(center.getId(), 12);

        ClientRow top = report.topClients().getFirst();
        assertEquals("Inés Fiel", top.name());
        assertEquals(6, top.visits());
        assertEquals(6 * treatment.getPrice(), top.spent(), 0.001);
    }

    @Test
    void regularClientWhoStoppedComingIsListedToWinBack() {
        User away = newClient("Rosa Ausente", "611800003");
        visit(away, now.minusDays(140), AppointmentStatus.COMPLETED);
        visit(away, now.minusDays(120), AppointmentStatus.COMPLETED);
        // Mismo historial, pero ya tiene otra cita: no hay que llamarla
        User booked = newClient("Nora Reservada", "611800004");
        visit(booked, now.minusDays(140), AppointmentStatus.COMPLETED);
        visit(booked, now.minusDays(120), AppointmentStatus.COMPLETED);
        visit(booked, now.plusDays(5), AppointmentStatus.CONFIRMED);
        // Una sola visita: aún no es cliente habitual
        User once = newClient("Una Vez", "611800005");
        visit(once, now.minusDays(150), AppointmentStatus.COMPLETED);

        AnalyticsReport report = analytics.reportFor(center.getId(), 12);

        ClientRow row = report.clientsToWinBack().stream().filter(c -> c.name().equals("Rosa Ausente")).findFirst().orElseThrow();
        assertEquals("Venía cada 3 semanas", row.usualGapLabel());
        assertEquals("Hace 4 meses", row.awayLabel());
        assertTrue(report.clientsToWinBack().stream().noneMatch(c -> c.name().equals("Nora Reservada")));
        assertTrue(report.clientsToWinBack().stream().noneMatch(c -> c.name().equals("Una Vez")));
    }

    @Test
    void moreCancellationsCountAsWorseAndMoreRevenueAsBetter() {
        assertEquals("Anuladas, rechazadas o no vino", analytics.reportFor(center.getId(), 3).kpis().get(7).label());

        Kpi worse = new Kpi("x", "i", 20, 10, AnalyticsService.Format.PERCENT, 10, false);
        assertTrue(worse.isWorsening());
        assertEquals("+10 pts", worse.changeLabel());
        Kpi better = new Kpi("x", "i", 1200, 1000, AnalyticsService.Format.MONEY, 20, true);
        assertTrue(better.isImprovement());
        assertEquals("1.200 €", better.displayValue());
    }

    @Test
    void revenueUsesWhatWasChargedNotTheCurrentListPrice() {
        User client = newClient("Precio Rebajado", "611800006");
        Appointment visit = visit(client, now.minusDays(40), AppointmentStatus.COMPLETED);
        visit.setPrice(12.0);
        appointments.saveAndFlush(visit);
        treatment.setPrice(treatment.getPrice() + 50);
        treatments.saveAndFlush(treatment);

        ClientRow row = analytics.reportFor(center.getId(), 3).topClients().stream()
                .filter(c -> c.name().equals("Precio Rebajado")).findFirst().orElseThrow();

        assertEquals(12.0, row.spent(), 0.001);
    }

    @Test
    void yearViewRendersAndCanOnlyGoBackWhileThereIsData() throws Exception {
        mvc.perform(get("/worker/analytics").param("anio", "").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(now.getYear() + " hasta hoy")))
                .andExpect(content().string(containsString("Primer año con Cuquora")))
                .andExpect(content().string(containsString("aria-disabled=\"true\"")));

        // Pedir un año sin datos lleva al primero que tiene
        AnalyticsReport report = analytics.yearReportFor(center.getId(), 1990);
        assertEquals(now.getYear(), report.year());
        assertNull(report.previousYear());
        assertNull(report.changes());
        assertNull(report.kpis().getFirst().change());
    }

    @Test
    void yearViewComparesWithTheSameSpanOfLastYearAndMarksTheFirstMonth() {
        LocalDateTime lastYear = now.minusYears(1).minusHours(1);
        User gone = newClient("Ana Antes", "611800007");
        visitAt(gone, lastYear);
        visitAt(gone, lastYear.minusDays(1));

        AnalyticsReport report = analytics.yearReportFor(center.getId(), null);

        assertEquals(now.getYear() - 1, report.previousYear());
        assertNotNull(report.kpis().getFirst().change());
        assertTrue(report.changes().lostTopClients().stream().anyMatch(c -> c.name().equals("Ana Antes")));
        // El mes de esas visitas es el primero con Cuquora: los anteriores no tienen datos
        AnalyticsReport previous = analytics.yearReportFor(center.getId(), now.getYear() - 1);
        int firstMonth = lastYear.minusDays(1).getMonthValue();
        assertTrue(previous.monthly().get(firstMonth - 1).cuquoraStart());
        if (firstMonth > 1) {
            assertFalse(previous.monthly().getFirst().hasData());
        }
        assertNull(previous.previousYear());
    }

    @Test
    void noShowsCountAsLostAndShowUpInTheTeamTable() {
        AnalyticsReport before = analytics.reportFor(center.getId(), 3);
        User absent = newClient("Pepa Faltona", "611800020");
        // Ayer a las 11:00: con «hoy» fallaba antes de las 11 (la cita aún no había pasado)
        visit(absent, now.minusDays(1), AppointmentStatus.NO_SHOW);
        appointments.flush();

        AnalyticsReport after = analytics.reportFor(center.getId(), 3);
        assertTrue(after.kpis().get(7).value() > before.kpis().get(7).value(), "El no presentado sube «Anuladas, rechazadas o no vino»");
        AnalyticsService.TeamRow carlosRow = after.team().stream().filter(r -> r.name().equals("Carlos Mendoza")).findFirst().orElseThrow();
        AnalyticsService.TeamRow carlosBefore = before.team().stream().filter(r -> r.name().equals("Carlos Mendoza")).findFirst().orElseThrow();
        assertEquals(carlosBefore.noShows() + 1, carlosRow.noShows());
        assertEquals("Ocupación de la agenda", after.kpis().get(4).label());
        assertEquals("Clientes atendidos", after.kpis().get(5).label());
        assertEquals(8, after.kpis().size());
    }

    @Test
    void theTeamTableShowsTicketAndReviews() throws Exception {
        User regular = newClient("Marisa Contenta", "611800021");
        Appointment done = visit(regular, now.minusDays(1), AppointmentStatus.COMPLETED);
        done.setPrice(40.0);
        reviews.save(Review.builder().appointment(done).center(center).client(regular).rating(4)
                .createdAt(now).build());
        appointments.flush();

        AnalyticsService.TeamRow row = analytics.reportFor(center.getId(), 12).team().stream()
                .filter(r -> r.name().equals("Carlos Mendoza")).findFirst().orElseThrow();
        assertNotNull(row.rating());
        assertTrue(row.reviewCount() >= 1);
        assertEquals(row.visits() == 0 ? 0 : row.revenue() / row.visits(), row.averageTicket(), 0.001);

        mvc.perform(get("/worker/analytics").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("Rendimiento por profesional")))
                .andExpect(content().string(containsString("Ocupación de la agenda")))
                .andExpect(content().string(containsString("/worker/analytics/export?meses=12")));
    }

    @Test
    void ownerDownloadsTheAnalyticsAsCsv() throws Exception {
        String csv = mvc.perform(get("/worker/analytics/export").param("meses", "3").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("analisis-centro-historico-ultimos-3-meses.csv")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFAnálisis de Centro Histórico;Últimos 3 meses"));
        assertTrue(csv.contains("Mes;Ingresos (€);Tratamientos (€);Productos (€);Visitas"));
        assertTrue(csv.contains("Profesional;Visitas;Clientes;Ingresos (€);Ticket medio (€);Ocupación (%);No vinieron;Nota media;Reseñas"));
        assertTrue(csv.contains("\r\nCarlos Mendoza;"));

        mvc.perform(get("/worker/analytics/export").param("anio", "").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")));
        mvc.perform(get("/worker/analytics/export").with(TestUsers.as(users, "lucia.romero@cbelleza.com")))
                .andExpect(redirectedUrl("/worker/dashboard"));
    }

    @Test
    void verdictSaysWhatWeighsMore() {
        assertEquals("Los ingresos bajan un 12 %, sobre todo porque viene menos gente.", AnalyticsService.verdict(-12, -15, 4));
        assertEquals("Los ingresos suben un 5 %, sobre todo porque cada visita deja más dinero.", AnalyticsService.verdict(5, 1, 4));
        assertNull(AnalyticsService.verdict(null, null, null));
    }

    private Kpi revenueKpi(AnalyticsReport report) {
        return report.kpis().getFirst();
    }

    private User newClient(String name, String phone) {
        return users.save(User.builder().name(name).phone(phone)
                .email("cliente_" + phone + User.PLACEHOLDER_EMAIL_DOMAIN).role(Role.CLIENT).build());
    }

    private void visitAt(User client, LocalDateTime at) {
        appointments.save(Appointment.builder().center(center).client(client).worker(carlos).treatment(treatment)
                .dateTime(at).status(AppointmentStatus.COMPLETED).build());
    }

    private Appointment visit(User client, LocalDateTime at, AppointmentStatus status) {
        return appointments.save(Appointment.builder().center(center).client(client).worker(carlos).treatment(treatment)
                .dateTime(at.withHour(11).withMinute(0).withSecond(0).withNano(0)).status(status).build());
    }
}
