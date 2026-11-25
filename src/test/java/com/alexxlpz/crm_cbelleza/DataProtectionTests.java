package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.ClientCard;
import com.alexxlpz.crm_cbelleza.entities.DataProcessing;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.NewClientForm;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.ClientCardTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Datos de salud y contrato de encargo (RGPD): la ficha técnica solo se guarda con consentimiento de la clienta,
 * abrirla no crea nada, y el dueño del centro acepta el contrato de encargo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DataProtectionTests {

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
    private CenterRepository centers;
    @Autowired
    private ClientCardRepository cards;
    @Autowired
    private ClientCardService clientCards;
    @Autowired
    private ClientCardTemplateService cardTemplates;
    @Autowired
    private com.alexxlpz.crm_cbelleza.services.CenterService centerService;
    @Autowired
    private com.alexxlpz.crm_cbelleza.services.UserService userService;

    private User carlos;
    private Long centerId;
    private User nuria;
    private String fieldId;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        centerId = carlos.getCenter().getId();
        // Clienta con cuenta y una cita en el centro (como quien reserva por la web), todavía sin ficha
        nuria = userService.createUser("Nuria Consentida", "nuria.consentida@gmail.com", "+34 622 333 444",
                "Consentida2026!", Role.CLIENT, null);
        Treatment treatment = treatments.findByCenterId(centerId).getFirst();
        LocalDateTime when = LocalDateTime.now().plusDays(3).withHour(11).withMinute(0).withSecond(0).withNano(0);
        appointments.saveAndFlush(Appointment.builder().center(carlos.getCenter()).treatment(treatment).client(nuria)
                .worker(carlos).status(AppointmentStatus.CONFIRMED).price(treatment.getPrice()).dateTime(when)
                .createdAt(when.minusDays(1)).build());
        fieldId = cardTemplates.getFields(centerId).getFirst().getId();
    }

    @Test
    void openingTheCardOfAClientWithoutOneDoesNotCreateIt() throws Exception {
        mvc.perform(get("/worker/clients/" + nuria.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Última edición"))));

        assertTrue(cards.findByCenterIdAndClientId(centerId, nuria.getId()).isEmpty(), "Mirar la ficha no la guarda");
    }

    @Test
    void healthDataNeedsConsentAndRecordsWhoAndWhen() {
        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> clientCards.saveCardData(centerId, nuria.getId(), Map.of(fieldId, "Alergia al látex"), false, carlos.getId()));
        assertEquals(ClientCardService.CONSENT_REQUIRED, error.getMessage());

        clientCards.saveCardData(centerId, nuria.getId(), Map.of(fieldId, "Alergia al látex"), true, carlos.getId());
        ClientCard card = cards.findByCenterIdAndClientId(centerId, nuria.getId()).orElseThrow();
        assertTrue(card.hasHealthConsent());
        assertNotNull(card.getHealthConsentAt());
        assertEquals(carlos.getId(), card.getHealthConsentBy().getId());
        LocalDateTime firstConsent = card.getHealthConsentAt();

        // Guardar otra vez no cambia cuándo se dio
        clientCards.saveCardData(centerId, nuria.getId(), Map.of(fieldId, "Alergia al látex y al níquel"), true, carlos.getId());
        assertEquals(firstConsent, cards.findByCenterIdAndClientId(centerId, nuria.getId()).orElseThrow().getHealthConsentAt());

        // Retirarlo con la ficha vacía sí se puede, y se borra el consentimiento
        clientCards.saveCardData(centerId, nuria.getId(), Map.of(fieldId, " "), false, carlos.getId());
        ClientCard emptied = cards.findByCenterIdAndClientId(centerId, nuria.getId()).orElseThrow();
        assertFalse(emptied.hasHealthConsent());
        assertFalse(emptied.hasData());
    }

    @Test
    void savingTheCardFormWithoutConsentShowsTheReason() throws Exception {
        mvc.perform(post("/worker/clients/" + nuria.getId() + "/card").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param(fieldId, "Piel atópica"))
                .andExpect(redirectedUrl("/worker/clients/" + nuria.getId()))
                .andExpect(flash().attribute("errorMessage", ClientCardService.CONSENT_REQUIRED));
        assertFalse(cards.findByCenterIdAndClientId(centerId, nuria.getId()).map(ClientCard::hasData).orElse(false));

        mvc.perform(post("/worker/clients/" + nuria.getId() + "/card").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param(fieldId, "Piel atópica")
                        .param("healthConsent", "true"))
                .andExpect(redirectedUrl("/worker/clients/" + nuria.getId()));
        assertTrue(cards.findByCenterIdAndClientId(centerId, nuria.getId()).orElseThrow().hasHealthConsent());
    }

    @Test
    void aClientWithoutAccountGivesConsentAtTheCenterWithoutTheBox() throws Exception {
        // Dada de alta a mano con notas: no se pide casilla, el consentimiento queda a nombre de quien la atiende
        Long id = clientCards.createClientManually(centerId,
                new NewClientForm("Inés Sin Cuenta", "+34 633 444 556", null, "Alergia al tinte"), carlos.getId()).clientId();
        ClientCard card = cards.findByCenterIdAndClientId(centerId, id).orElseThrow();
        assertTrue(card.hasData());
        assertTrue(card.hasHealthConsent());
        assertEquals(carlos.getId(), card.getHealthConsentBy().getId());

        // Su ficha no enseña la casilla y se guarda sin marcarla
        mvc.perform(get("/worker/clients/" + id).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(not(containsString("healthConsentInput"))));
        clientCards.saveCardData(centerId, id, Map.of(fieldId, "Alergia al tinte y al látex"), false, carlos.getId());
        assertTrue(cards.findByCenterIdAndClientId(centerId, id).orElseThrow().hasHealthConsent());

        // Una clienta con cuenta sí la ve
        mvc.perform(get("/worker/clients/" + nuria.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("healthConsentInput")));
    }

    @Test
    void aClientHasOnlyOneCardPerCenter() {
        clientCards.saveCardData(centerId, nuria.getId(), Map.of(fieldId, "Algo"), true, carlos.getId());
        cards.flush();
        assertThrows(DataIntegrityViolationException.class, () -> cards.saveAndFlush(ClientCard.builder()
                .center(carlos.getCenter()).client(nuria).dataJson("{}").build()));
    }

    @Test
    void dataProcessingContractIsPublic() throws Exception {
        mvc.perform(get("/encargo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Contrato de encargo")))
                .andExpect(content().string(containsString(DataProcessing.VERSION_LABEL)));
    }

    @Test
    void ownerSeesAndAcceptsTheContractButAStandardWorkerCannot() throws Exception {
        Center before = centers.findById(centerId).orElseThrow();
        before.setDpaVersion(null);
        centers.saveAndFlush(before);
        User lucia = users.findByEmailIgnoreCase(LUCIA).orElseThrow();
        assertEquals(centerId, lucia.getCenter().getId());
        assertEquals(Role.WORKER, lucia.getRole());

        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, LUCIA)))
                .andExpect(content().string(not(containsString("Aceptar el contrato"))));
        mvc.perform(post("/worker/center/dpa").with(csrf()).with(TestUsers.as(users, LUCIA)))
                .andExpect(flash().attribute("errorMessage", containsString("Solo el dueño del centro")));
        assertNull(centers.findById(centerId).orElseThrow().getDpaVersion());

        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("Aceptar el contrato")));
        mvc.perform(post("/worker/center/dpa").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(redirectedUrl("/worker/dashboard"));

        Center after = centers.findById(centerId).orElseThrow();
        assertEquals(DataProcessing.VERSION, after.getDpaVersion());
        assertEquals(TestUsers.WORKER_CENTER_1, after.getDpaAcceptedBy());
        assertNotNull(after.getDpaAcceptedAt());
        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(not(containsString("Aceptar el contrato"))));
    }

    @Test
    void theClientGivesConsentWhenBookingOnlyOnce() throws Exception {
        User sofia = users.findById(TestUsers.details(users, TestUsers.CLIENT).getId()).orElseThrow();
        cards.findByCenterIdAndClientId(centerId, sofia.getId()).ifPresent(card -> {
            card.setHealthConsentAt(null);
            card.setHealthConsentBy(null);
            cards.saveAndFlush(card);
        });
        String page = "/client/centers/" + centerService.shortSlug(carlos.getCenter());
        String question = "apunte en mi ficha datos de salud";

        mvc.perform(get(page).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(containsString(question)));

        Treatment treatment = treatments.findByCenterId(centerId).stream().filter(t -> t.getDuration() <= 60).findFirst().orElseThrow();
        LocalDateTime tuesday = java.time.LocalDate.now().plusWeeks(30)
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.TUESDAY)).atTime(10, 0);
        mvc.perform(post("/client/booking").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT))
                        .param("centerId", centerId.toString())
                        .param("treatmentId", treatment.getId().toString())
                        .param("dateTime", tuesday.toString())
                        .param("healthConsent", "true"))
                .andExpect(redirectedUrl("/client/appointments"));

        ClientCard card = cards.findByCenterIdAndClientId(centerId, sofia.getId()).orElseThrow();
        assertTrue(card.hasHealthConsent());
        assertEquals(sofia.getId(), card.getHealthConsentBy().getId(), "Lo dio ella misma");

        // Ya no se le vuelve a preguntar en ese centro, y su perfil lo enseña (sin botón: para retirarlo, habla con el centro)
        mvc.perform(get(page).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(not(containsString(question))));
        mvc.perform(get("/client/profile").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(containsString("Ver centros que tienen tu ficha")))
                .andExpect(content().string(containsString(carlos.getCenter().getName())))
                .andExpect(content().string(not(containsString("consent/withdraw"))));
    }
}
