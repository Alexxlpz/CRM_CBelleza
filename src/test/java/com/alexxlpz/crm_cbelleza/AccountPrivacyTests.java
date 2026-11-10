package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AccountPrivacyService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.ClientCardTemplateService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Descargar mis datos» y «Borrar mi cuenta» desde el perfil del cliente (RGPD). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountPrivacyTests {

    private static final String PASSWORD = "Privada2026!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository users;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private ClientCardRepository cards;
    @Autowired
    private ClientCardService clientCards;
    @Autowired
    private ClientCardTemplateService cardTemplates;
    @Autowired
    private PersistentTokenRepository rememberMeTokens;

    private User carlos;
    private Treatment treatment;
    private User marta;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        treatment = treatments.findByCenterId(carlos.getCenter().getId()).getFirst();
        marta = userService.createUser("Marta Privada", "marta.privada@gmail.com", "+34 611 222 333", PASSWORD, Role.CLIENT, null);
    }

    private Appointment appointment(AppointmentStatus status, LocalDateTime dateTime, String summary) {
        return appointments.saveAndFlush(Appointment.builder()
                .center(carlos.getCenter())
                .treatment(treatment)
                .client(marta)
                .worker(carlos)
                .status(status)
                .price(treatment.getPrice())
                .dateTime(dateTime)
                .createdAt(dateTime.minusDays(7))
                .treatmentSummary(summary)
                .build());
    }

    private Appointment pastWithSummaryAndCard() {
        Appointment past = appointment(AppointmentStatus.COMPLETED,
                LocalDateTime.now().minusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0),
                "Enrojecimiento leve en la mejilla izquierda");
        String fieldId = cardTemplates.getFields(carlos.getCenter().getId()).getFirst().getId();
        clientCards.saveCardData(carlos.getCenter().getId(), marta.getId(), Map.of(fieldId, "Piel sensible"), carlos.getId());
        return past;
    }

    private Appointment upcoming() {
        return appointment(AppointmentStatus.CONFIRMED,
                LocalDateTime.now().plusDays(5).withHour(11).withMinute(0).withSecond(0).withNano(0), null);
    }

    @Test
    void downloadHasAccountAppointmentsAndWhatTheCenterWrote() throws Exception {
        pastWithSummaryAndCard();

        mvc.perform(get("/client/profile/data").with(user(AppUserDetails.from(marta))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("mis-datos-cuquora-")))
                .andExpect(jsonPath("$.cuenta.correo").value("marta.privada@gmail.com"))
                .andExpect(jsonPath("$.cuenta.telefono").value("+34 611 222 333"))
                .andExpect(jsonPath("$.citas[0].centro").value(carlos.getCenter().getName()))
                .andExpect(jsonPath("$.citas[0].estado").value("Completada"))
                .andExpect(jsonPath("$.citas[0].resumenDelTratamiento").value("Enrojecimiento leve en la mejilla izquierda"))
                .andExpect(jsonPath("$.fichasDeLosCentros[0].centro").value(carlos.getCenter().getName()))
                .andExpect(content().string(containsString("Piel sensible")));

        // Solo sus propios datos: un trabajador no tiene esta descarga
        mvc.perform(get("/client/profile/data").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void wrongPasswordDoesNotDeleteTheAccount() throws Exception {
        mvc.perform(post("/client/profile/delete").param("confirmation", "otra-cosa").with(csrf())
                        .with(user(AppUserDetails.from(marta))))
                .andExpect(redirectedUrl("/client/profile#borrar-cuenta"))
                .andExpect(flash().attribute("errorMessage", containsString("no es correcta")));

        User same = users.findById(marta.getId()).orElseThrow();
        assertFalse(same.isDeleted());
        assertEquals("marta.privada@gmail.com", same.getEmail());
    }

    @Test
    void deletingClearsPersonalDataCancelsUpcomingAndKeepsTheCenterNumbers() throws Exception {
        Appointment past = pastWithSummaryAndCard();
        Appointment next = upcoming();
        rememberMeTokens.createNewToken(new PersistentRememberMeToken("marta.privada@gmail.com", "serie-marta", "valor", new Date()));
        Long centerId = carlos.getCenter().getId();
        assertTrue(clientCards.getClientsForCenter(centerId, null).stream().anyMatch(c -> c.getId().equals(marta.getId())));

        mvc.perform(post("/client/profile/delete").param("confirmation", PASSWORD).with(csrf())
                        .with(user(AppUserDetails.from(marta))))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("successMessage", containsString("Tu cuenta se ha borrado")));

        User deleted = users.findById(marta.getId()).orElseThrow();
        assertTrue(deleted.isDeleted());
        assertEquals(AccountPrivacyService.DELETED_NAME, deleted.getName());
        assertNull(deleted.getEmail());
        assertNull(deleted.getPhone());
        assertNull(deleted.getPassword());
        assertFalse(deleted.hasAccount(), "ya no se puede entrar");
        assertTrue(users.findByEmailIgnoreCase("marta.privada@gmail.com").isEmpty(), "el correo queda libre");
        assertNull(rememberMeTokens.getTokenForSeries("serie-marta"));

        Appointment cancelled = appointments.findById(next.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus(), "la cita por delante se anula y el hueco queda libre");
        Appointment history = appointments.findById(past.getId()).orElseThrow();
        assertEquals(AppointmentStatus.COMPLETED, history.getStatus(), "la cita pasada sigue contando para el centro");
        assertEquals(treatment.getPrice(), history.getPrice());
        assertNull(history.getTreatmentSummary(), "el resumen era un dato suyo");
        assertTrue(cards.findByClientId(marta.getId()).isEmpty(), "su ficha se borra");
        assertTrue(clientCards.getClientsForCenter(centerId, null).stream().noneMatch(c -> c.getId().equals(marta.getId())),
                "sale de la cartera del centro");
    }

    @Test
    void googleAccountConfirmsByTypingTheWord() throws Exception {
        User google = users.save(User.builder().name("Nuria Google").email("nuria.google@gmail.com")
                .googleId("google-nuria").role(Role.CLIENT).build());

        mvc.perform(get("/client/profile").with(user(AppUserDetails.from(google))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("para confirmar")))
                .andExpect(content().string(containsString("BORRAR")));
        mvc.perform(post("/client/profile/delete").param("confirmation", "no").with(csrf())
                        .with(user(AppUserDetails.from(google))))
                .andExpect(redirectedUrl("/client/profile#borrar-cuenta"));
        assertFalse(users.findById(google.getId()).orElseThrow().isDeleted());

        mvc.perform(post("/client/profile/delete").param("confirmation", " borrar ").with(csrf())
                        .with(user(AppUserDetails.from(google))))
                .andExpect(redirectedUrl("/login"));
        User deleted = users.findById(google.getId()).orElseThrow();
        assertTrue(deleted.isDeleted());
        assertNull(deleted.getGoogleId(), "entrar con Google crearía una cuenta nueva y vacía");
    }

    @Test
    void sessionsOpenOnOtherDevicesAreClosed() throws Exception {
        AppUserDetails otherDevice = AppUserDetails.from(marta);
        mvc.perform(post("/client/profile/delete").param("confirmation", PASSWORD).with(csrf())
                        .with(user(AppUserDetails.from(marta))))
                .andExpect(redirectedUrl("/login"));

        mvc.perform(get("/client/appointments").with(user(otherDevice)))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("errorMessage", "Esta cuenta se ha borrado."));
    }
}
