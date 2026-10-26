package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.AgendaSnapshot;
import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Carga la agenda de un centro ({@link AgendaSnapshot}) y calcula los huecos llenos para el selector del cliente. */
@Service
public class AgendaAvailabilityService {

    /** Días hacia delante que ve el selector de hora del cliente (booking-picker.js enseña 6 meses). */
    static final int BOOKING_HORIZON_DAYS = 215;

    /** Días hacia delante en los que se busca el «Primer hueco» del buscador. */
    static final int FIRST_SLOT_DAYS = 14;

    private final AppointmentRepository appointments;
    private final AgendaBlockRepository blocks;
    private final UserRepository users;
    private final CenterRepository centers;
    private final TreatmentRepository treatments;
    private final OpeningHoursService openingHours;
    private final WorkerHoursService workerHours;
    private final Clock clock;

    public AgendaAvailabilityService(AppointmentRepository appointments,
                                     AgendaBlockRepository blocks,
                                     UserRepository users,
                                     CenterRepository centers,
                                     TreatmentRepository treatments,
                                     OpeningHoursService openingHours,
                                     WorkerHoursService workerHours,
                                     Clock clock) {
        this.workerHours = workerHours;
        this.appointments = appointments;
        this.blocks = blocks;
        this.users = users;
        this.centers = centers;
        this.treatments = treatments;
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
                blocks.findByCenterIdAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(center.getId(), since, to),
                workerHours.forCenter(center.getId()));
    }

    /**
     * Huecos del selector de hora del cliente en los que ya no cabe ninguna cita (ni cabina ni profesional libre)
     * y días enteros cerrados por un bloqueo del centro.
     */
    @Transactional(readOnly = true)
    public Availability availability(Long centerId) {
        return availability(centerId, null, null, null, null);
    }

    /**
     * Igual, para un tratamiento y profesional concretos: un hueco también está lleno si no queda libre el profesional
     * elegido ({@code workerId}) o, con «Cualquiera», nadie de los que hacen el tratamiento. {@code changingAppointmentId}:
     * cita del cliente {@code clientId} que se está cambiando de hora, cuyo hueco cuenta como libre.
     */
    @Transactional(readOnly = true)
    public Availability availability(Long centerId, Long treatmentId, Long workerId, Long changingAppointmentId, Long clientId) {
        Center center = centers.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
        Long ignored = changingAppointmentId != null && clientId != null
                && appointments.findByIdAndClientId(changingAppointmentId, clientId)
                        .filter(app -> app.getCenter().getId().equals(centerId)).isPresent()
                ? changingAppointmentId : null;
        List<User> team = users.findByCenterIdAndRole(centerId, Role.WORKER);
        Treatment treatment = treatmentId != null ? treatments.findByIdAndCenterId(treatmentId, centerId).orElse(null) : null;
        List<User> candidates = workerId != null
                ? team.stream().filter(w -> w.getId().equals(workerId) && (treatment == null || treatment.isDoneBy(workerId))).toList()
                : treatment != null ? treatment.doneBy(team) : team;
        boolean restricted = candidates.size() < team.size();
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
                    AgendaSnapshot.Request request = new AgendaSnapshot.Request(start, slot, null, ignored, true);
                    if (!agenda.fits(request) || (restricted && agenda.freeWorker(request, candidates).isEmpty())) {
                        full.add(start.toString());
                    }
                }
            }
        }
        return new Availability(hours, full, closedDates);
    }

    /**
     * Primer hueco libre del centro de aquí a {@value #FIRST_SLOT_DAYS} días, del largo de un tramo del selector
     * (con cabina y alguien del equipo libre): «Primer hueco: hoy a las 17:30» en el buscador. Vacío si no hay.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> firstFreeSlot(Center center, OpeningHours hours) {
        LocalDateTime now = CenterClock.now(clock, center);
        LocalDate today = now.toLocalDate();
        LocalDate last = today.plusDays(FIRST_SLOT_DAYS);
        AgendaSnapshot agenda = snapshot(center, today.atStartOfDay(), last.plusDays(1).atStartOfDay());
        int slot = hours.slotMinutes();
        for (LocalDate day = today; !day.isAfter(last); day = day.plusDays(1)) {
            for (OpeningHours.Period period : hours.periods(day.getDayOfWeek())) {
                LocalDateTime close = day.atTime(period.closes());
                for (LocalDateTime start = day.atTime(period.opens()); start.isBefore(close); start = start.plusMinutes(slot)) {
                    if (!start.isBefore(now) && agenda.fits(new AgendaSnapshot.Request(start, slot, null, null, true))) {
                        return Optional.of(start);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public record Availability(OpeningHours hours, List<String> occupied, List<String> closedDates) {
    }
}
