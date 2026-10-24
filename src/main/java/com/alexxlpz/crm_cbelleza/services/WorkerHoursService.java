package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.BookingProperties;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours.Period;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.entities.WorkerHoursPeriod;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.OpeningHoursForm;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.repositories.WorkerHoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Horario de cada profesional. Sin horario propio trabaja siempre que el centro abre; con él, solo se le dan citas
 * dentro de sus tramos (que siempre caen dentro del horario del centro). Lo cambia el dueño en «Mi Centro».
 */
@Service
@Transactional
public class WorkerHoursService {

    private final WorkerHoursRepository periods;
    private final UserRepository users;
    private final OpeningHoursService openingHours;
    private final BookingProperties properties;

    public WorkerHoursService(WorkerHoursRepository periods, UserRepository users, OpeningHoursService openingHours,
                              BookingProperties properties) {
        this.periods = periods;
        this.users = users;
        this.openingHours = openingHours;
        this.properties = properties;
    }

    /** Horario propio del profesional; vacío si trabaja todo el horario del centro. */
    @Transactional(readOnly = true)
    public Optional<OpeningHours> forWorker(Long workerId) {
        List<WorkerHoursPeriod> saved = periods.findByWorkerIdOrderByDayOfWeekAscStartsAtAsc(workerId);
        return saved.isEmpty() ? Optional.empty() : Optional.of(toHours(saved));
    }

    /** Horario propio de cada profesional del centro que lo tiene (los demás no aparecen), en una consulta. */
    @Transactional(readOnly = true)
    public Map<Long, OpeningHours> forCenter(Long centerId) {
        Map<Long, List<WorkerHoursPeriod>> byWorker = periods.findByCenterId(centerId).stream()
                .collect(Collectors.groupingBy(p -> p.getWorker().getId()));
        Map<Long, OpeningHours> hours = new HashMap<>();
        byWorker.forEach((workerId, saved) -> hours.put(workerId, toHours(saved)));
        return hours;
    }

    /** El horario que de verdad tiene el profesional: el suyo o, si no tiene, el del centro. */
    @Transactional(readOnly = true)
    public OpeningHours effective(Long centerId, Long workerId) {
        return forWorker(workerId).orElseGet(() -> openingHours.forCenter(centerId));
    }

    /**
     * Guarda el horario del profesional {@code workerId} del centro. {@code custom = false}: vuelve a trabajar todo
     * el horario del centro. Con horario propio, los tramos tienen que caer dentro del horario del centro.
     */
    public void replace(Long centerId, Long workerId, boolean custom, OpeningHoursForm form) {
        User worker = users.findByIdAndCenterIdAndRole(workerId, centerId, Role.WORKER)
                .orElseThrow(() -> new ResourceNotFoundException("Ese profesional no es de tu centro."));
        if (!custom) {
            periods.deleteByWorkerId(workerId);
            return;
        }
        if (form == null || form.isEmpty()) {
            throw new BusinessRuleException("Marca al menos un día en el que trabaje " + worker.getName() + ".");
        }
        OpeningHours hours = openingHours.parse(form);
        OpeningHours center = openingHours.forCenter(centerId);
        for (Period period : hours.periods()) {
            if (!center.covers(period.day(), period.opens(), period.closes())) {
                String day = OpeningHours.dayName(period.day()).toLowerCase(Locale.ROOT);
                String centerHours = center.hoursOf(period.day());
                throw new BusinessRuleException("El " + day + " " + worker.getName() + " no puede trabajar de " + period.label()
                        + (centerHours == null ? ": el centro cierra ese día." : ": el centro abre de " + centerHours + "."));
            }
        }
        periods.deleteByWorkerId(workerId);
        periods.flush();
        periods.saveAll(hours.periods().stream()
                .map(p -> WorkerHoursPeriod.builder()
                        .worker(worker)
                        .dayOfWeek(p.day().getValue())
                        .startsAt(p.opens())
                        .endsAt(p.closes())
                        .build())
                .toList());
    }

    private OpeningHours toHours(List<WorkerHoursPeriod> saved) {
        return new OpeningHours(properties.slotMinutes(), saved.stream()
                .map(p -> new Period(DayOfWeek.of(p.getDayOfWeek()), p.getStartsAt(), p.getEndsAt()))
                .toList());
    }
}
