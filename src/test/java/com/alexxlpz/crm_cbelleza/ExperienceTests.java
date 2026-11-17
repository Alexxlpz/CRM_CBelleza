package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.dto.ClientCardFieldDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.forms.PhoneNumbers;
import com.alexxlpz.crm_cbelleza.forms.ProductSaleForm;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.ClientAllergyService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.ClientCardTemplateService;
import com.alexxlpz.crm_cbelleza.services.DashboardService;
import com.alexxlpz.crm_cbelleza.services.DashboardService.PendingRequest;
import com.alexxlpz.crm_cbelleza.services.FriendlyDates;
import com.alexxlpz.crm_cbelleza.services.ProductSaleService;
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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mejoras de experiencia del 15 nov 2026: la cita recién pedida arriba, teléfonos que llaman, fechas amables, próxima
 * cita en el buscador, «Por aprobar» en el panel, historial plegado, importe de compras solo para el dueño y alergias
 * de la ficha en cada cita.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExperienceTests {

    private static final String LUCIA = "lucia.romero@cbelleza.com";
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
    private DashboardService dashboardService;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private ClientAllergyService allergyService;
    @Autowired
    private ClientCardService clientCards;
    @Autowired
    private ClientCardTemplateService cardTemplates;
    @Autowired
    private InventoryRepository inventories;
    @Autowired
    private ProductSaleService saleService;

    private User carlos() {
        return users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
    }

    private User newClient(String phone) {
        String id = UUID.randomUUID().toString();
        return users.save(User.builder().name("Clienta " + id).phone(phone)
                .email(id + "@ejemplo.com").password("x").role(Role.CLIENT).build());
    }

    private Appointment save(User client, User worker, LocalDateTime at, AppointmentStatus status) {
        Center center = carlos().getCenter();
        Treatment treatment = treatments.findByCenterId(center.getId()).getFirst();
        return appointments.saveAndFlush(Appointment.builder()
                .dateTime(at).treatment(treatment).center(center).client(client).worker(worker)
                .status(status).price(treatment.getPrice()).workerMessage("").createdAt(LocalDateTime.now())
                .build());
    }

    private static LocalDateTime inDays(int days, int hour) {
        return LocalDateTime.of(LocalDate.now(MADRID).plusDays(days), LocalTime.of(hour, 0));
    }

    /* ---------- Fechas amables y teléfonos ---------- */

    @Test
    void friendlyDatesSayTodayTomorrowOrTheShortDate() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        assertEquals("Hoy · 11:00", FriendlyDates.dateTime(LocalDateTime.of(2026, 11, 15, 11, 0), today));
        assertEquals("Mañana, lun 16 nov · 09:00", FriendlyDates.dateTime(LocalDateTime.of(2026, 11, 16, 9, 0), today));
        assertEquals("vie 20 nov · 10:30", FriendlyDates.dateTime(LocalDateTime.of(2026, 11, 20, 10, 30), today));
        assertEquals("sáb 2 ene 2027 · 12:00", FriendlyDates.dateTime(LocalDateTime.of(2027, 1, 2, 12, 0), today));
        assertEquals("Hoy", FriendlyDates.day(today, today));
        assertEquals("Mañana", FriendlyDates.day(today.plusDays(1), today));
        assertEquals("mar 17 nov", FriendlyDates.day(today.plusDays(2), today));
    }

    @Test
    void phoneLinksKeepOnlyDigitsAndTheLeadingPlus() {
        assertEquals("tel:+34910111222", PhoneNumbers.telHref("+34 910 111 222"));
        assertEquals("tel:600111222", PhoneNumbers.telHref("600 11 12 22"));
        assertNull(PhoneNumbers.telHref(""));
        assertNull(PhoneNumbers.telHref("sin teléfono"));
        assertNull(PhoneNumbers.telHref(null));
    }

    @Test
    void centerPhoneCanBeTappedInMyAppointments() throws Exception {
        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"tel-link\" href=\"tel:")));
    }

    /* ---------- 1. Recién pedida arriba ---------- */

    @Test
    void justBookedAppointmentGoesFirstAndHighlighted() throws Exception {
        User client = newClient(null);
        Appointment soon = save(client, carlos(), inDays(2, 10), AppointmentStatus.CONFIRMED);
        Appointment booked = save(client, null, inDays(9, 12), AppointmentStatus.PENDING);

        String html = mvc.perform(get("/client/appointments").with(TestUsers.as(users, client.getEmail()))
                        .flashAttr("justBookedId", booked.getId())
                        .flashAttr("successTitle", "Solicitud enviada")
                        .flashAttr("successMessage", "Te avisaremos."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("appointment-card--just-booked")))
                .andExpect(content().string(containsString("Acabas de pedirla")))
                .andExpect(content().string(containsString("alert-title")))
                .andExpect(content().string(containsString("Profesional: te la asigna el centro")))
                .andReturn().getResponse().getContentAsString();
        // La recién pedida va antes que la más cercana, que sigue siendo «Tu próxima cita»
        int justBooked = html.indexOf("Acabas de pedirla");
        int next = html.indexOf("Tu próxima cita");
        assertTrue(justBooked > 0 && next > justBooked);
        assertTrue(soon.getId() != null);

        // Sin el aviso (al recargar) todo se ve normal
        mvc.perform(get("/client/appointments").with(TestUsers.as(users, client.getEmail())))
                .andExpect(content().string(not(containsString("appointment-card--just-booked"))));
    }

    /* ---------- 4. Próxima cita en el buscador ---------- */

    @Test
    void searchShowsTheClientsNextAppointment() throws Exception {
        User client = newClient(null);
        save(client, carlos(), inDays(1, 10), AppointmentStatus.CONFIRMED);
        save(client, carlos(), inDays(3, 10), AppointmentStatus.CONFIRMED);

        mvc.perform(get("/centers").with(TestUsers.as(users, client.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tu próxima cita")))
                .andExpect(content().string(containsString("Mañana")))
                .andExpect(content().string(containsString("Mis citas (2)")));
        // Sin sesión o sin citas, no sale
        mvc.perform(get("/centers"))
                .andExpect(content().string(not(containsString("Tu próxima cita"))));
        User without = newClient(null);
        mvc.perform(get("/centers").with(TestUsers.as(users, without.getEmail())))
                .andExpect(content().string(not(containsString("Tu próxima cita"))));
    }

    /* ---------- 6. Por aprobar ---------- */

    @Test
    void pendingRequestsAreListedInTheDashboard() throws Exception {
        User client = newClient("+34 655 909 808");
        Appointment request = save(client, null, inDays(4, 11), AppointmentStatus.PENDING);
        Appointment past = save(client, null, inDays(-1, 11), AppointmentStatus.PENDING);

        List<PendingRequest> pending = dashboardService.pendingFor(carlos().getCenter());
        assertTrue(pending.stream().anyMatch(r -> r.id().equals(request.getId()) && "tel:+34655909808".equals(
                PhoneNumbers.telHref(r.clientPhone()))));
        assertTrue(pending.stream().noneMatch(r -> r.id().equals(past.getId())), "Las pasadas ya no se pueden aceptar");
        for (int i = 1; i < pending.size(); i++) {
            assertTrue(!pending.get(i).start().isBefore(pending.get(i - 1).start()));
        }

        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, LUCIA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"porAprobar\"")))
                .andExpect(content().string(containsString("data-request=\"" + request.getId() + "\"")))
                .andExpect(content().string(containsString("/js/worker-requests")));
    }

    /* ---------- 7 y 8. Ficha: historial plegado e importe de compras ---------- */

    @Test
    void clientCardFoldsTheHistoryAndHidesSalesTotalFromStandardWorkers() throws Exception {
        User carlos = carlos();
        Long centerId = carlos.getCenter().getId();
        User client = newClient(null);
        for (int i = 1; i <= 8; i++) {
            save(client, carlos, inDays(-i * 7, 10), AppointmentStatus.COMPLETED);
        }
        Long inventoryId = inventories.findByCenterId(centerId).stream()
                .filter(item -> item.getStock() > 2).findFirst().orElseThrow().getId();
        saleService.sell(centerId, client.getId(), new ProductSaleForm(inventoryId, 1), carlos.getId());

        mvc.perform(get("/worker/clients/" + client.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("1 compra · ")))
                .andExpect(content().string(containsString("history-item--extra")))
                .andExpect(content().string(containsString("Ver las 8 citas")))
                // Desplegado, el mismo botón vuelve a plegarlo
                .andExpect(content().string(containsString("data-less-label=\"Ver menos\"")))
                .andExpect(content().string(containsString("aria-controls=\"historyList\" aria-expanded=\"false\"")));
        mvc.perform(get("/worker/clients/" + client.getId()).with(TestUsers.as(users, LUCIA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 compra")))
                .andExpect(content().string(not(containsString("1 compra · "))));
    }

    /* ---------- 9. Alergias de la ficha en la agenda y en «Hoy» ---------- */

    @Test
    void allergiesFromTheCardReachTheAgenda() throws Exception {
        User carlos = carlos();
        Long centerId = carlos.getCenter().getId();
        User client = newClient(null);
        save(client, carlos, inDays(2, 12), AppointmentStatus.CONFIRMED);
        ClientCardFieldDTO allergyField = cardTemplates.getFields(centerId).stream()
                .filter(f -> f.getLabel() != null && f.getLabel().toLowerCase().contains("alergia"))
                .findFirst().orElseThrow();
        clientCards.saveCardData(centerId, client.getId(), Map.of(allergyField.getId(), "Látex"), carlos.getId());

        assertEquals("Látex", allergyService.of(centerId, client.getId()));
        assertEquals("Látex", allergyService.byClient(centerId).get(client.getId()));
        assertNull(allergyService.of(centerId, newClient(null).getId()));
        assertTrue(appointmentService.getCalendarAppointments(centerId).stream()
                .filter(a -> a.getClient() != null && client.getId().equals(a.getClient().getId()))
                .allMatch(a -> "Látex".equals(a.getClientAllergies())));

        mvc.perform(get("/worker/calendar/data").with(TestUsers.as(users, LUCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..clientAllergies", org.hamcrest.Matchers.hasItem("Látex")));
    }
}
