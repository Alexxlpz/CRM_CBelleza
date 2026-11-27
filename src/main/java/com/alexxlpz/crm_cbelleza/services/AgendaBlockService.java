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

import java.io.Serial;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Bloqueos de agenda: vacaciones o descansos de un trabajador y cierres de todo el centro.
 * Mientras dura un bloqueo no se pueden poner citas a esa persona (o en el centro). No se puede bloquear
 * un periodo que ya tiene citas confirmadas: primero hay que moverlas o anularlas. Las solicitudes sin responder
 * y las horas propuestas que caen dentro se rechazan o se quitan al bloquear, después de que el trabajador lo confirme.
 * Cada trabajador bloquea y desbloquea su propia agenda; cerrar el centro o tocar la agenda de otra persona
 * es cosa del dueño del centro.
 */
@Service
public class AgendaBlockService {

    /** Largo máximo del motivo de un bloqueo (columna {@code reason}). */
    public static final int MAX_REASON = 255;

    private final AgendaBlockRepository blocks;
    private final AppointmentRepository appointments;
    private final CenterRepository centers;
    private final UserRepository users;
    private final CenterAccess access;
    private final BookingService booking;
    private final Clock clock;

    public AgendaBlockService(AgendaBlockRepository blocks,
                              AppointmentRepository appointments,
                              CenterRepository centers,
                              UserRepository users,
                              CenterAccess access,
                              BookingService booking,
                              Clock clock) {
        this.access = access;
        this.booking = booking;
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
     * (por defecto, quien lo crea). Si hay solicitudes sin responder u horas propuestas en ese periodo y no llega
     * {@code releaseRequests}, lanza {@link OpenRequestsException} para que la agenda pregunte; con él, se rechazan
     * (avisando al cliente) o pierden esas horas.
     */
    @Transactional
    public AgendaBlock create(Long centerId, Long creatorId, Long workerId, boolean wholeCenter,
                              String startsAt, String endsAt, String reason) {
        return create(centerId, creatorId, workerId, wholeCenter, startsAt, endsAt, reason, false);
    }

    @Transactional
    public AgendaBlock create(Long centerId, Long creatorId, Long workerId, boolean wholeCenter,
                              String startsAt, String endsAt, String reason, boolean releaseRequests) {
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
        String why = FormText.trimToNull(reason);
        if (why != null && why.length() > MAX_REASON) {
            throw new BusinessRuleException("El motivo no puede pasar de " + MAX_REASON + " caracteres.");
        }
        User worker = wholeCenter ? null : workerOfCenter(centerId, workerId != null ? workerId : creatorId);
        if ((worker == null || !worker.getId().equals(creatorId)) && !access.isOwner(centerId, creatorId)) {
            throw new BusinessRuleException(wholeCenter
                    ? "Solo el dueño del centro puede cerrarlo. Bloquea tu propia agenda o pídeselo a " + access.ownerName(centerId) + "."
                    : "Solo puedes bloquear tu propia agenda. Para la de otra persona, pídeselo a " + access.ownerName(centerId) + ".");
        }

        long clashes = appointments.findByCenterIdAndStatusAndDateTimeBetween(centerId, AppointmentStatus.CONFIRMED, start.minusDays(1), end)
                .stream()
                .filter(app -> app.overlaps(start, end))
                .filter(app -> worker == null || (app.getWorker() != null && app.getWorker().getId().equals(worker.getId())))
                .count();
        if (clashes > 0) {
            throw new BusinessRuleException("Hay " + clashes + (clashes == 1 ? " cita confirmada" : " citas confirmadas")
                    + " en ese periodo. Muévelas o anúlalas antes de bloquearlo.");
        }

        Long blockedWorkerId = worker != null ? worker.getId() : null;
        BookingService.BlockImpact impact = booking.blockImpact(centerId, blockedWorkerId, start, end);
        if (!impact.isEmpty() && !releaseRequests) {
            throw new OpenRequestsException(impactMessage(impact));
        }

        AgendaBlock saved = blocks.save(AgendaBlock.builder()
                .center(center)
                .worker(worker)
                .startsAt(start)
                .endsAt(end)
                .reason(why)
                .createdBy(users.getReferenceById(creatorId))
                .build());
        if (!impact.isEmpty()) {
            booking.releaseForBlock(centerId, blockedWorkerId, start, end, worker == null
                    ? "El centro estará cerrado a esa hora. Pide otra hora cuando quieras."
                    : worker.getName() + " no estará disponible a esa hora. Pide otra hora o elige a otro profesional.");
        }
        return saved;
    }

    /** «Hay 2 solicitudes sin responder en ese periodo. Si lo bloqueas, se rechazarán y avisaremos a los clientes…». */
    private static String impactMessage(BookingService.BlockImpact impact) {
        StringBuilder text = new StringBuilder();
        int rejected = impact.rejected().size();
        int trimmed = impact.trimmed().size();
        if (rejected > 0) {
            text.append(rejected == 1
                    ? "Hay 1 solicitud sin responder en ese periodo. Si lo bloqueas, se rechazará y avisaremos al cliente para que pida otra hora."
                    : "Hay " + rejected + " solicitudes sin responder en ese periodo. Si lo bloqueas, se rechazarán y avisaremos a los clientes para que pidan otra hora.");
        }
        if (trimmed > 0) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(trimmed == 1
                    ? "A 1 propuesta se le quitarán las horas que caen dentro; el cliente podrá elegir entre las demás."
                    : "A " + trimmed + " propuestas se les quitarán las horas que caen dentro; los clientes podrán elegir entre las demás.");
        }
        return text.toString();
    }

    /** Hay solicitudes u horas propuestas en el periodo: la agenda pregunta antes de bloquear. */
    public static class OpenRequestsException extends BusinessRuleException {
        @Serial
        private static final long serialVersionUID = 1L;

        public OpenRequestsException(String message) {
            super(message);
        }
    }

    /** Quita un bloqueo: el suyo cualquier trabajador; un cierre del centro o el de otra persona, solo el dueño. */
    @Transactional
    public void delete(Long centerId, Long actorId, Long blockId) {
        AgendaBlock block = blocks.findByIdAndCenterId(blockId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ese bloqueo no existe en tu centro."));
        boolean own = block.getWorker() != null && block.getWorker().getId().equals(actorId);
        if (!own && !access.isOwner(centerId, actorId)) {
            throw new BusinessRuleException("Solo puedes quitar tus propios bloqueos. Este pídeselo a " + access.ownerName(centerId) + ".");
        }
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
