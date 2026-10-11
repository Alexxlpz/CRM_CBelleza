package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Bloqueos de agenda: vacaciones o descansos de un trabajador y cierres de todo el centro.
 * Mientras dura un bloqueo no se pueden poner citas a esa persona (o en el centro). No se puede bloquear
 * un periodo que ya tiene citas confirmadas: primero hay que moverlas o anularlas.
 */
@Service
public class AgendaBlockService {

    private final AgendaBlockRepository blocks;
    private final AppointmentRepository appointments;
    private final CenterRepository centers;
    private final UserRepository users;
    private final Clock clock;

    public AgendaBlockService(AgendaBlockRepository blocks,
                              AppointmentRepository appointments,
                              CenterRepository centers,
                              UserRepository users,
                              Clock clock) {
        this.blocks = blocks;
        this.appointments = appointments;
        this.centers = centers;
        this.users = users;
        this.clock = clock;
    }

    /** Bloqueos que todavía no han terminado (los que pinta la agenda). */
    @Transactional(readOnly = true)
    public List<AgendaBlock> upcoming(Long centerId) {
        Center center = center(centerId);
        return blocks.findByCenterIdAndEndsAtAfterOrderByStartsAtAsc(centerId, CenterClock.now(clock, center).minusDays(31));
    }

    /**
     * Crea un bloqueo. {@code wholeCenter} = cierre del centro; si no, es de {@code workerId}
     * (por defecto, quien lo crea).
     */
    @Transactional
    public AgendaBlock create(Long centerId, Long creatorId, Long workerId, boolean wholeCenter,
                              String startsAt, String endsAt, String reason) {
        Center center = centers.findByIdForUpdate(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
        LocalDateTime start = parse(startsAt);
        LocalDateTime end = parse(endsAt);
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("El final del bloqueo tiene que ser posterior al inicio.");
        }
        if (!end.isAfter(CenterClock.now(clock, center))) {
            throw new BusinessRuleException("No se puede bloquear un periodo que ya ha pasado.");
        }
        if (start.plusDays(366).isBefore(end)) {
            throw new BusinessRuleException("Un bloqueo no puede durar más de un año.");
        }
        User worker = wholeCenter ? null : workerOfCenter(centerId, workerId != null ? workerId : creatorId);

        long clashes = appointments.findByCenterIdAndStatusAndDateTimeBetween(centerId, AppointmentStatus.CONFIRMED, start.minusDays(1), end)
                .stream()
                .filter(app -> app.overlaps(start, end))
                .filter(app -> worker == null || (app.getWorker() != null && app.getWorker().getId().equals(worker.getId())))
                .count();
        if (clashes > 0) {
            throw new BusinessRuleException("Hay " + clashes + (clashes == 1 ? " cita confirmada" : " citas confirmadas")
                    + " en ese periodo. Muévelas o anúlalas antes de bloquearlo.");
        }

        return blocks.save(AgendaBlock.builder()
                .center(center)
                .worker(worker)
                .startsAt(start)
                .endsAt(end)
                .reason(FormText.trimToNull(reason))
                .createdBy(users.getReferenceById(creatorId))
                .build());
    }

    @Transactional
    public void delete(Long centerId, Long blockId) {
        AgendaBlock block = blocks.findByIdAndCenterId(blockId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ese bloqueo no existe en tu centro."));
        blocks.delete(block);
    }

    private User workerOfCenter(Long centerId, Long workerId) {
        return users.findByIdAndCenterIdAndRole(workerId, centerId, Role.WORKER)
                .orElseThrow(() -> new ResourceNotFoundException("Ese profesional no es de tu centro."));
    }

    private Center center(Long centerId) {
        return centers.findById(centerId).orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
    }

    private static LocalDateTime parse(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessRuleException("Indica el inicio y el final del bloqueo.");
        }
    }
}
