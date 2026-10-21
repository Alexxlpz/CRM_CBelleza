package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.AgendaSnapshot;
import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Carga la agenda de un centro ({@link AgendaSnapshot}) y calcula los huecos llenos para el selector del cliente. */
@Service
public class AgendaAvailabilityService {

    /** Días hacia delante que ve el selector de hora del cliente (booking-picker.js enseña 6 meses). */
    static final int BOOKING_HORIZON_DAYS = 215;

    private final AppointmentRepository appointments;
    private final AgendaBlockRepository blocks;
    private final UserRepository users;
    private final CenterRepository centers;
    private final OpeningHoursService openingHours;
    private final Clock clock;

    public AgendaAvailabilityService(AppointmentRepository appointments,
                                     AgendaBlockRepository blocks,
                                     UserRepository users,
                                     CenterRepository centers,
                                     OpeningHoursService openingHours,
                                     Clock clock) {
        this.appointments = appointments;
        this.blocks = blocks;
        this.users = users;
        this.centers = centers;
        this.openingHours = openingHours;
        this.clock = clock;
    }

    /** Agenda del centro entre dos momentos (con un día de margen para citas que empiezan antes). */
    @Transactional(readOnly = true)
    public AgendaSnapshot snapshot(Center center, LocalDateTime from, LocalDateTime to) {
        LocalDateTime since = from.minusDays(1);
        return new AgendaSnapshot(
                center.getCabins() != null ? center.getCabins() : 1,
                users.findByCenterIdAndRole(center.getId(), Role.WORKER),
                appointments.findByCenterIdAndStatusAndDateTimeBetween(center.getId(), AppointmentStatus.CONFIRMED, since, to),
                appointments.findByCenterIdAndStatus(center.getId(), AppointmentStatus.PROPOSED),
                blocks.findByCenterIdAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(center.getId(), since, to));
    }

    /**
     * Huecos del selector de hora del cliente en los que ya no cabe ninguna cita (ni cabina ni profesional libre)
     * y días enteros cerrados por un bloqueo del centro.
     */
    @Transactional(readOnly = true)
    public Availability availability(Long centerId) {
        Center center = centers.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
        LocalDate today = CenterClock.now(clock, center).toLocalDate();
        LocalDate last = today.plusDays(BOOKING_HORIZON_DAYS);
        AgendaSnapshot agenda = snapshot(center, today.atStartOfDay(), last.plusDays(1).atStartOfDay());
        OpeningHours hours = openingHours.forCenter(centerId);
        int slot = hours.slotMinutes();

        List<String> full = new ArrayList<>();
        List<String> closedDates = new ArrayList<>();
        for (LocalDate day = today; !day.isAfter(last); day = day.plusDays(1)) {
            List<OpeningHours.Period> periods = hours.periods(day.getDayOfWeek());
            if (periods.isEmpty()) {
                continue;
            }
            if (agenda.centerClosed(day.atTime(periods.getFirst().opens()), day.atTime(periods.getLast().closes()))) {
                closedDates.add(day.toString());
                continue;
            }
            for (OpeningHours.Period period : periods) {
                LocalDateTime close = day.atTime(period.closes());
                for (LocalDateTime start = day.atTime(period.opens()); start.isBefore(close); start = start.plusMinutes(slot)) {
                    if (!agenda.fits(new AgendaSnapshot.Request(start, slot, null, null, true))) {
                        full.add(start.toString());
                    }
                }
            }
        }
        return new Availability(hours, full, closedDates);
    }

    public record Availability(OpeningHours hours, List<String> occupied, List<String> closedDates) {
    }
}
