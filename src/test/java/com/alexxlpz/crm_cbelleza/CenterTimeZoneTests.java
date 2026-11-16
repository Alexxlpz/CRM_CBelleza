package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.mail.AppointmentMailService;
import com.alexxlpz.crm_cbelleza.mail.EmailGateway;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.AppointmentLifecycleService;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;

/**
 * Las citas se guardan en la hora local de su centro: completarlas, recordarlas y decir si ya han pasado tiene que
 * hacerse con la hora del centro, no con la del servidor (España). Se prueba con un centro en México (6-7 h menos).
 */
@SpringBootTest
@Transactional
class CenterTimeZoneTests {

    private static final String MEXICO = "America/Mexico_City";

    @Autowired
    private AppointmentLifecycleService lifecycle;
    @Autowired
    private AppointmentMailService mail;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private UserRepository users;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private EmailGateway emailGateway;

    private User carlos;
    private User sofia;
    private Center center;
    private Treatment treatment;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        center = centers.findById(carlos.getCenter().getId()).orElseThrow();
        center.setTimeZone(MEXICO);
        centers.saveAndFlush(center);
        treatment = treatments.findByCenterId(center.getId()).stream()
                .filter(t -> t.getDuration() <= 60)
                .findFirst()
                .orElseThrow();
    }

    private static LocalDateTime mexicoNow() {
        return LocalDateTime.now(ZoneId.of(MEXICO)).truncatedTo(ChronoUnit.MINUTES);
    }

    /** Se guarda directamente: con la agenda, el profesional podría tener a esa hora una cita de los datos demo. */
    private Appointment confirmedAt(LocalDateTime start) {
        return appointments.saveAndFlush(Appointment.builder()
                .dateTime(start)
                .treatment(treatment)
                .center(center)
                .client(sofia)
                .worker(carlos)
                .status(AppointmentStatus.CONFIRMED)
                .price(treatment.getPrice())
                .workerMessage("")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private AppointmentStatus storedStatus(Appointment app) {
        entityManager.flush();
        entityManager.clear();
        return appointments.findById(app.getId()).orElseThrow().getStatus();
    }

    @Test
    void appointmentLaterTodayInTheCenterIsNotCompletedEarly() {
        // En hora de España ya habría pasado: en México aún faltan dos horas
        Appointment later = confirmedAt(mexicoNow().plusHours(2));

        lifecycle.completePastAppointments();

        assertEquals(AppointmentStatus.CONFIRMED, storedStatus(later));
        Appointment reloaded = appointments.findById(later.getId()).orElseThrow();
        assertTrue(reloaded.isUpcoming(), "la cita aún no ha empezado en la hora del centro");
    }

    @Test
    void appointmentThatEndedInTheCenterIsCompleted() {
        Appointment finished = confirmedAt(mexicoNow().minusMinutes(treatment.getDuration() + 10L));

        lifecycle.completePastAppointments();

        assertEquals(AppointmentStatus.COMPLETED, storedStatus(finished));
    }

    @Test
    void clientSeesTheAppointmentAsUpcomingWithTheCenterTime() {
        Appointment later = confirmedAt(mexicoNow().plusHours(2));

        var split = appointmentService.getClientAppointmentsSplit(sofia.getId());

        assertTrue(split.upcoming().stream().anyMatch(a -> a.getId().equals(later.getId())));
        assertFalse(split.history().stream().anyMatch(a -> a.getId().equals(later.getId())));
    }

    @Test
    void remindersCountTheNext24HoursInTheCenterTime() {
        // Dentro de 2 h en México: en hora de España «ya ha pasado», pero hay que recordarla
        Appointment soon = confirmedAt(mexicoNow().plusHours(2));
        // Dentro de 27 h en México: en hora de España caería en las próximas 24 h, pero aún es pronto
        Appointment dayAfter = confirmedAt(mexicoNow().plusHours(27));
        clearInvocations(emailGateway);

        mail.sendReminders();

        entityManager.flush();
        entityManager.clear();
        assertNotNull(appointments.findById(soon.getId()).orElseThrow().getReminderSentAt());
        assertNull(appointments.findById(dayAfter.getId()).orElseThrow().getReminderSentAt());
    }
}
