package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.WorkerAppointmentForm;
import com.alexxlpz.crm_cbelleza.mail.AppointmentMailService;
import com.alexxlpz.crm_cbelleza.mail.EmailGateway;
import com.alexxlpz.crm_cbelleza.mail.EmailMessage;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.AgendaAvailabilityService;
import com.alexxlpz.crm_cbelleza.services.AgendaBlockService;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.ClientNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agenda flexible: cabinas y citas simultáneas, citas creadas y movidas por el trabajador, propuestas de
 * otras horas, anulaciones, bloqueos de agenda, campana del cliente y correos de citas.
 * Cada test va en una transacción que se deshace al terminar (los avisos tras el commit no se disparan).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgendaTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private BookingService booking;
    @Autowired
    private AgendaBlockService blocks;
    @Autowired
    private AgendaAvailabilityService availability;
    @Autowired
    private ClientNotificationService clientNotifications;
    @Autowired
    private AppointmentMailService mail;
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
        // Tratamiento de 60 min o menos, para que quepa a cualquier hora de la mañana
        treatment = treatments.findByCenterId(center.getId()).stream()
                .filter(t -> t.getDuration() <= 60)
                .findFirst()
                .orElseThrow();
    }

    /** Un lunes lejano (sin citas de ejemplo) a la hora indicada. */
    private static LocalDateTime monday(int weeksAhead, int hour) {
        LocalDate day = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return LocalDateTime.of(day, LocalTime.of(hour, 0));
    }

    private void cabins(int count) {
        center.setCabins(count);
        centers.saveAndFlush(center);
    }

    private Appointment createByWorker(User worker, LocalDateTime start, boolean outside) {
        return booking.createByWorker(center.getId(), worker.getId(), new WorkerAppointmentForm(
                sofia.getId(), null, null, null, treatment.getId(), start.toString(), worker.getId(), null, outside));
    }

    private Appointment pendingRequest(LocalDateTime start) {
        return booking.requestBooking(sofia.getId(), center.getId(), treatment.getId(), start);
    }

    /* ---------- Cabinas y profesionales ---------- */

    @Test
    void twoWorkersCanAttendAtTheSameTimeIfThereAreTwoCabins() {
        cabins(2);
        LocalDateTime start = monday(40, 10);
        createByWorker(carlos, start, false);
        Appointment second = createByWorker(lucia, start, false);
        assertEquals(AppointmentStatus.CONFIRMED, second.getStatus());
    }

    @Test
    void oneCabinMeansOneAppointmentAtATime() {
        cabins(1);
        LocalDateTime start = monday(41, 10);
        createByWorker(carlos, start, false);
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> createByWorker(lucia, start, false));
        assertTrue(error.getMessage().contains("ocupada"), error.getMessage());
    }

    @Test
    void aWorkerCannotHaveTwoAppointmentsAtOnce() {
        cabins(3);
        LocalDateTime start = monday(42, 10);
        createByWorker(carlos, start, false);
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> createByWorker(carlos, start.plusMinutes(30), false));
        assertTrue(error.getMessage().contains("ya tiene otra cita"), error.getMessage());
    }

    @Test
    void clientCannotRequestWhenNoWorkerIsFree() {
        cabins(5);  // sobran cabinas, pero el centro solo tiene dos profesionales
        LocalDateTime start = monday(15, 10);
        createByWorker(carlos, start, false);
        createByWorker(lucia, start, false);
        assertThrows(BusinessRuleException.class, () -> pendingRequest(start));
        assertTrue(availability.availability(center.getId()).occupied().contains(start.toString()),
                "el selector del cliente marca la hora como llena");
    }

    /* ---------- Citas del trabajador ---------- */

    @Test
    void workerAppointmentIsConfirmedAndCanGoOutsideOpeningHoursOnlyIfAsked() {
        LocalDateTime evening = monday(44, 21);
        assertThrows(BookingService.OutsideOpeningHoursException.class, () -> createByWorker(carlos, evening, false));
        Appointment created = createByWorker(carlos, evening, true);
        assertEquals(AppointmentStatus.CONFIRMED, created.getStatus());
        assertEquals(AppointmentChange.CREATED_BY_CENTER, created.getLastChange());
        assertEquals(carlos.getId(), created.getWorker().getId());
    }

    @Test
    void rescheduleKeepsItConfirmedAndRemembersThePreviousTime() {
        cabins(2);
        LocalDateTime start = monday(45, 10);
        Appointment app = createByWorker(carlos, start, false);

        booking.reschedule(center.getId(), app.getId(), start.plusHours(2).toString(), lucia.getId(), false);

        Appointment moved = appointments.findById(app.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CONFIRMED, moved.getStatus());
        assertEquals(start, moved.getPreviousDateTime());
        assertEquals(start.plusHours(2), moved.getDateTime());
        assertEquals(lucia.getId(), moved.getWorker().getId());
        assertEquals(AppointmentChange.RESCHEDULED, moved.getLastChange());
    }

    @Test
    void approveAssignsTheChosenWorker() {
        Appointment request = pendingRequest(monday(46, 11));
        booking.approve(center.getId(), request.getId(), carlos.getId(), lucia.getId(), "Te esperamos");
        Appointment confirmed = appointments.findById(request.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(lucia.getId(), confirmed.getWorker().getId());
    }

    /* ---------- Propuestas ---------- */

    @Test
    void clientAcceptsOneOfTheProposedTimes() {
        Appointment request = pendingRequest(monday(47, 10));
        LocalDateTime option = monday(47, 12);
        booking.propose(center.getId(), request.getId(), carlos.getId(), List.of(option.toString(), monday(47, 16).toString()), "¿Te viene bien?");

        Appointment proposed = appointments.findById(request.getId()).orElseThrow();
        assertEquals(AppointmentStatus.PROPOSED, proposed.getStatus());
        assertEquals(2, proposed.getProposals().size());
        assertNotNull(proposed.getProposalExpiresAt());

        Long chosen = proposed.getProposals().getFirst().getId();
        booking.acceptProposal(sofia.getId(), request.getId(), chosen);

        Appointment confirmed = appointments.findById(request.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(option, confirmed.getDateTime());
        assertTrue(confirmed.getProposals().isEmpty());
        assertEquals(AppointmentChange.PROPOSAL_ACCEPTED, confirmed.getLastChange());
    }

    @Test
    void proposedTimesAreReservedForThatClient() {
        cabins(1);
        Appointment request = pendingRequest(monday(16, 10));
        LocalDateTime option = monday(16, 12);
        booking.propose(center.getId(), request.getId(), carlos.getId(), List.of(option.toString()), null);

        // Otro cliente no puede pedir esa hora mientras la propuesta siga abierta
        assertThrows(BusinessRuleException.class, () -> pendingRequest(option));
        assertTrue(availability.availability(center.getId()).occupied().contains(option.toString()));
    }

    @Test
    void workerCanTakeAProposedTimeAndTheProposalLosesIt() {
        cabins(1);
        Appointment request = pendingRequest(monday(49, 10));
        LocalDateTime option = monday(49, 12);
        booking.propose(center.getId(), request.getId(), carlos.getId(), List.of(option.toString()), null);

        createByWorker(lucia, option, false);

        Appointment back = appointments.findById(request.getId()).orElseThrow();
        assertEquals(AppointmentStatus.PENDING, back.getStatus(), "sin horas propuestas, vuelve a estar pendiente");
        assertTrue(back.getProposals().isEmpty());
    }

    @Test
    void declinedAndExpiredProposalsAreCancelled() {
        Appointment declined = pendingRequest(monday(50, 10));
        booking.propose(center.getId(), declined.getId(), carlos.getId(), List.of(monday(50, 12).toString()), null);
        booking.declineProposal(sofia.getId(), declined.getId());
        assertEquals(AppointmentStatus.CANCELLED, appointments.findById(declined.getId()).orElseThrow().getStatus());

        Appointment expiring = pendingRequest(monday(51, 10));
        booking.propose(center.getId(), expiring.getId(), carlos.getId(), List.of(monday(51, 12).toString()), null);
        Appointment proposed = appointments.findById(expiring.getId()).orElseThrow();
        proposed.setProposalExpiresAt(LocalDateTime.now().minusMinutes(1));
        appointments.saveAndFlush(proposed);

        assertTrue(booking.expireProposals() >= 1);
        Appointment expired = appointments.findById(expiring.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CANCELLED, expired.getStatus());
        assertEquals(AppointmentChange.PROPOSAL_EXPIRED, expired.getLastChange());
    }

    @Test
    void atMostThreeProposals() {
        Appointment request = pendingRequest(monday(52, 10));
        List<String> four = List.of(monday(52, 11).toString(), monday(52, 12).toString(), monday(52, 13).toString(), monday(52, 14).toString());
        assertThrows(BusinessRuleException.class, () -> booking.propose(center.getId(), request.getId(), carlos.getId(), four, null));
    }

    /* ---------- Anulaciones ---------- */

    @Test
    void clientCancelsOwnAppointmentButNotSomeoneElses() throws Exception {
        Appointment request = pendingRequest(monday(53, 10));
        User other = users.findByEmailIgnoreCase("carlos@cbelleza.com").orElseThrow();

        // Otro usuario (aquí, un trabajador) no puede tocar citas de un cliente: no es suya → 404 / 403
        mvc.perform(post("/client/appointments/" + request.getId() + "/cancel").with(csrf())
                        .with(TestUsers.as(users, other.getEmail())))
                .andExpect(status().isForbidden());

        booking.cancelByClient(sofia.getId(), request.getId());
        Appointment cancelled = appointments.findById(request.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus());
        assertEquals(AppointmentChange.CANCELLED_BY_CLIENT, cancelled.getLastChange());
    }

    @Test
    void centerCancelsAConfirmedAppointment() {
        Appointment app = createByWorker(carlos, monday(54, 10), false);
        booking.cancelByCenter(center.getId(), app.getId(), "Imprevisto");
        assertEquals(AppointmentStatus.CANCELLED, appointments.findById(app.getId()).orElseThrow().getStatus());
    }

    /* ---------- Bloqueos ---------- */

    @Test
    void blockedWorkerCannotBeBooked() {
        LocalDateTime start = monday(55, 9);
        blocks.create(center.getId(), carlos.getId(), carlos.getId(), false, start.toString(), start.plusHours(5).toString(), "Médico");
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> createByWorker(carlos, start.plusHours(1), false));
        assertTrue(error.getMessage().contains("no está disponible"), error.getMessage());
        // Lucía sí puede atender
        createByWorker(lucia, start.plusHours(1), false);
    }

    @Test
    void closedCenterRejectsRequestsAndShowsTheDayClosed() {
        LocalDateTime day = monday(17, 0);
        blocks.create(center.getId(), carlos.getId(), null, true, day.toString(), day.plusDays(1).toString(), "Festivo");
        assertThrows(BusinessRuleException.class, () -> pendingRequest(day.withHour(10)));
        assertTrue(availability.availability(center.getId()).closedDates().contains(day.toLocalDate().toString()));
    }

    @Test
    void cannotBlockAPeriodWithConfirmedAppointments() {
        LocalDateTime start = monday(57, 10);
        createByWorker(carlos, start, false);
        assertThrows(BusinessRuleException.class, () -> blocks.create(center.getId(), carlos.getId(), carlos.getId(), false,
                start.minusHours(1).toString(), start.plusHours(1).toString(), null));
    }

    /* ---------- Aislamiento entre centros ---------- */

    @Test
    void workerOfAnotherCenterCannotTouchTheseAppointmentsOrBlocks() throws Exception {
        Appointment request = pendingRequest(monday(58, 10));
        AgendaBlock block = blocks.create(center.getId(), carlos.getId(), carlos.getId(), false,
                monday(58, 15).toString(), monday(58, 16).toString(), null);
        String elena = TestUsers.WORKER_CENTER_2;

        mvc.perform(post("/worker/appointments/" + request.getId() + "/propose").param("dateTime", monday(58, 12).toString())
                .with(csrf()).with(TestUsers.as(users, elena))).andExpect(status().isNotFound());
        mvc.perform(post("/worker/appointments/" + request.getId() + "/cancel")
                .with(csrf()).with(TestUsers.as(users, elena))).andExpect(status().isNotFound());
        mvc.perform(post("/worker/appointments/" + request.getId() + "/reschedule").param("dateTime", monday(58, 12).toString())
                .with(csrf()).with(TestUsers.as(users, elena))).andExpect(status().isNotFound());
        mvc.perform(post("/worker/agenda-blocks/" + block.getId() + "/delete")
                .with(csrf()).with(TestUsers.as(users, elena))).andExpect(status().isNotFound());
        // Ni crear una cita para un profesional de otro centro
        mvc.perform(post("/worker/appointments").with(csrf()).with(TestUsers.as(users, elena))
                        .param("clientId", String.valueOf(sofia.getId()))
                        .param("treatmentId", String.valueOf(treatment.getId()))
                        .param("dateTime", monday(58, 11).toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCannotAcceptTheProposalOfAnotherClient() {
        Appointment request = pendingRequest(monday(59, 10));
        booking.propose(center.getId(), request.getId(), carlos.getId(), List.of(monday(59, 12).toString()), null);
        Long proposalId = appointments.findById(request.getId()).orElseThrow().getProposals().getFirst().getId();
        User otherClient = users.findByEmailIgnoreCase("carlos@cbelleza.com").orElseThrow();
        assertThrows(com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException.class,
                () -> booking.acceptProposal(otherClient.getId(), request.getId(), proposalId));
    }

    /* ---------- API de la agenda ---------- */

    @Test
    void agendaApiCreatesAppointmentsAndAsksBeforeGoingOutsideHours() throws Exception {
        String evening = monday(60, 21).toString();
        mvc.perform(post("/worker/appointments").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("newClientName", "Clienta Telefónica")
                        .param("newClientPhone", "+34 611 222 333")
                        .param("treatmentId", String.valueOf(treatment.getId()))
                        .param("dateTime", evening))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_HOURS"));

        mvc.perform(post("/worker/appointments").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("newClientName", "Clienta Telefónica")
                        .param("newClientPhone", "+34 611 222 333")
                        .param("treatmentId", String.valueOf(treatment.getId()))
                        .param("dateTime", evening)
                        .param("allowOutsideHours", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.client.name").value("Clienta Telefónica"));
    }

    @Test
    void calendarPageHasTheNewForms() throws Exception {
        mvc.perform(get("/worker/calendar").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"newAppointmentModal\"")))
                .andExpect(content().string(containsString("id=\"proposeModal\"")))
                .andExpect(content().string(containsString("id=\"blockModal\"")));
    }

    /* ---------- Cliente: «Mis Citas» y campana ---------- */

    @Test
    void clientSeesTheProposalAndTheBellWarnsHim() throws Exception {
        Appointment request = pendingRequest(monday(61, 10));
        booking.propose(center.getId(), request.getId(), carlos.getId(), List.of(monday(61, 12).toString()), null);

        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El centro te propone otra hora")))
                .andExpect(content().string(containsString("Ninguna me viene bien")))
                .andExpect(content().string(containsString("Avisos de tus citas")));

        var bell = clientNotifications.forClient(sofia.getId());
        assertTrue(bell.items().stream().anyMatch(n -> n.message().contains("te propone otras horas")));
        assertTrue(bell.unseen() > 0);

        clientNotifications.markSeen(sofia.getId());
        assertEquals(0, clientNotifications.forClient(sofia.getId()).items().stream()
                .filter(n -> n.unseen() && n.message().contains("te propone")).count());
    }

    /* ---------- Correos ---------- */

    @Test
    void confirmationAndProposalEmailsGoToTheClient() {
        Appointment request = pendingRequest(monday(62, 10));
        booking.approve(center.getId(), request.getId(), carlos.getId(), null, "Te esperamos");
        mail.notifyChange(request.getId(), AppointmentChange.CONFIRMED);

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, atLeastOnce()).send(sent.capture());
        EmailMessage message = sent.getValue();
        assertEquals(sofia.getEmail(), message.to());
        assertTrue(message.subject().startsWith("Cita confirmada"), message.subject());
        assertTrue(message.htmlBody().contains("Te esperamos"));
    }

    @Test
    void clientsWithoutRealEmailGetNoEmails() {
        Appointment app = booking.createByWorker(center.getId(), carlos.getId(), new WorkerAppointmentForm(
                null, "Sin Correo", "+34 622 333 444", null, treatment.getId(), monday(63, 10).toString(), null, null, false));
        org.mockito.Mockito.clearInvocations(emailGateway);
        mail.notifyChange(app.getId(), AppointmentChange.CREATED_BY_CENTER);
        verify(emailGateway, org.mockito.Mockito.never()).send(any());
    }

    @Test
    void reminderIsMarkedBeforeSendingAndNeverSentTwice() {
        // Se guarda directamente: con la agenda, el profesional podría tener a esa hora una cita de los datos demo
        Appointment app = appointments.saveAndFlush(Appointment.builder()
                .dateTime(LocalDateTime.now().plusHours(3).withMinute(0).withSecond(0).withNano(0))
                .treatment(treatment)
                .center(center)
                .client(sofia)
                .worker(carlos)
                .status(AppointmentStatus.CONFIRMED)
                .price(treatment.getPrice())
                .workerMessage("")
                .createdAt(LocalDateTime.now())
                .build());
        org.mockito.Mockito.clearInvocations(emailGateway);

        assertTrue(mail.sendReminders() >= 1);
        assertEquals(0, mail.sendReminders(), "una cita ya recordada no se vuelve a recordar");

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, atLeastOnce()).send(sent.capture());
        assertEquals(1, sent.getAllValues().stream()
                .filter(m -> m.to().equals(sofia.getEmail()) && m.subject().startsWith("Recordatorio"))
                .count());
        assertTrue(appointments.findByStatusAndReminderSentAtIsNullAndDateTimeBetween(AppointmentStatus.CONFIRMED,
                app.getDateTime().minusMinutes(1), app.getDateTime().plusMinutes(1)).isEmpty());
    }
}
