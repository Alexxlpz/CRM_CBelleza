package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.CenterData;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.Occupancy;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.Window;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ocupación de la agenda y CSV del análisis, sin base de datos. Un miércoles de 10 a 12 con dos cabinas:
 * Carlos trabaja todo el horario y Lucía solo de 10 a 11.
 */
class AnalyticsOccupancyTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 21);

    private final User carlos = User.builder().id(1L).name("Carlos").build();
    private final User lucia = User.builder().id(2L).name("Lucía").build();
    private final OpeningHours centerHours = OpeningHours.uniform(30, Set.of(DayOfWeek.WEDNESDAY), LocalTime.of(10, 0), LocalTime.of(12, 0));
    private final OpeningHours luciaHours = OpeningHours.uniform(30, Set.of(DayOfWeek.WEDNESDAY), LocalTime.of(10, 0), LocalTime.of(11, 0));
    private final Window day = new Window(WEDNESDAY.atStartOfDay(), WEDNESDAY.atTime(LocalTime.MAX));

    private static Appointment appointment(User worker, int hour, int minutes, AppointmentStatus status) {
        return Appointment.builder()
                .dateTime(WEDNESDAY.atTime(hour, 0))
                .treatment(Treatment.builder().duration(minutes).build())
                .worker(worker)
                .status(status)
                .build();
    }

    private CenterData data(List<Appointment> all, List<AgendaBlock> blocks) {
        return new CenterData(1L, WEDNESDAY.atTime(23, 0), all, List.of(), List.of(), Map.of(), YearMonth.from(WEDNESDAY),
                2, centerHours, Map.of(lucia.getId(), luciaHours), List.of(carlos, lucia), blocks);
    }

    @Test
    void occupancyCountsCabinsAndWhoIsWorking() {
        List<Appointment> all = List.of(
                appointment(carlos, 10, 60, AppointmentStatus.COMPLETED),
                appointment(lucia, 10, 30, AppointmentStatus.NO_SHOW),
                appointment(lucia, 11, 60, AppointmentStatus.CANCELLED));

        // De 10 a 11 caben dos citas a la vez (120 min); de 11 a 12, solo Carlos (60): 90 de 180 min con cita
        Occupancy occupancy = AnalyticsService.occupancy(data(all, List.of()), day);
        assertEquals(50, occupancy.share());
        assertEquals(50, occupancy.byWorker().get(carlos.getId()));
        assertEquals(50, occupancy.byWorker().get(lucia.getId()));
    }

    @Test
    void centerClosuresAndWorkerBlocksAreNotTimeToFill() {
        List<Appointment> all = List.of(appointment(carlos, 10, 60, AppointmentStatus.COMPLETED));
        List<AgendaBlock> blocks = List.of(
                AgendaBlock.builder().startsAt(WEDNESDAY.atTime(11, 30)).endsAt(WEDNESDAY.atTime(12, 0)).build(),
                AgendaBlock.builder().worker(lucia).startsAt(WEDNESDAY.atTime(10, 0)).endsAt(WEDNESDAY.atTime(11, 0)).build());

        // Lucía no trabaja (bloqueo) y de 11:30 a 12 cierra el centro: quedan 90 min de Carlos, 60 con cita
        Occupancy occupancy = AnalyticsService.occupancy(data(all, blocks), day);
        assertEquals(67, occupancy.share());
        assertEquals(67, occupancy.byWorker().get(carlos.getId()));
        assertNull(occupancy.byWorker().get(lucia.getId()));
    }

    @Test
    void slotsThatHaveNotEndedYetDoNotCount() {
        Window untilQuarterPastEleven = new Window(WEDNESDAY.atStartOfDay(), WEDNESDAY.atTime(11, 15));
        Occupancy occupancy = AnalyticsService.occupancy(
                data(List.of(appointment(carlos, 10, 60, AppointmentStatus.COMPLETED)), List.of()), untilQuarterPastEleven);
        // Solo las franjas de 10:00 y 10:30, con los dos: 120 min, 60 con cita
        assertEquals(50, occupancy.share());
    }

    @Test
    void noOpeningHoursMeansNoOccupancy() {
        Window sunday = new Window(WEDNESDAY.plusDays(4).atStartOfDay(), WEDNESDAY.plusDays(4).atTime(LocalTime.MAX));
        assertNull(AnalyticsService.occupancy(data(List.of(), List.of()), sunday).share());
    }

    @Test
    void csvCellsAreEscapedForExcel() {
        assertEquals("Centro Histórico", AnalyticsCsv.cell("Centro Histórico"));
        assertEquals("\"Uñas; pestañas\"", AnalyticsCsv.cell("Uñas; pestañas"));
        assertEquals("\"Dice \"\"hola\"\"\"", AnalyticsCsv.cell("Dice \"hola\""));
        assertEquals("'=HYPERLINK(1)", AnalyticsCsv.cell("=HYPERLINK(1)"));
        assertEquals("-12,50", AnalyticsCsv.cell("-12,50"));
        assertEquals("centro-historico-ultimos-12-meses", AnalyticsCsv.slug("Centro Histórico · Últimos 12 meses"));
    }
}
