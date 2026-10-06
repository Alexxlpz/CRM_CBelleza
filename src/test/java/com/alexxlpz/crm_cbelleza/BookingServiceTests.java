package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reglas de reserva: horario, tratamiento del centro, franjas ocupadas y aislamiento por centro. */
@SpringBootTest
class BookingServiceTests {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private UserRepository users;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;

    /** Un lunes lejano a la hora indicada, para no chocar con las citas de ejemplo. */
    private static LocalDateTime farMonday(int weeksAhead, int hour) {
        LocalDate monday = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return LocalDateTime.of(monday, LocalTime.of(hour, 0));
    }

    private User client() {
        return users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
    }

    private Long centerOf(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getCenter().getId();
    }

    private Treatment treatmentOf(Long centerId) {
        return treatments.findByCenterId(centerId).getFirst();
    }

    @Test
    void bookingIsCreatedAsPending() {
        Long center = centerOf(TestUsers.WORKER_CENTER_1);
        Appointment app = bookingService.requestBooking(client().getId(), center, treatmentOf(center).getId(), farMonday(20, 10));
        assertEquals(AppointmentStatus.PENDING, app.getStatus());
    }

    @Test
    void treatmentMustBelongToTheCenter() {
        Long center1 = centerOf(TestUsers.WORKER_CENTER_1);
        Long center2 = centerOf(TestUsers.WORKER_CENTER_2);
        assertThrows(BusinessRuleException.class, () -> bookingService.requestBooking(
                client().getId(), center1, treatmentOf(center2).getId(), farMonday(21, 10)));
    }

    @Test
    void bookingOutsideOpeningHoursIsRejected() {
        Long center = centerOf(TestUsers.WORKER_CENTER_1);
        LocalDateTime sunday = farMonday(22, 10).minusDays(1);
        assertThrows(BusinessRuleException.class, () -> bookingService.requestBooking(
                client().getId(), center, treatmentOf(center).getId(), sunday));
        assertThrows(BusinessRuleException.class, () -> bookingService.requestBooking(
                client().getId(), center, treatmentOf(center).getId(), farMonday(22, 7)));
    }

    @Test
    void secondApprovalOfTheSameSlotIsRejected() {
        Long center = centerOf(TestUsers.WORKER_CENTER_1);
        Long treatmentId = treatmentOf(center).getId();
        LocalDateTime slot = farMonday(23, 11);
        Long workerId = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getId();

        Appointment first = bookingService.requestBooking(client().getId(), center, treatmentId, slot);
        Appointment second = bookingService.requestBooking(client().getId(), center, treatmentId, slot);

        bookingService.approve(center, first.getId(), workerId, "ok");
        assertThrows(BusinessRuleException.class, () -> bookingService.approve(center, second.getId(), workerId, "ok"));
        assertEquals(AppointmentStatus.PENDING, appointments.findById(second.getId()).orElseThrow().getStatus());
    }

    @Test
    void cannotRequestAnAlreadyConfirmedSlot() {
        Long center = centerOf(TestUsers.WORKER_CENTER_1);
        Long treatmentId = treatmentOf(center).getId();
        LocalDateTime slot = farMonday(24, 12);
        Long workerId = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getId();

        Appointment confirmed = bookingService.requestBooking(client().getId(), center, treatmentId, slot);
        bookingService.approve(center, confirmed.getId(), workerId, null);

        assertThrows(BusinessRuleException.class,
                () -> bookingService.requestBooking(client().getId(), center, treatmentId, slot));
    }

    @Test
    void cannotRejectAppointmentOfAnotherCenter() {
        Long center1 = centerOf(TestUsers.WORKER_CENTER_1);
        Long center2 = centerOf(TestUsers.WORKER_CENTER_2);
        Appointment app = bookingService.requestBooking(client().getId(), center1, treatmentOf(center1).getId(), farMonday(25, 10));
        assertThrows(ResourceNotFoundException.class, () -> bookingService.reject(center2, app.getId(), "no"));
    }
}
