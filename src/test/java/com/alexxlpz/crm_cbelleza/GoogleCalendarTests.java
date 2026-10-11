package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.GoogleCalendarLink;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleAuthorization;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleCalendarGateway;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleCalendarService;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.GoogleCalendarLinkRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Interruptor «Guardar mis citas en Google Calendar» de «Mi Centro», con Google simulado. */
@SpringBootTest(properties = {
        "crm.security.google.client-id=test-client-id",
        "crm.security.google.client-secret=test-client-secret",
        "crm.google-calendar.token-key=clave-de-pruebas"
})
@AutoConfigureMockMvc
@Transactional
class GoogleCalendarTests {

    private static final String REFRESH_TOKEN = "refresh-token-de-google";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private GoogleCalendarLinkRepository links;
    @Autowired
    private GoogleCalendarService calendarService;

    @MockitoBean
    private GoogleCalendarGateway google;

    @BeforeEach
    void fakeGoogle() {
        when(google.exchangeCode(eq("codigo-ok"), anyString()))
                .thenReturn(new GoogleAuthorization(REFRESH_TOKEN, "carlos.personal@gmail.com"));
        when(google.createCalendar(eq(REFRESH_TOKEN), anyString(), anyString())).thenReturn("calendario-cuquora");
        when(google.insertEvent(eq(REFRESH_TOKEN), eq("calendario-cuquora"), any())).thenReturn("evento-1");
    }

    @Test
    void profileShowsSwitchOff() throws Exception {
        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Guardar mis citas en Google Calendar"),
                        containsString("role=\"switch\" aria-checked=\"false\""),
                        containsString("/worker/google-calendar/connect"))));
    }

    @Test
    void switchSendsWorkerToGooglePermissionScreen() throws Exception {
        mvc.perform(post("/worker/google-calendar/connect").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth"),
                        containsString("client_id=test-client-id"),
                        containsString("calendar.app.created"),
                        containsString("access_type=offline"),
                        containsString("redirect_uri=http://localhost/worker/google-calendar/callback"))));
    }

    @Test
    void clientsCannotConnectCalendar() throws Exception {
        mvc.perform(post("/worker/google-calendar/connect").with(csrf())
                        .with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void callbackStoresEncryptedTokenAndCopiesUpcomingAppointments() throws Exception {
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        Appointment upcoming = confirmedAppointmentFor(carlos);

        connect(TestUsers.WORKER_CENTER_1, "codigo-ok");

        GoogleCalendarLink link = links.findByWorkerId(carlos.getId()).orElseThrow();
        assertEquals("calendario-cuquora", link.getCalendarId());
        assertEquals("carlos.personal@gmail.com", link.getGoogleEmail());
        assertNotEquals(REFRESH_TOKEN, link.getEncryptedRefreshToken(), "el token se guarda cifrado");
        assertEquals("evento-1", appointments.findById(upcoming.getId()).orElseThrow().getGoogleEventId());

        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(allOf(
                        containsString("role=\"switch\" aria-checked=\"true\""),
                        containsString("carlos.personal@gmail.com"))));
    }

    @Test
    void calendarAndEventsUseTheCenterTimeZone() throws Exception {
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        confirmedAppointmentFor(carlos);
        connect(TestUsers.WORKER_CENTER_1, "codigo-ok");
        verify(google).createCalendar(REFRESH_TOKEN, "Cuquora · Centro Histórico", "Europe/Madrid");
        verify(google, atLeastOnce()).insertEvent(eq(REFRESH_TOKEN), eq("calendario-cuquora"), any());
        verify(google, never()).insertEvent(anyString(), anyString(),
                argThat(event -> !event.timeZone().equals("Europe/Madrid")));

        connect("ana@cbelleza.com", "codigo-ok");
        verify(google).createCalendar(REFRESH_TOKEN, "Cuquora · Las Condes", "America/Santiago");
    }

    @Test
    void workerCanChangeCenterTimeZoneOnlyToListedZones() throws Exception {
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        mvc.perform(post("/worker/center").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("workerName", carlos.getName()).param("timeZone", "Mars/Olympus"))
                .andExpect(redirectedUrl("/worker/center"));
        assertEquals("Europe/Madrid", users.findWithCenterById(carlos.getId()).orElseThrow().getCenter().getTimeZone());

        mvc.perform(post("/worker/center").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("workerName", carlos.getName()).param("timeZone", "Atlantic/Canary"))
                .andExpect(redirectedUrl("/worker/center"));
        assertEquals("Atlantic/Canary", users.findWithCenterById(carlos.getId()).orElseThrow().getCenter().getTimeZone());
    }

    @Test
    void callbackWithForeignStateIsIgnored() throws Exception {
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/worker/google-calendar/connect").with(csrf()).session(session)
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)));

        mvc.perform(get("/worker/google-calendar/callback").param("code", "codigo-ok").param("state", "otro")
                        .session(session).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(redirectedUrl("/worker/center"));

        assertTrue(links.findByWorkerId(carlos.getId()).isEmpty());
        verify(google, never()).exchangeCode(anyString(), anyString());
    }

    @Test
    void rejectedAppointmentIsRemovedFromGoogle() throws Exception {
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        Appointment upcoming = confirmedAppointmentFor(carlos);
        connect(TestUsers.WORKER_CENTER_1, "codigo-ok");

        upcoming.setStatus(AppointmentStatus.REJECTED);
        appointments.save(upcoming);
        calendarService.syncAppointment(upcoming.getId());

        verify(google).deleteEvent(REFRESH_TOKEN, "calendario-cuquora", "evento-1");
        assertNull(appointments.findById(upcoming.getId()).orElseThrow().getGoogleEventId());
    }

    @Test
    void cancelledAppointmentIsRemovedAndWorkerChangeMovesTheEvent() throws Exception {
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        User lucia = users.findByEmailIgnoreCase("lucia.romero@cbelleza.com").orElseThrow();
        Appointment upcoming = confirmedAppointmentFor(carlos);
        connect(TestUsers.WORKER_CENTER_1, "codigo-ok");
        assertEquals(carlos.getId(), appointments.findById(upcoming.getId()).orElseThrow().getGoogleEventOwnerId());

        // Pasa a Lucía (sin Google Calendar): el evento se borra del calendario de Carlos
        upcoming.setWorker(lucia);
        appointments.save(upcoming);
        calendarService.syncAppointment(upcoming.getId());
        verify(google).deleteEvent(REFRESH_TOKEN, "calendario-cuquora", "evento-1");
        assertNull(appointments.findById(upcoming.getId()).orElseThrow().getGoogleEventId());

        // Vuelve a Carlos y se anula: se crea y luego se borra
        upcoming.setWorker(carlos);
        appointments.save(upcoming);
        calendarService.syncAppointment(upcoming.getId());
        assertEquals("evento-1", appointments.findById(upcoming.getId()).orElseThrow().getGoogleEventId());
        upcoming.setStatus(AppointmentStatus.CANCELLED);
        appointments.save(upcoming);
        calendarService.syncAppointment(upcoming.getId());
        assertNull(appointments.findById(upcoming.getId()).orElseThrow().getGoogleEventId());
    }

    @Test
    void disconnectOnlyAffectsOwnCalendar() throws Exception {
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        connect(TestUsers.WORKER_CENTER_1, "codigo-ok");

        // Otro trabajador (de otro centro) apaga su interruptor: no toca la conexión de Carlos
        mvc.perform(post("/worker/google-calendar/disconnect").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(redirectedUrl("/worker/center"));
        assertTrue(links.findByWorkerId(carlos.getId()).isPresent());

        mvc.perform(post("/worker/google-calendar/disconnect").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(redirectedUrl("/worker/center"));
        assertTrue(links.findByWorkerId(carlos.getId()).isEmpty());
        verify(google).deleteCalendar(REFRESH_TOKEN, "calendario-cuquora");
        verify(google).revoke(REFRESH_TOKEN);
    }

    /** Pulsa el interruptor y simula la vuelta de Google con el mismo "state". */
    private void connect(String worker, String code) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String location = mvc.perform(post("/worker/google-calendar/connect").with(csrf()).session(session)
                        .with(TestUsers.as(users, worker)))
                .andReturn().getResponse().getHeader("Location");
        Matcher state = Pattern.compile("[?&]state=([^&]+)").matcher(location);
        assertTrue(state.find());

        mvc.perform(get("/worker/google-calendar/callback").param("code", code).param("state", state.group(1))
                        .session(session).with(TestUsers.as(users, worker)))
                .andExpect(redirectedUrl("/worker/center"));
    }

    private Appointment confirmedAppointmentFor(User worker) {
        Long centerId = worker.getCenter().getId();
        return appointments.save(Appointment.builder()
                .dateTime(LocalDate.now().plusDays(3).atTime(10, 0))
                .treatment(treatments.findByCenterId(centerId).getFirst())
                .center(worker.getCenter())
                .client(users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow())
                .worker(worker)
                .status(AppointmentStatus.CONFIRMED)
                .workerMessage("")
                .build());
    }
}
