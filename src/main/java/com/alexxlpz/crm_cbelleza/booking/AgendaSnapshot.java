package com.alexxlpz.crm_cbelleza.booking;

import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentProposal;
import com.alexxlpz.crm_cbelleza.entities.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Foto de la agenda de un centro en un rango de fechas: citas confirmadas, horas propuestas que siguen
 * abiertas, bloqueos y trabajadores. Responde si cabe una cita más en un intervalo:
 * <ul>
 *     <li>el centro no puede estar cerrado (bloqueo de todo el centro);</li>
 *     <li>el profesional elegido no puede tener otra cita ni un bloqueo a esa hora;</li>
 *     <li>a la vez no puede haber más citas que cabinas ni que profesionales disponibles.</li>
 * </ul>
 * Las horas propuestas a un cliente reservan hueco frente a otras solicitudes y propuestas,
 * pero el trabajador puede ocuparlas al crear o mover citas (la propuesta pierde esa opción).
 */
public final class AgendaSnapshot {

    private final int cabins;
    private final List<User> workers;
    private final List<Appointment> confirmed;
    private final List<Hold> holds;
    private final List<AgendaBlock> blocks;

    /** Hora propuesta a otra cita, ocupada mientras el cliente decide. */
    public record Hold(Long appointmentId, Long workerId, LocalDateTime start, LocalDateTime end) {
    }

    /** Qué se comprueba: intervalo, profesional (opcional), cita que se está moviendo y si cuentan las horas propuestas. */
    public record Request(LocalDateTime start, int durationMinutes, Long workerId, Long ignoredAppointmentId, boolean countHolds) {
        public LocalDateTime end() {
            return start.plusMinutes(durationMinutes);
        }
    }

    public AgendaSnapshot(int cabins, List<User> workers, List<Appointment> confirmed,
                          List<Appointment> proposed, List<AgendaBlock> blocks) {
        this.cabins = Math.max(1, cabins);
        this.workers = workers;
        this.confirmed = confirmed;
        this.blocks = blocks;
        this.holds = new ArrayList<>();
        for (Appointment app : proposed) {
            int duration = app.getTreatment().getDuration();
            Long workerId = app.getWorker() != null ? app.getWorker().getId() : null;
            for (AppointmentProposal proposal : app.getProposals()) {
                holds.add(new Hold(app.getId(), workerId, proposal.getDateTime(), proposal.getDateTime().plusMinutes(duration)));
            }
        }
    }

    /** Motivo por el que no cabe la cita, o vacío si cabe. */
    public Optional<String> problem(Request request) {
        LocalDateTime start = request.start();
        LocalDateTime end = request.end();

        if (blocks.stream().anyMatch(b -> b.isWholeCenter() && b.overlaps(start, end))) {
            return Optional.of("El centro está cerrado en ese horario.");
        }
        if (request.workerId() != null) {
            String name = workerName(request.workerId());
            if (blocks.stream().anyMatch(b -> !b.isWholeCenter() && b.getWorker().getId().equals(request.workerId()) && b.overlaps(start, end))) {
                return Optional.of(name + " no está disponible en ese horario.");
            }
            boolean busy = confirmed.stream()
                    .filter(app -> !app.getId().equals(request.ignoredAppointmentId()))
                    .anyMatch(app -> app.getWorker() != null && app.getWorker().getId().equals(request.workerId()) && app.overlaps(start, end));
            if (busy) {
                return Optional.of(name + " ya tiene otra cita a esa hora.");
            }
        }

        List<LocalDateTime[]> occupying = new ArrayList<>();
        confirmed.stream()
                .filter(app -> !app.getId().equals(request.ignoredAppointmentId()))
                .filter(app -> app.overlaps(start, end))
                .forEach(app -> occupying.add(new LocalDateTime[]{app.getDateTime(), app.getEndDateTime()}));
        if (request.countHolds()) {
            holds.stream()
                    .filter(hold -> !hold.appointmentId().equals(request.ignoredAppointmentId()))
                    .filter(hold -> start.isBefore(hold.end()) && end.isAfter(hold.start()))
                    .forEach(hold -> occupying.add(new LocalDateTime[]{hold.start(), hold.end()}));
        }

        long freeWorkers = workers.stream()
                .filter(w -> blocks.stream().noneMatch(b -> !b.isWholeCenter() && b.getWorker().getId().equals(w.getId()) && b.overlaps(start, end)))
                .count();
        int busyAtOnce = maxConcurrent(occupying, start, end);
        if (busyAtOnce + 1 > cabins) {
            return Optional.of(cabins == 1
                    ? "Esa hora ya está ocupada por otra cita."
                    : "No quedan cabinas libres a esa hora.");
        }
        if (busyAtOnce + 1 > freeWorkers) {
            return Optional.of("No hay ningún profesional libre a esa hora.");
        }
        return Optional.empty();
    }

    public boolean fits(Request request) {
        return problem(request).isEmpty();
    }

    /**
     * Profesional que puede atender la cita sin chocar con sus citas, bloqueos u horas que ha propuesto a otros
     * clientes. Si hay varios, el que menos citas tiene ese día, para repartir las reservas automáticas.
     * Vacío si no queda ninguno libre.
     */
    public Optional<User> freeWorker(Request request) {
        return freeWorker(request, workers);
    }

    /** Como {@link #freeWorker(Request)}, pero solo entre {@code candidates} (quienes hacen el tratamiento o el elegido). */
    public Optional<User> freeWorker(Request request, List<User> candidates) {
        LocalDateTime start = request.start();
        LocalDateTime end = request.end();
        return candidates.stream()
                .filter(w -> fits(new Request(start, request.durationMinutes(), w.getId(), request.ignoredAppointmentId(), request.countHolds())))
                .filter(w -> holds.stream().noneMatch(hold -> w.getId().equals(hold.workerId())
                        && !hold.appointmentId().equals(request.ignoredAppointmentId())
                        && start.isBefore(hold.end()) && end.isAfter(hold.start())))
                .min(Comparator.comparingLong(w -> confirmed.stream()
                        .filter(app -> app.getWorker() != null && app.getWorker().getId().equals(w.getId()))
                        .filter(app -> app.getDateTime().toLocalDate().equals(start.toLocalDate()))
                        .count()));
    }

    /** true si el intervalo cae dentro de un cierre de todo el centro. */
    public boolean centerClosed(LocalDateTime start, LocalDateTime end) {
        return blocks.stream().anyMatch(b -> b.isWholeCenter() && !b.getStartsAt().isAfter(start) && !b.getEndsAt().isBefore(end));
    }

    /** Máximo de intervalos que coinciden en algún momento dentro de [start, end). */
    private static int maxConcurrent(List<LocalDateTime[]> intervals, LocalDateTime start, LocalDateTime end) {
        int max = 0;
        List<LocalDateTime> points = new ArrayList<>();
        points.add(start);
        intervals.forEach(i -> {
            if (i[0].isAfter(start) && i[0].isBefore(end)) points.add(i[0]);
        });
        for (LocalDateTime point : points) {
            int count = (int) intervals.stream().filter(i -> !i[0].isAfter(point) && i[1].isAfter(point)).count();
            max = Math.max(max, count);
        }
        return max;
    }

    private String workerName(Long workerId) {
        Map<Long, User> byId = workers.stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        User worker = byId.get(workerId);
        return worker != null ? Objects.requireNonNullElse(worker.getName(), "Ese profesional") : "Ese profesional";
    }
}
