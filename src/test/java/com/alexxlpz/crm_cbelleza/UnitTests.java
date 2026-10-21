package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.booking.BookingProperties;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.booking.ClientVisitAnalyzer;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.PasswordPolicy;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests unitarios (sin Spring) de las piezas de lógica pura. */
class UnitTests {

    private static final LocalDateTime MONDAY_10 = LocalDateTime.of(2030, 1, 7, 10, 0); // lunes

    @Test
    void openingHoursAcceptOnlySlotsInsideSchedule() {
        OpeningHours hours = new BookingProperties().defaultHours();
        assertTrue(hours.fits(MONDAY_10, 60));
        assertTrue(hours.fits(MONDAY_10.withHour(19), 60), "19:00 + 60 min termina justo al cierre");
        assertFalse(hours.fits(MONDAY_10.withHour(19).withMinute(30), 60), "terminaría después de las 20:00");
        assertFalse(hours.fits(MONDAY_10.withMinute(15), 30), "no empieza en una franja de 30 min");
        assertFalse(hours.fits(MONDAY_10.plusDays(5), 30), "sábado");
        assertFalse(hours.fits(MONDAY_10.withHour(8), 30), "antes de abrir");
        assertEquals(List.of("Lunes a viernes: 09:00 - 20:00"), hours.describe());
    }

    @Test
    void splitShiftOnlyAcceptsAppointmentsInsideOnePeriod() {
        OpeningHours hours = new OpeningHours(30, List.of(
                new OpeningHours.Period(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(14, 0)),
                new OpeningHours.Period(DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(20, 0)),
                new OpeningHours.Period(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(14, 0)),
                new OpeningHours.Period(DayOfWeek.TUESDAY, LocalTime.of(16, 0), LocalTime.of(20, 0)),
                new OpeningHours.Period(DayOfWeek.SATURDAY, LocalTime.of(10, 0), LocalTime.of(14, 0))));
        assertTrue(hours.fits(MONDAY_10.withHour(13), 60), "termina justo al cerrar la mañana");
        assertFalse(hours.fits(MONDAY_10.withHour(13).withMinute(30), 60), "pisaría el cierre del mediodía");
        assertFalse(hours.fits(MONDAY_10.withHour(14), 30), "a mediodía está cerrado");
        assertTrue(hours.fits(MONDAY_10.withHour(16), 30));
        assertFalse(hours.fits(MONDAY_10.plusDays(2), 30), "el miércoles no abre");
        assertTrue(hours.fits(MONDAY_10.plusDays(5), 30), "el sábado abre por la mañana");
        assertEquals(List.of("Lunes y martes: 09:00 - 14:00 y 16:00 - 20:00", "Sábado: 10:00 - 14:00"), hours.describe());
        assertEquals(9, hours.earliestHour());
        assertEquals(20, hours.latestHour());
    }

    @Test
    void firstVisitIsDetectedPerClient() {
        User ana = User.builder().id(1L).name("Ana").build();
        Appointment visit1 = appointment(10L, ana, MONDAY_10, AppointmentStatus.COMPLETED);
        Appointment visit2 = appointment(11L, ana, MONDAY_10.plusDays(7), AppointmentStatus.CONFIRMED);
        Appointment rejectedBefore = appointment(12L, User.builder().id(2L).build(), MONDAY_10.minusDays(3), AppointmentStatus.REJECTED);

        Map<Long, Boolean> flags = new ClientVisitAnalyzer().firstVisitFlags(List.of(visit2, visit1, rejectedBefore));

        assertTrue(flags.get(10L), "la primera cita es primera visita");
        assertFalse(flags.get(11L), "la segunda ya no lo es");
        assertTrue(flags.get(12L), "una cita rechazada no cuenta como visita previa");
    }

    @Test
    void passwordPolicy() {
        PasswordPolicy policy = new PasswordPolicy();
        assertDoesNotThrow(() -> policy.validateRequired("secreto", "secreto"));
        assertDoesNotThrow(() -> policy.validateOptional("", null), "vacía = no se cambia");
        assertThrows(BusinessRuleException.class, () -> policy.validateRequired("123", "123"));
        assertThrows(BusinessRuleException.class, () -> policy.validateRequired("secreto", "otro"));
        assertThrows(BusinessRuleException.class, () -> policy.validateRequired(null, null));
    }

    private static Appointment appointment(Long id, User client, LocalDateTime when, AppointmentStatus status) {
        return Appointment.builder().id(id).client(client).dateTime(when).status(status).build();
    }
}
