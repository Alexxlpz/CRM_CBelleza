package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.CenterForm;
import com.alexxlpz.crm_cbelleza.forms.InventoryItemForm;
import com.alexxlpz.crm_cbelleza.forms.TreatmentForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.PublicFormLimiter;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.InventoryService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import com.alexxlpz.crm_cbelleza.mail.EmailGateway;
import com.alexxlpz.crm_cbelleza.mail.EmailMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Correcciones de la auditoría de seguridad del 30 de octubre de 2026 (S-01 a S-16 del reporte ejecutivo). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityFixesTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private CenterService centerService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private TreatmentService treatmentService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private UserService userService;
    @MockitoBean
    private EmailGateway emailGateway;

    private Center centerOf(String workerEmail) {
        return users.findByEmailIgnoreCase(workerEmail).orElseThrow().getCenter();
    }

    /** S-13: las páginas llevan CSP y las demás cabeceras; Leaflet y las fuentes salen de la propia app. */
    @Test
    void pagesSendSecurityHeadersAndLoadNothingFromUnpkgOrGoogleFonts() throws Exception {
        mvc.perform(get("/centers"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(content().string(containsString("/vendor/leaflet/leaflet.js")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("unpkg.com"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("fonts.googleapis.com"))));
        mvc.perform(get("/vendor/leaflet/leaflet.js")).andExpect(status().isOk());
        mvc.perform(get("/css/fonts.css")).andExpect(status().isOk());
    }

    /** S-03: el enlace corto se guarda; renombrar el centro no lo cambia ni deja el antiguo libre para otro. */
    @Test
    void shortLinkStaysTheSameWhenTheCenterIsRenamed() {
        Center center = centerOf(TestUsers.WORKER_CENTER_1);
        String before = centerService.shortSlug(center);

        centerService.updateCenter(center.getId(), new CenterForm("Un nombre nuevo", null, null, null, null, null, null, null));

        Center renamed = centers.findById(center.getId()).orElseThrow();
        assertEquals(before, centerService.shortSlug(renamed));
        assertEquals(center.getId(), centerService.findPublishedBySlug(before).orElseThrow().getId());
        assertEquals(before, centerService.shortSlugs().get(center.getId()));
    }

    /** S-11: un producto nuevo de un centro no aparece en el catálogo de los demás ni se puede añadir desde otro. */
    @Test
    void newProductsStayInsideTheirCenter() {
        Long first = centerOf(TestUsers.WORKER_CENTER_1).getId();
        Long second = centerOf(TestUsers.WORKER_CENTER_2).getId();
        inventoryService.addToInventory(first, new InventoryItemForm("new", null, "Sérum secreto", "Fórmula propia",
                "Facial", 42.0, 3));
        Product created = inventoryService.getInventoryByCenter(first).stream()
                .map(item -> item.getProduct())
                .filter(p -> "Sérum secreto".equals(p.getName()))
                .findFirst().orElseThrow();

        assertTrue(inventoryService.getAvailableCatalogProducts(second).stream().noneMatch(p -> p.getId().equals(created.getId())));
        assertThrows(RuntimeException.class, () -> inventoryService.addToInventory(second,
                new InventoryItemForm("existing", created.getId(), null, null, null, null, 1)));
    }

    /** S-16: precios negativos. */
    @Test
    void negativePricesAreRejected() {
        Long centerId = centerOf(TestUsers.WORKER_CENTER_1).getId();
        assertThrows(BusinessRuleException.class, () -> treatmentService.addTreatment(centerId,
                new TreatmentForm("Gratis y algo", null, -10.0, 30, null, List.of())));
        assertThrows(BusinessRuleException.class, () -> inventoryService.addToInventory(centerId,
                new InventoryItemForm("new", null, "Regalo", null, "Facial", -5.0, 1)));
    }

    /** S-16: una página enorme en el buscador ya no da error 500 (la cuenta se desbordaba). */
    @Test
    void hugePageNumberInSearchReturnsAnEmptyList() throws Exception {
        mvc.perform(get("/api/centers").param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    /** S-08: aunque el centro acepte solo las citas, un cliente no puede llenarle la agenda. */
    @Test
    void aClientCannotFillTheAgendaOfACenterThatAutoAccepts() {
        Center center = centerOf(TestUsers.WORKER_CENTER_1);
        center.setAutoAcceptBookings(true);
        centers.saveAndFlush(center);
        User client = userService.createUser("Reservas Infinitas", "muchas.citas@gmail.com", null, null, Role.CLIENT, null);
        Long treatmentId = treatments.findByCenterId(center.getId()).getFirst().getId();

        for (int i = 0; i < BookingService.MAX_UPCOMING_BOOKINGS; i++) {
            bookingService.requestBooking(client.getId(), center.getId(), treatmentId, farTuesday(40 + i, 10));
        }
        var e = assertThrows(BusinessRuleException.class, () ->
                bookingService.requestBooking(client.getId(), center.getId(), treatmentId, farTuesday(60, 10)));
        assertTrue(e.getMessage().contains("citas por venir"));
    }

    /** S-01: los formularios públicos se cortan tras unos pocos envíos por hora y por IP. */
    @Test
    void publicFormsAreLimitedPerIp() {
        PublicFormLimiter limiter = new PublicFormLimiter(Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneId.of("UTC")), 2, 2, 2);
        limiter.check(PublicFormLimiter.Form.CONTACT, "10.1.0.1");
        limiter.check(PublicFormLimiter.Form.CONTACT, "10.1.0.1");
        assertThrows(BusinessRuleException.class, () -> limiter.check(PublicFormLimiter.Form.CONTACT, "10.1.0.1"));
        // Otra IP u otro formulario llevan su propia cuenta
        limiter.check(PublicFormLimiter.Form.CONTACT, "10.1.0.2");
        limiter.check(PublicFormLimiter.Form.REGISTRATION, "10.1.0.1");
    }

    /** S-10: si dos cuentas se llaman igual, el nombre deja de servir para entrar (hay que usar el correo). */
    @Test
    void ambiguousNamesCannotBeUsedToLogIn() throws Exception {
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        userService.createUser(carlos.getName(), "otro.carlos@gmail.com", null, "Cuquora2026!", Role.CLIENT, null);

        mvc.perform(post("/login").with(csrf())
                        .param("identifier", carlos.getName())
                        .param("password", "Cuquora2026!"))
                .andExpect(redirectedUrl("/login?error"));
    }

    /**
     * S-01: el alta de un centro es pública, así que no manda correos a las direcciones del equipo que escribe quien
     * la rellena: el equipo recibe su invitación cuando administración publica el centro.
     */
    @Test
    void teamInvitationsAreSentWhenTheCenterIsPublishedNotWhenItIsRegistered() throws Exception {
        mvc.perform(post("/register-center").with(csrf())
                        .param("centerName", "Estudio Alba")
                        .param("cif", "B12345678")
                        .param("phone", "911 222 333")
                        .param("cabins", "1")
                        .param("hoursDay", "2")
                        .param("hoursFrom", "09:30")
                        .param("hoursTo", "14:00")
                        .param("address", "Calle Mayor 12, 28013 Madrid")
                        .param("latitude", "40.4155")
                        .param("longitude", "-3.7074")
                        .param("timeZone", "Europe/Madrid")
                        .param("contactName", "Alba Ruiz")
                        .param("email", "alba@estudioalba.es")
                        .param("workerNames", "Nerea Gil")
                        .param("workerEmails", "nerea@estudioalba.es")
                        .param("mode", "OFF")
                        .param("terms", "true"))
                .andExpect(redirectedUrl("/register-center"));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, timeout(5000).atLeast(2)).send(sent.capture());
        assertTrue(sent.getAllValues().stream().noneMatch(m -> "nerea@estudioalba.es".equals(m.to())),
                "nada a la dirección del equipo mientras el centro está sin publicar");
        String approvalUrl = sent.getAllValues().stream()
                .map(m -> Pattern.compile("/register-center/approve/([A-Za-z0-9_-]+)").matcher(m.htmlBody()))
                .filter(Matcher::find).map(m -> m.group()).findFirst().orElseThrow();

        mvc.perform(post(approvalUrl).with(csrf())).andExpect(status().isOk());

        verify(emailGateway, timeout(5000).atLeastOnce()).send(argThat(m -> "nerea@estudioalba.es".equals(m.to())
                && m.subject().startsWith("Te han dado de alta en Estudio Alba")));
    }

    /** S-01: el campo trampa que rellenan los robots hace que el alta no cree nada. */
    @Test
    void honeypotFieldStopsBotRegistrations() throws Exception {
        long before = centers.count();
        mvc.perform(post("/register-center").with(csrf())
                        .param("centerName", "Spam SL")
                        .param("email", "spam@ejemplo.com")
                        .param("website", "http://spam.example")
                        .param("terms", "true"))
                .andExpect(redirectedUrl("/register-center"));
        assertEquals(before, centers.count());
    }

    /** Un martes lejano a la hora indicada, para no chocar con las citas de ejemplo ni con otros tests. */
    private static LocalDateTime farTuesday(int weeksAhead, int hour) {
        LocalDate tuesday = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        return LocalDateTime.of(tuesday, LocalTime.of(hour, 0));
    }
}
