package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.FieldException;
import com.alexxlpz.crm_cbelleza.forms.CenterForm;
import com.alexxlpz.crm_cbelleza.forms.WorkerAppointmentForm;
import com.alexxlpz.crm_cbelleza.mail.EmailGateway;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.AgendaBlockService;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Punto 7 de la revisión del 26 nov 2026: cerrar el centro o bloquear una agenda dejaba colgadas las solicitudes
 * sin responder y las horas propuestas de ese periodo, y bajar las cabinas dejaba citas confirmadas sin sitio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgendaClosingTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private BookingService booking;
    @Autowired
    private AgendaBlockService blocks;
    @Autowired
    private AgendaBlockRepository blockRepository;
    @Autowired
    private CenterService centerService;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;

    @MockitoBean
    private EmailGateway emailGateway;

    private User carlos;
    private User lucia;
    private User sofia;
    private Center center;
    private Treatment treatment;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        lucia = users.findByEmailIgnoreCase("lucia.romero@cbelleza.com").orElseThrow();
        sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        center = centers.findById(carlos.getCenter().getId()).orElseThrow();
        treatment = treatments.findByCenterId(center.getId()).stream()
                .filter(t -> t.getDuration() <= 60)
                .findFirst()
                .orElseThrow();
    }

    /** Un día laborable lejano (sin citas de ejemplo) a la hora indicada. */
    private static LocalDateTime day(int weeksAhead, DayOfWeek dayOfWeek, int hour) {
        LocalDate date = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(dayOfWeek));
        return LocalDateTime.of(date, LocalTime.of(hour, 0));
    }

    private Appointment request(LocalDateTime start, Long workerId) {
        return booking.requestBooking(sofia.getId(), center.getId(), treatment.getId(), workerId, start);
    }

    private Appointment reload(Appointment appointment) {
        return appointments.findById(appointment.getId()).orElseThrow();
    }

    private void closeCenter(LocalDateTime from, LocalDateTime to, boolean release) {
        blocks.create(center.getId(), carlos.getId(), null, true, from.toString(), to.toString(), "Festivo", release);
    }

    @Test
    void closingTheCenterAsksBeforeRejectingOpenRequests() {
        LocalDateTime start = day(30, DayOfWeek.MONDAY, 11);
        Appointment pending = request(start, null);
        long before = blockRepository.count();

        AgendaBlockService.OpenRequestsException ask = assertThrows(AgendaBlockService.OpenRequestsException.class,
                () -> closeCenter(start.toLocalDate().atStartOfDay(), start.toLocalDate().plusDays(1).atStartOfDay(), false));
        assertTrue(ask.getMessage().contains("1 solicitud sin responder"), ask.getMessage());
        assertEquals(before, blockRepository.count(), "Sin confirmar no se bloquea nada");
        assertEquals(AppointmentStatus.PENDING, reload(pending).getStatus());

        closeCenter(start.toLocalDate().atStartOfDay(), start.toLocalDate().plusDays(1).atStartOfDay(), true);
        Appointment rejected = reload(pending);
        assertEquals(AppointmentStatus.REJECTED, rejected.getStatus());
        assertTrue(rejected.getWorkerMessage().contains("cerrado"), rejected.getWorkerMessage());
        assertEquals(before + 1, blockRepository.count());
    }

    @Test
    void blockingRemovesOnlyTheProposedHoursInsideAndRejectsWhenNoneAreLeft() {
        Appointment proposed = request(day(31, DayOfWeek.MONDAY, 11), null);
        LocalDateTime tuesday = day(31, DayOfWeek.TUESDAY, 10);
        LocalDateTime wednesday = day(31, DayOfWeek.WEDNESDAY, 10);
        booking.propose(center.getId(), proposed.getId(), carlos.getId(), List.of(tuesday.toString(), wednesday.toString()), null);

        AgendaBlockService.OpenRequestsException ask = assertThrows(AgendaBlockService.OpenRequestsException.class,
                () -> closeCenter(tuesday.toLocalDate().atStartOfDay(), tuesday.toLocalDate().plusDays(1).atStartOfDay(), false));
        assertTrue(ask.getMessage().contains("1 propuesta"), ask.getMessage());

        closeCenter(tuesday.toLocalDate().atStartOfDay(), tuesday.toLocalDate().plusDays(1).atStartOfDay(), true);
        Appointment trimmed = reload(proposed);
        assertEquals(AppointmentStatus.PROPOSED, trimmed.getStatus());
        assertEquals(1, trimmed.getProposals().size());
        assertEquals(wednesday, trimmed.getProposals().getFirst().getDateTime());

        closeCenter(wednesday.toLocalDate().atStartOfDay(), wednesday.toLocalDate().plusDays(1).atStartOfDay(), true);
        Appointment rejected = reload(proposed);
        assertEquals(AppointmentStatus.REJECTED, rejected.getStatus());
        assertTrue(rejected.getProposals().isEmpty());
    }

    @Test
    void aWorkerBlockOnlyTouchesRequestsForThatWorker() {
        LocalDateTime start = day(32, DayOfWeek.MONDAY, 11);
        Appointment anyone = request(start, null);
        Appointment withLucia = request(start.plusHours(2), lucia.getId());

        // Carlos bloquea su agenda: ni la de «Cualquiera» ni la de Lucía le tocan
        assertDoesNotThrow(() -> blocks.create(center.getId(), carlos.getId(), carlos.getId(), false,
                start.withHour(9).toString(), start.withHour(20).toString(), null, false));

        blocks.create(center.getId(), lucia.getId(), lucia.getId(), false,
                start.withHour(9).toString(), start.withHour(20).toString(), null, true);
        assertEquals(AppointmentStatus.PENDING, reload(anyone).getStatus());
        Appointment rejected = reload(withLucia);
        assertEquals(AppointmentStatus.REJECTED, rejected.getStatus());
        assertTrue(rejected.getWorkerMessage().startsWith(lucia.getName()), rejected.getWorkerMessage());
    }

    @Test
    void theAgendaGetsACodeToAskBeforeBlocking() throws Exception {
        LocalDateTime start = day(33, DayOfWeek.MONDAY, 11);
        request(start, null);
        mvc.perform(post("/worker/agenda-blocks").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("wholeCenter", "true")
                        .param("startsAt", start.withHour(0).toString())
                        .param("endsAt", start.plusDays(1).withHour(0).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPEN_REQUESTS"))
                .andExpect(jsonPath("$.message").value(containsString("se rechazará")));
        mvc.perform(post("/worker/agenda-blocks").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("wholeCenter", "true")
                        .param("startsAt", start.withHour(0).toString())
                        .param("endsAt", start.plusDays(1).withHour(0).toString())
                        .param("releaseRequests", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void cabinsCannotGoBelowTheAppointmentsAlreadyConfirmedAtOnce() {
        center.setCabins(2);
        centers.saveAndFlush(center);
        LocalDateTime start = day(34, DayOfWeek.MONDAY, 10);
        for (User worker : List.of(carlos, lucia)) {
            booking.createByWorker(center.getId(), worker.getId(), new WorkerAppointmentForm(
                    sofia.getId(), null, null, null, treatment.getId(), start.toString(), worker.getId(), null, false));
        }

        FieldException tooFew = assertThrows(FieldException.class, () -> centerService.updateCenter(center.getId(),
                new CenterForm(null, null, null, null, null, null, null, 1)));
        assertEquals("cabins", tooFew.getField());
        assertTrue(tooFew.getMessage().contains("2 citas confirmadas a la vez"), tooFew.getMessage());
        assertEquals(2, centers.findById(center.getId()).orElseThrow().getCabins());

        // Subir siempre se puede
        assertDoesNotThrow(() -> centerService.updateCenter(center.getId(), new CenterForm(null, null, null, null, null, null, null, 3)));
        assertDoesNotThrow(() -> centerService.updateCenter(center.getId(), new CenterForm(null, null, null, null, null, null, null, 2)));
    }
}
