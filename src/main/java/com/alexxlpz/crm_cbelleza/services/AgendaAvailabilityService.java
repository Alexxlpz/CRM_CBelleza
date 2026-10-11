package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.AgendaSnapshot;
import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.booking.OpeningHoursPolicy;
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
    private final OpeningHoursPolicy openingHours;
    private final Clock clock;

    public AgendaAvailabilityService(AppointmentRepository appointments,
                                     AgendaBlockRepository blocks,
                                     UserRepository users,
                                     CenterRepository centers,
                                     OpeningHoursPolicy openingHours,
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
        int slot = openingHours.slotMinutes();

        List<String> full = new ArrayList<>();
        List<String> closedDates = new ArrayList<>();
        for (LocalDate day = today; !day.isAfter(last); day = day.plusDays(1)) {
            if (!openingHours.workingDays().contains(day.getDayOfWeek())) {
                continue;
            }
            LocalDateTime open = day.atTime(openingHours.openHour(), 0);
            LocalDateTime close = day.atTime(openingHours.closeHour(), 0);
            if (agenda.centerClosed(open, close)) {
                closedDates.add(day.toString());
                continue;
            }
            for (LocalDateTime start = open; start.isBefore(close); start = start.plusMinutes(slot)) {
                if (!agenda.fits(new AgendaSnapshot.Request(start, slot, null, null, true))) {
                    full.add(start.toString());
                }
            }
        }
        return new Availability(full, closedDates);
    }

    public record Availability(List<String> occupied, List<String> closedDates) {
    }
}
