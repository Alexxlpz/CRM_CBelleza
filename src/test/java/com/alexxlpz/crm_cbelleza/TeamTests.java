package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.mail.EmailGateway;
import com.alexxlpz.crm_cbelleza.mail.EmailMessage;
import com.alexxlpz.crm_cbelleza.repositories.AccountInvitationRepository;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * El equipo en «Mi Centro»: el dueño (Carlos) invita, reenvía invitaciones y saca del equipo; Lucía es
 * trabajadora estándar de Centro Histórico. Cada test se deshace al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamTests {

    private static final String LUCIA = "lucia.romero@cbelleza.com";
    private static final Pattern INVITATION_LINK = Pattern.compile("/invitation/([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private AgendaBlockRepository blocks;
    @Autowired
    private AccountInvitationRepository invitations;
    @MockitoBean
    private EmailGateway emailGateway;

    private User carlos;
    private User lucia;
    private Center center;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        lucia = users.findByEmailIgnoreCase(LUCIA).orElseThrow();
        center = centers.findById(carlos.getCenter().getId()).orElseThrow();
    }

    @Test
    void ownerInvitesSomeoneWhoGetsALinkToJoinTheCenter() throws Exception {
        mvc.perform(post("/worker/center/team").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("name", "Marta López")
                        .param("email", "  Marta.Lopez@Correo.com "))
                .andExpect(redirectedUrl("/worker/center#equipo"))
                .andExpect(flash().attribute("teamMessage", containsString("marta.lopez@correo.com")));

        User marta = users.findByEmailIgnoreCase("marta.lopez@correo.com").orElseThrow();
        assertEquals(Role.WORKER, marta.getRole());
        assertEquals(center.getId(), marta.getCenter().getId());
        assertFalse(marta.hasAccount());
        assertFalse(center.isOwnedBy(marta.getId()));

        String token = invitationToken("marta.lopez@correo.com");
        mvc.perform(get("/invitation/" + token)).andExpect(content().string(containsString(center.getName())));
        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("Marta López")))
                .andExpect(content().string(containsString("Invitación enviada")));
    }

    @Test
    void invitationsForTakenEmailsAreRejected() throws Exception {
        long before = users.count();
        mvc.perform(post("/worker/center/team").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("name", "Lucía").param("email", LUCIA))
                .andExpect(flash().attribute("teamError", containsString("ya está en tu equipo")));
        mvc.perform(post("/worker/center/team").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("name", "Elena").param("email", TestUsers.WORKER_CENTER_2))
                .andExpect(flash().attribute("teamError", containsString("otro centro")));
        mvc.perform(post("/worker/center/team").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("name", "Sin correo").param("email", "no-es-un-correo"))
                .andExpect(flash().attribute("teamError", containsString("no es válido")))
                .andExpect(flash().attribute("inviteName", "Sin correo"));
        assertEquals(before, users.count());
    }

    @Test
    void standardWorkerSeesTheTeamButCannotChangeIt() throws Exception {
        long before = users.count();
        mvc.perform(post("/worker/center/team").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("name", "Marta").param("email", "marta@correo.com"))
                .andExpect(redirectedUrl("/worker/center"))
                .andExpect(flash().attribute("errorMessage", containsString("Solo el dueño")));
        mvc.perform(post("/worker/center/team/" + carlos.getId() + "/remove").with(csrf()).with(TestUsers.as(users, LUCIA)))
                .andExpect(flash().attribute("errorMessage", containsString("Solo el dueño")));
        assertEquals(before, users.count());
        assertEquals(center.getId(), users.findById(carlos.getId()).orElseThrow().getCenter().getId());

        mvc.perform(get("/worker/center").with(TestUsers.as(users, LUCIA)))
                .andExpect(content().string(containsString("Carlos")))
                .andExpect(content().string(containsString("invita a gente nueva")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Enviar invitación"))));
    }

    @Test
    void removedWorkerLosesAccessAtOnceButKeepsHerHistory() throws Exception {
        // Sus citas por delante pasan a Carlos; tiene unas vacaciones apuntadas
        LocalDateTime now = LocalDateTime.now();
        upcomingOf(lucia).forEach(a -> a.setWorker(carlos));
        blocks.save(AgendaBlock.builder().center(center).worker(lucia).createdBy(lucia)
                .startsAt(now.plusDays(40)).endsAt(now.plusDays(45)).reason("Vacaciones").build());
        long pastAppointments = appointments.findAll().stream()
                .filter(a -> a.getWorker() != null && a.getWorker().getId().equals(lucia.getId())).count();
        AppUserDetails openSession = TestUsers.details(users, LUCIA);

        mvc.perform(post("/worker/center/team/" + lucia.getId() + "/remove").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(redirectedUrl("/worker/center#equipo"))
                .andExpect(flash().attribute("teamMessage", containsString("ya no está en el equipo")));

        User removed = users.findById(lucia.getId()).orElseThrow();
        assertNull(removed.getCenter());
        assertTrue(removed.hasAccount());
        assertTrue(blocks.findByCenterIdAndEndsAtAfterOrderByStartsAtAsc(center.getId(), now).stream()
                .noneMatch(b -> b.getWorker() != null && b.getWorker().getId().equals(lucia.getId())));
        assertEquals(pastAppointments, appointments.findAll().stream()
                .filter(a -> a.getWorker() != null && a.getWorker().getId().equals(lucia.getId())).count());

        // La sesión que tenía abierta ya no entra en el panel
        mvc.perform(get("/worker/calendar").with(user(openSession)))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("errorMessage", containsString("Ya no formas parte del equipo de " + center.getName())));
        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(LUCIA))));
    }

    @Test
    void workerWithUpcomingAppointmentsOrTheOwnerCannotBeRemoved() throws Exception {
        Appointment upcoming = appointments.findAll().stream()
                .filter(a -> a.getCenter().getId().equals(center.getId()))
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED && a.getDateTime().isAfter(LocalDateTime.now().plusDays(1)))
                .findFirst().orElseThrow();
        upcoming.setWorker(lucia);

        mvc.perform(post("/worker/center/team/" + lucia.getId() + "/remove").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("teamError", containsString("por delante")));
        assertEquals(center.getId(), users.findById(lucia.getId()).orElseThrow().getCenter().getId());

        mvc.perform(post("/worker/center/team/" + carlos.getId() + "/remove").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("teamError", containsString("cede antes el centro")));
        assertEquals(center.getId(), users.findById(carlos.getId()).orElseThrow().getCenter().getId());
    }

    @Test
    void ownerResendsAnInvitationAndTheOldLinkStopsWorking() throws Exception {
        mvc.perform(post("/worker/center/team").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                .param("name", "Marta López").param("email", "marta.lopez@correo.com"));
        String first = invitationToken("marta.lopez@correo.com");
        User marta = users.findByEmailIgnoreCase("marta.lopez@correo.com").orElseThrow();

        mvc.perform(post("/worker/center/team/" + marta.getId() + "/resend").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("teamMessage", containsString("enviada de nuevo")));
        assertEquals(2, invitations.findByUserIdAndUsedAtIsNull(marta.getId()).size());
        mvc.perform(get("/invitation/" + first)).andExpect(content().string(containsString("ha caducado")));

        // Quien ya tiene cuenta no necesita invitación
        mvc.perform(post("/worker/center/team/" + lucia.getId() + "/resend").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("teamError", containsString("ya ha activado su cuenta")));
    }

    @Test
    void someoneWhoLeftCanBeInvitedBackWithHerAccount() throws Exception {
        upcomingOf(lucia).forEach(a -> a.setWorker(carlos));
        mvc.perform(post("/worker/center/team/" + lucia.getId() + "/remove").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)));
        assertNull(users.findById(lucia.getId()).orElseThrow().getCenter());

        mvc.perform(post("/worker/center/team").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("name", "Lucía").param("email", LUCIA))
                .andExpect(flash().attribute("teamMessage", containsString("ya tenía cuenta")));
        assertEquals(center.getId(), users.findById(lucia.getId()).orElseThrow().getCenter().getId());
    }

    private List<Appointment> upcomingOf(User worker) {
        return appointments.findAll().stream()
                .filter(a -> a.getWorker() != null && a.getWorker().getId().equals(worker.getId()))
                .filter(a -> a.getDateTime().isAfter(LocalDateTime.now().minusDays(1)))
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING || a.getStatus() == AppointmentStatus.PROPOSED
                        || a.getStatus() == AppointmentStatus.CONFIRMED)
                .toList();
    }

    /** Enlace de la última invitación enviada por correo a esa dirección. */
    private String invitationToken(String email) {
        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, timeout(5000).atLeastOnce()).send(sent.capture());
        List<EmailMessage> toEmail = sent.getAllValues().stream().filter(m -> email.equals(m.to())).toList();
        assertFalse(toEmail.isEmpty(), "No se ha enviado la invitación a " + email);
        Matcher matcher = INVITATION_LINK.matcher(toEmail.getLast().htmlBody());
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}
