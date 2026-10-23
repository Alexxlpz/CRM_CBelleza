package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.AgendaSnapshot;
import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.AppointmentProposal;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.NewClientForm;
import com.alexxlpz.crm_cbelleza.forms.WorkerAppointmentForm;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Todo lo que cambia una cita: solicitud del cliente, respuesta del centro, citas creadas y movidas por el
 * trabajador, propuestas de otras horas y anulaciones.
 * <p>
 * Las operaciones que ocupan agenda bloquean la fila del centro para que dos peticiones simultáneas no
 * puedan llenar el mismo hueco. Si cabe una cita lo decide {@link AgendaSnapshot}: cabinas del centro,
 * profesionales libres, bloqueos de agenda y horas propuestas a otros clientes.
 * Cada cambio queda anotado en la cita ({@code lastChange}) y publica un {@link AppointmentChangedEvent}.
 */
@Service
public class BookingService {

    /** Máximo de horas que se pueden proponer a la vez. */
    public static final int MAX_PROPOSALS = 3;

    private final AppointmentRepository appointmentRepository;
    private final CenterRepository centerRepository;
    private final TreatmentRepository treatmentRepository;
    private final UserRepository userRepository;
    private final ClientCardService clientCardService;
    private final LoyaltyService loyaltyService;
    private final AgendaAvailabilityService availability;
    private final OpeningHoursService openingHours;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int proposalHours;

    public BookingService(AppointmentRepository appointmentRepository,
                          CenterRepository centerRepository,
                          TreatmentRepository treatmentRepository,
                          UserRepository userRepository,
                          ClientCardService clientCardService,
                          LoyaltyService loyaltyService,
                          AgendaAvailabilityService availability,
                          OpeningHoursService openingHours,
                          ApplicationEventPublisher events,
                          Clock clock,
                          @Value("${crm.booking.proposal-hours:48}") int proposalHours) {
        this.appointmentRepository = appointmentRepository;
        this.centerRepository = centerRepository;
        this.treatmentRepository = treatmentRepository;
        this.userRepository = userRepository;
        this.clientCardService = clientCardService;
        this.loyaltyService = loyaltyService;
        this.availability = availability;
        this.openingHours = openingHours;
        this.events = events;
        this.clock = clock;
        this.proposalHours = proposalHours;
    }

    /* =====================================================================
       Cliente
       ===================================================================== */

    /**
     * El cliente solicita una cita; queda PENDING hasta que el centro la apruebe. Si el centro acepta las citas
     * automáticamente, nace CONFIRMED con el profesional libre que menos citas tiene ese día (si por lo que sea no
     * queda ninguno libre, se queda pendiente como cualquier solicitud).
     */
    @Transactional
    public Appointment requestBooking(Long clientId, Long centerId, Long treatmentId, LocalDateTime start) {
        return requestBooking(clientId, centerId, treatmentId, null, start);
    }

    /**
     * Igual, con el profesional que ha elegido el cliente ({@code workerId}; nulo = «Cualquiera»). La cita queda a su
     * nombre aunque esté pendiente, y el centro la aprueba con él salvo que la cambie.
     */
    @Transactional
    public Appointment requestBooking(Long clientId, Long centerId, Long treatmentId, Long workerId, LocalDateTime start) {
        Center center = lockCenter(centerId);
        if (!center.isPublished()) {
            throw new ResourceNotFoundException("Centro no encontrado.");
        }
        LocalDateTime now = CenterClock.now(clock, center);
        if (start.isBefore(now)) {
            throw new BusinessRuleException("No se puede reservar una fecha pasada.");
        }
        Treatment treatment = treatmentRepository.findByIdAndCenterId(treatmentId, centerId)
                .orElseThrow(() -> new BusinessRuleException("El tratamiento seleccionado no pertenece a este centro."));
        if (!openingHours.forCenter(centerId).fits(start, treatment.getDuration())) {
            throw new BusinessRuleException("El servicio no cabe completo dentro del horario del centro.");
        }
        AgendaSnapshot.Request slot = new AgendaSnapshot.Request(start, treatment.getDuration(), null, null, true);
        AgendaSnapshot agenda = availability.snapshot(center, start.toLocalDate().atStartOfDay(), start.plusDays(1));
        ensureFits(agenda, slot);
        User free = freeCandidate(agenda, slot, treatment, workerId);

        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new BusinessRuleException("Cliente no encontrado en el sistema."));
        boolean confirmed = center.isAutoAcceptBookings() && free != null;
        User worker = confirmed || workerId != null ? free : null;
        AppointmentChange change = confirmed ? AppointmentChange.BOOKED : AppointmentChange.REQUESTED;

        Appointment appointment = Appointment.builder()
                .dateTime(start)
                .treatment(treatment)
                .center(center)
                .client(client)
                .worker(worker)
                .price(treatment.priceOn(now.toLocalDate()))
                .status(confirmed ? AppointmentStatus.CONFIRMED : AppointmentStatus.PENDING)
                .workerMessage("")
                .createdAt(LocalDateTime.now(clock))
                .build();
        appointment.recordChange(change, LocalDateTime.now(clock));
        Appointment saved = save(appointment, change);
        if (confirmed) {
            releaseTakenProposals(center, saved);
        }
        return saved;
    }

    /** El cliente elige una de las horas que le propuso el centro: la cita queda confirmada a esa hora. */
    @Transactional
    public void acceptProposal(Long clientId, Long appointmentId, Long proposalId) {
        Appointment appointment = findForClient(clientId, appointmentId);
        Center center = lockCenter(appointment.getCenter().getId());
        LocalDateTime now = CenterClock.now(clock, center);
        if (appointment.getStatus() != AppointmentStatus.PROPOSED || isExpired(appointment, now)) {
            throw new BusinessRuleException("Esta propuesta ya no está disponible.");
        }
        AppointmentProposal chosen = appointment.getProposals().stream()
                .filter(p -> p.getId().equals(proposalId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Esa hora no forma parte de la propuesta."));
        if (chosen.getDateTime().isBefore(now)) {
            throw new BusinessRuleException("Esa hora ya ha pasado. Elige otra o pide una cita nueva.");
        }
        Long workerId = appointment.getWorker() != null ? appointment.getWorker().getId() : null;
        String problem = availability.snapshot(center, chosen.getDateTime(), chosen.getDateTime().plusDays(1))
                .problem(new AgendaSnapshot.Request(chosen.getDateTime(), duration(appointment), workerId, appointment.getId(), true))
                .orElse(null);
        if (problem != null) {
            throw new BusinessRuleException("Esa hora ya no está disponible. Elige otra de la propuesta.");
        }
        appointment.setDateTime(chosen.getDateTime());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        clearProposal(appointment);
        appointment.recordChange(AppointmentChange.PROPOSAL_ACCEPTED, LocalDateTime.now(clock));
        save(appointment, AppointmentChange.PROPOSAL_ACCEPTED);
    }

    /** Ninguna de las horas propuestas le viene bien al cliente: la cita queda anulada. */
    @Transactional
    public void declineProposal(Long clientId, Long appointmentId) {
        Appointment appointment = findForClient(clientId, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.PROPOSED) {
            throw new BusinessRuleException("Esta propuesta ya no está disponible.");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        clearProposal(appointment);
        appointment.recordChange(AppointmentChange.PROPOSAL_DECLINED, LocalDateTime.now(clock));
        save(appointment, AppointmentChange.PROPOSAL_DECLINED);
    }

    /** El cliente anula una solicitud, una propuesta o una cita confirmada que aún no ha empezado. */
    @Transactional
    public void cancelByClient(Long clientId, Long appointmentId) {
        Appointment appointment = findForClient(clientId, appointmentId);
        LocalDateTime now = CenterClock.now(clock, appointment.getCenter());
        AppointmentStatus status = appointment.getStatus();
        if (!(status.isOpen() || status == AppointmentStatus.CONFIRMED) || !appointment.getDateTime().isAfter(now)) {
            throw new BusinessRuleException("Esta cita ya no se puede anular.");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        clearProposal(appointment);
        appointment.recordChange(AppointmentChange.CANCELLED_BY_CLIENT, LocalDateTime.now(clock));
        save(appointment, AppointmentChange.CANCELLED_BY_CLIENT);
    }

    /**
     * El cliente cambia la hora (y, si quiere, el profesional) de su solicitud o cita confirmada desde «Mis reservas»,
     * hasta {@link Center#getClientRescheduleHours()} horas antes. Una cita confirmada sigue confirmada si a la nueva
     * hora queda libre el profesional elegido o, con «Cualquiera», alguien que haga el tratamiento; una solicitud
     * sigue pendiente. El centro recibe un aviso.
     */
    @Transactional
    public Appointment rescheduleByClient(Long clientId, Long appointmentId, Long workerId, LocalDateTime start) {
        Appointment appointment = findForClient(clientId, appointmentId);
        Center center = lockCenter(appointment.getCenter().getId());
        LocalDateTime now = CenterClock.now(clock, center);
        ensureClientCanReschedule(appointment, center, now);
        if (!start.isAfter(now)) {
            throw new BusinessRuleException("Elige una hora que no haya pasado.");
        }
        Treatment treatment = appointment.getTreatment();
        if (!openingHours.forCenter(center.getId()).fits(start, treatment.getDuration())) {
            throw new BusinessRuleException("El servicio no cabe completo dentro del horario del centro.");
        }
        AgendaSnapshot.Request slot = new AgendaSnapshot.Request(start, treatment.getDuration(), null, appointment.getId(), true);
        AgendaSnapshot agenda = availability.snapshot(center, start.toLocalDate().atStartOfDay(), start.plusDays(1));
        ensureFits(agenda, slot);
        boolean confirmed = appointment.getStatus() == AppointmentStatus.CONFIRMED;
        User free = confirmed && workerId == null ? keepWorkerIfFree(agenda, slot, appointment)
                : freeCandidate(agenda, slot, treatment, workerId);
        if (confirmed && free == null) {
            throw new SlotUnavailableException("No hay ningún profesional libre a esa hora para este servicio.");
        }
        User worker = confirmed || workerId != null ? free : null;

        boolean timeChanged = !start.equals(appointment.getDateTime());
        boolean workerChanged = !Objects.equals(worker != null ? worker.getId() : null,
                appointment.getWorker() != null ? appointment.getWorker().getId() : null);
        if (!timeChanged && !workerChanged) {
            return appointment;
        }
        if (timeChanged) {
            appointment.setPreviousDateTime(appointment.getDateTime());
            appointment.setDateTime(start);
            appointment.setReminderSentAt(null);
        }
        appointment.setWorker(worker);
        appointment.recordChange(AppointmentChange.RESCHEDULED_BY_CLIENT, LocalDateTime.now(clock));
        Appointment saved = save(appointment, AppointmentChange.RESCHEDULED_BY_CLIENT);
        if (confirmed) {
            releaseTakenProposals(center, saved);
        }
        return saved;
    }

    /** Con «Cualquiera» en una cita confirmada se queda quien la tenía si sigue libre a la nueva hora. */
    private User keepWorkerIfFree(AgendaSnapshot agenda, AgendaSnapshot.Request slot, Appointment appointment) {
        User current = appointment.getWorker();
        if (current != null && appointment.getTreatment().isDoneBy(current.getId())) {
            try {
                return freeCandidate(agenda, slot, appointment.getTreatment(), current.getId());
            } catch (BusinessRuleException e) {
                // Ya no está libre (o no está en el centro): cualquiera que haga el tratamiento
            }
        }
        return freeCandidate(agenda, slot, appointment.getTreatment(), null);
    }

    /** Por qué el cliente no puede cambiar él mismo la hora de esta cita (estado o plazo del centro); vacío si puede. */
    public Optional<String> clientRescheduleProblem(Appointment appointment) {
        try {
            ensureClientCanReschedule(appointment, appointment.getCenter(), CenterClock.now(clock, appointment.getCenter()));
            return Optional.empty();
        } catch (BusinessRuleException e) {
            return Optional.of(e.getMessage());
        }
    }

    private static void ensureClientCanReschedule(Appointment appointment, Center center, LocalDateTime now) {
        AppointmentStatus status = appointment.getStatus();
        if ((status != AppointmentStatus.PENDING && status != AppointmentStatus.CONFIRMED) || !appointment.getDateTime().isAfter(now)) {
            throw new BusinessRuleException("Esta cita ya no se puede cambiar.");
        }
        int hours = center.getClientRescheduleHours();
        if (hours <= 0) {
            throw new BusinessRuleException(center.getName() + " no deja cambiar la hora desde la web. Llama al centro para cambiarla.");
        }
        if (appointment.getDateTime().isBefore(now.plusHours(hours))) {
            throw new BusinessRuleException("Solo se puede cambiar hasta " + hours + " h antes de la cita. Llama al centro si necesitas cambiarla.");
        }
    }

    /* =====================================================================
       Centro: responder solicitudes
       ===================================================================== */

    /**
     * El centro confirma una solicitud (o una propuesta, a la hora que pidió el cliente). La atiende
     * {@code workerId} o, si no se indica, quien la aprueba. Falla si no queda cabina o el profesional está ocupado.
     */
    @Transactional
    public void approve(Long centerId, Long appointmentId, Long approverId, Long workerId, String message) {
        Center center = lockCenter(centerId);
        Appointment appointment = findInCenter(centerId, appointmentId);
        if (!appointment.getStatus().isOpen()) {
            throw new BusinessRuleException("Esta cita ya no está pendiente.");
        }
        User worker = workerOfCenter(centerId, workerId != null ? workerId
                : appointment.getWorker() != null ? appointment.getWorker().getId() : approverId);
        AgendaSnapshot agenda = availability.snapshot(center, appointment.getDateTime(), appointment.getDateTime().plusDays(1));
        ensureFits(agenda, new AgendaSnapshot.Request(appointment.getDateTime(), duration(appointment), worker.getId(), appointment.getId(), false));

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setWorker(worker);
        appointment.setWorkerMessage(message);
        clearProposal(appointment);
        appointment.recordChange(AppointmentChange.CONFIRMED, LocalDateTime.now(clock));
        save(appointment, AppointmentChange.CONFIRMED);
        releaseTakenProposals(center, appointment);
    }

    /** El centro rechaza una solicitud o retira por completo una propuesta. */
    @Transactional
    public void reject(Long centerId, Long appointmentId, String message) {
        Appointment appointment = findInCenter(centerId, appointmentId);
        if (!appointment.getStatus().isOpen()) {
            throw new BusinessRuleException("Esta cita ya no está pendiente.");
        }
        appointment.setStatus(AppointmentStatus.REJECTED);
        appointment.setWorkerMessage(message);
        clearProposal(appointment);
        appointment.recordChange(AppointmentChange.REJECTED, LocalDateTime.now(clock));
        save(appointment, AppointmentChange.REJECTED);
    }

    /**
     * Propone al cliente de 1 a {@value #MAX_PROPOSALS} horas alternativas. Esas horas quedan reservadas
     * para él (no las puede coger otro cliente) hasta que responda o caduque la propuesta.
     */
    @Transactional
    public void propose(Long centerId, Long appointmentId, Long proposerId, List<String> dateTimes, String message) {
        Center center = lockCenter(centerId);
        Appointment appointment = findInCenter(centerId, appointmentId);
        if (!appointment.getStatus().isOpen()) {
            throw new BusinessRuleException("Solo se pueden proponer horas para solicitudes pendientes.");
        }
        List<LocalDateTime> times = (dateTimes == null ? List.<String>of() : dateTimes).stream()
                .filter(value -> !FormText.isBlank(value))
                .map(BookingService::parse)
                .distinct()
                .sorted()
                .toList();
        if (times.isEmpty() || times.size() > MAX_PROPOSALS) {
            throw new BusinessRuleException("Propón entre 1 y " + MAX_PROPOSALS + " horas.");
        }
        LocalDateTime now = CenterClock.now(clock, center);
        User worker = workerOfCenter(centerId, appointment.getWorker() != null ? appointment.getWorker().getId() : proposerId);
        int duration = duration(appointment);
        AgendaSnapshot agenda = availability.snapshot(center, times.getFirst(), times.getLast().plusDays(1));
        OpeningHours hours = openingHours.forCenter(centerId);
        for (LocalDateTime time : times) {
            if (!time.isAfter(now)) {
                throw new BusinessRuleException("Las horas propuestas tienen que ser futuras.");
            }
            if (!hours.fits(time, duration)) {
                throw new BusinessRuleException("La hora " + label(time) + " queda fuera del horario de reservas.");
            }
            String problem = agenda.problem(new AgendaSnapshot.Request(time, duration, worker.getId(), appointment.getId(), true)).orElse(null);
            if (problem != null) {
                throw new BusinessRuleException(label(time) + ": " + problem);
            }
        }
        LocalDateTime expires = earliest(now.plusHours(proposalHours), times.getFirst().minusHours(2));
        if (!expires.isAfter(now)) {
            throw new BusinessRuleException("Propón horas con más margen: el cliente necesita tiempo para responder.");
        }

        appointment.getProposals().clear();
        times.forEach(time -> appointment.getProposals().add(
                AppointmentProposal.builder().appointment(appointment).dateTime(time).build()));
        appointment.setWorker(worker);
        appointment.setStatus(AppointmentStatus.PROPOSED);
        appointment.setProposalExpiresAt(expires);
        appointment.setWorkerMessage(message);
        appointment.recordChange(AppointmentChange.PROPOSED, LocalDateTime.now(clock));
        save(appointment, AppointmentChange.PROPOSED);
    }

    /** El centro retira su propuesta: la solicitud vuelve a estar pendiente a la hora que pidió el cliente. */
    @Transactional
    public void withdrawProposal(Long centerId, Long appointmentId) {
        Appointment appointment = findInCenter(centerId, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.PROPOSED) {
            throw new BusinessRuleException("Esta cita no tiene ninguna propuesta abierta.");
        }
        appointment.setStatus(AppointmentStatus.PENDING);
        clearProposal(appointment);
        appointment.recordChange(AppointmentChange.PROPOSAL_WITHDRAWN, LocalDateTime.now(clock));
        save(appointment, AppointmentChange.PROPOSAL_WITHDRAWN);
    }

    /* =====================================================================
       Centro: organizar la agenda
       ===================================================================== */

    /** El trabajador apunta una cita (acordada por teléfono, en el mostrador...): nace confirmada. */
    @Transactional
    public Appointment createByWorker(Long centerId, Long creatorId, WorkerAppointmentForm form) {
        Center center = lockCenter(centerId);
        LocalDateTime now = CenterClock.now(clock, center);
        if (form.treatmentId() == null) {
            throw new BusinessRuleException("Elige un tratamiento.");
        }
        Treatment treatment = treatmentRepository.findByIdAndCenterId(form.treatmentId(), centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ese tratamiento no es de tu centro."));
        LocalDateTime start = parse(form.dateTime());
        User worker = workerOfCenter(centerId, form.workerId() != null ? form.workerId() : creatorId);
        checkSchedule(center, now, start, treatment.getDuration(), worker.getId(), null, form.outsideHoursAllowed());
        User client = clientFor(centerId, creatorId, form);

        Appointment appointment = Appointment.builder()
                .dateTime(start)
                .treatment(treatment)
                .center(center)
                .client(client)
                .worker(worker)
                .price(treatment.priceOn(now.toLocalDate()))
                .status(AppointmentStatus.CONFIRMED)
                .workerMessage(FormText.trimToNull(form.message()))
                .createdAt(LocalDateTime.now(clock))
                .build();
        appointment.recordChange(AppointmentChange.CREATED_BY_CENTER, LocalDateTime.now(clock));
        Appointment saved = save(appointment, AppointmentChange.CREATED_BY_CENTER);
        releaseTakenProposals(center, saved);
        return saved;
    }

    /**
     * Cambia la hora (y, si se indica, el profesional) de una cita confirmada que aún no ha empezado.
     * Sigue confirmada; el cliente ve la hora anterior y recibe un aviso.
     */
    @Transactional
    public Appointment reschedule(Long centerId, Long appointmentId, String dateTime, Long workerId, boolean allowOutsideHours) {
        Center center = lockCenter(centerId);
        Appointment appointment = findInCenter(centerId, appointmentId);
        LocalDateTime now = CenterClock.now(clock, center);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED || !appointment.getDateTime().isAfter(now)) {
            throw new BusinessRuleException("Solo se pueden mover citas confirmadas que aún no han empezado.");
        }
        LocalDateTime start = parse(dateTime);
        User worker = workerId != null ? workerOfCenter(centerId, workerId) : appointment.getWorker();
        Long workerToCheck = worker != null ? worker.getId() : null;
        checkSchedule(center, now, start, duration(appointment), workerToCheck, appointment.getId(), allowOutsideHours);

        boolean timeChanged = !start.equals(appointment.getDateTime());
        boolean workerChanged = !Objects.equals(workerToCheck, appointment.getWorker() != null ? appointment.getWorker().getId() : null);
        if (!timeChanged && !workerChanged) {
            return appointment;
        }
        if (timeChanged) {
            appointment.setPreviousDateTime(appointment.getDateTime());
            appointment.setDateTime(start);
            appointment.setReminderSentAt(null);
        }
        appointment.setWorker(worker);
        appointment.recordChange(AppointmentChange.RESCHEDULED, LocalDateTime.now(clock));
        Appointment saved = save(appointment, AppointmentChange.RESCHEDULED);
        releaseTakenProposals(center, saved);
        return saved;
    }

    /** El centro anula una cita confirmada que aún no ha empezado (el cliente recibe un aviso). */
    @Transactional
    public void cancelByCenter(Long centerId, Long appointmentId, String message) {
        Appointment appointment = findInCenter(centerId, appointmentId);
        LocalDateTime now = CenterClock.now(clock, appointment.getCenter());
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED || !appointment.getDateTime().isAfter(now)) {
            throw new BusinessRuleException("Solo se pueden anular citas confirmadas que aún no han empezado.");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        if (!FormText.isBlank(message)) {
            appointment.setWorkerMessage(message.trim());
        }
        appointment.recordChange(AppointmentChange.CANCELLED_BY_CENTER, LocalDateTime.now(clock));
        save(appointment, AppointmentChange.CANCELLED_BY_CENTER);
    }

    /**
     * El cliente no vino: la cita confirmada que ya ha empezado pasa a «No se presentó». Pierde los puntos que
     * hubiera sumado y no avisa a nadie (el cliente lo ve en su historial).
     */
    @Transactional
    public void markNoShow(Long centerId, Long appointmentId) {
        Appointment appointment = findInCenter(centerId, appointmentId);
        LocalDateTime now = CenterClock.now(clock, appointment.getCenter());
        AppointmentStatus status = appointment.getStatus();
        if ((status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.COMPLETED)
                || appointment.getDateTime().isAfter(now)) {
            throw new BusinessRuleException("Solo se puede marcar «No se presentó» en una cita confirmada que ya ha empezado.");
        }
        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointmentRepository.save(appointment);
        loyaltyService.revokeAppointment(appointment.getId());
    }

    /** Se marcó por error: la cita vuelve a contar como hecha (y sus puntos se apuntan de nuevo al consultarlos). */
    @Transactional
    public void undoNoShow(Long centerId, Long appointmentId) {
        Appointment appointment = findInCenter(centerId, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.NO_SHOW) {
            throw new BusinessRuleException("Esta cita no está marcada como «No se presentó».");
        }
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appointment);
    }

    /* =====================================================================
       Tareas periódicas
       ===================================================================== */

    /** Anula las propuestas que han caducado sin respuesta del cliente. Devuelve cuántas. */
    @Transactional
    public int expireProposals() {
        List<Appointment> expired = appointmentRepository.findByStatusAndProposalExpiresAtBefore(
                AppointmentStatus.PROPOSED, LocalDateTime.now(clock).plusDays(1)).stream()
                .filter(app -> isExpired(app, CenterClock.now(clock, app.getCenter())))
                .toList();
        for (Appointment appointment : expired) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            clearProposal(appointment);
            appointment.recordChange(AppointmentChange.PROPOSAL_EXPIRED, LocalDateTime.now(clock));
            save(appointment, AppointmentChange.PROPOSAL_EXPIRED);
        }
        return expired.size();
    }

    /* =====================================================================
       Ayudas
       ===================================================================== */

    /**
     * Comprueba día, horario y hueco de una cita que coloca el trabajador. Fuera del horario de reservas
     * solo se permite si lo confirma ({@code allowOutsideHours}); las horas propuestas a otros clientes no
     * frenan al trabajador (la propuesta pierde esa opción después).
     */
    private void checkSchedule(Center center, LocalDateTime now, LocalDateTime start, int duration,
                               Long workerId, Long ignoredAppointmentId, boolean allowOutsideHours) {
        if (!start.isAfter(now)) {
            throw new BusinessRuleException("No se pueden poner citas en una hora que ya ha pasado.");
        }
        if (!allowOutsideHours && !openingHours.forCenter(center.getId()).fits(start, duration)) {
            throw new OutsideOpeningHoursException();
        }
        AgendaSnapshot agenda = availability.snapshot(center, start, start.plusDays(1));
        ensureFits(agenda, new AgendaSnapshot.Request(start, duration, workerId, ignoredAppointmentId, false));
    }

    /**
     * Tras ocupar un hueco, las propuestas abiertas de otros clientes que ya no caben pierden esa hora;
     * si no les queda ninguna, la solicitud vuelve a estar pendiente.
     */
    private void releaseTakenProposals(Center center, Appointment taken) {
        List<Appointment> proposed = appointmentRepository.findByCenterIdAndStatus(center.getId(), AppointmentStatus.PROPOSED);
        if (proposed.isEmpty()) {
            return;
        }
        AgendaSnapshot agenda = availability.snapshot(center, taken.getDateTime().minusDays(1), taken.getDateTime().plusDays(1));
        for (Appointment other : proposed) {
            if (other.getId().equals(taken.getId())) {
                continue;
            }
            Long workerId = other.getWorker() != null ? other.getWorker().getId() : null;
            boolean removed = other.getProposals().removeIf(p -> p.getDateTime().toLocalDate().equals(taken.getDateTime().toLocalDate())
                    && !agenda.fits(new AgendaSnapshot.Request(p.getDateTime(), duration(other), workerId, other.getId(), false)));
            if (removed && other.getProposals().isEmpty()) {
                other.setStatus(AppointmentStatus.PENDING);
                other.setProposalExpiresAt(null);
                other.recordChange(AppointmentChange.PROPOSAL_WITHDRAWN, LocalDateTime.now(clock));
                save(other, AppointmentChange.PROPOSAL_WITHDRAWN);
            } else if (removed) {
                appointmentRepository.save(other);
            }
        }
    }

    private User clientFor(Long centerId, Long creatorId, WorkerAppointmentForm form) {
        if (form.clientId() != null) {
            return clientCardService.requireClientOfCenter(centerId, form.clientId());
        }
        if (FormText.isBlank(form.newClientName()) || FormText.isBlank(form.newClientPhone())) {
            throw new BusinessRuleException("Elige un cliente de tu cartera o escribe el nombre y el teléfono del cliente nuevo.");
        }
        var created = clientCardService.createClientManually(centerId,
                new NewClientForm(form.newClientName(), form.newClientPhone(), form.newClientEmail(), null), creatorId);
        return userRepository.findById(created.clientId()).orElseThrow();
    }

    /**
     * Quién puede atender la cita del cliente: el profesional que ha elegido o, con «Cualquiera», el más libre de
     * los que hacen el tratamiento. Si el cliente ha elegido a alguien o el tratamiento no lo hace todo el equipo,
     * tiene que quedar uno libre; si no, puede ser nulo (la solicitud espera a que el centro la organice).
     */
    private User freeCandidate(AgendaSnapshot agenda, AgendaSnapshot.Request slot, Treatment treatment, Long workerId) {
        Long centerId = treatment.getCenter().getId();
        List<User> team = userRepository.findByCenterIdAndRole(centerId, Role.WORKER);
        List<User> candidates;
        if (workerId != null) {
            User chosen = team.stream().filter(w -> w.getId().equals(workerId)).findFirst()
                    .orElseThrow(() -> new BusinessRuleException("Ese profesional ya no está en el centro. Elige otro."));
            if (!treatment.doneBy(team).contains(chosen)) {
                throw new BusinessRuleException(chosen.getName() + " no hace " + treatment.getName() + ". Elige otro profesional.");
            }
            candidates = List.of(chosen);
        } else {
            candidates = treatment.doneBy(team);
        }
        User free = agenda.freeWorker(slot, candidates).orElse(null);
        if (free == null && (workerId != null || candidates.size() < team.size())) {
            throw new SlotUnavailableException(workerId != null
                    ? candidates.getFirst().getName() + " no está libre a esa hora. Elige otra hora u otro profesional."
                    : "No hay ningún profesional libre a esa hora para este servicio.");
        }
        return free;
    }

    private User workerOfCenter(Long centerId, Long workerId) {
        if (workerId == null) {
            throw new BusinessRuleException("Elige el profesional que atenderá la cita.");
        }
        return userRepository.findByIdAndCenterIdAndRole(workerId, centerId, Role.WORKER)
                .orElseThrow(() -> new ResourceNotFoundException("Ese profesional no es de tu centro."));
    }

    private static void ensureFits(AgendaSnapshot agenda, AgendaSnapshot.Request request) {
        agenda.problem(request).ifPresent(problem -> {
            throw new SlotUnavailableException(problem);
        });
    }

    /** Los cambios se anotan con la hora del servidor (la misma con la que se compara la campana de avisos). */
    private Appointment save(Appointment appointment, AppointmentChange change) {
        Appointment saved = appointmentRepository.save(appointment);
        events.publishEvent(new AppointmentChangedEvent(saved.getId(), change));
        return saved;
    }

    private static void clearProposal(Appointment appointment) {
        appointment.getProposals().clear();
        appointment.setProposalExpiresAt(null);
    }

    private static boolean isExpired(Appointment appointment, LocalDateTime now) {
        return appointment.getProposalExpiresAt() != null && !appointment.getProposalExpiresAt().isAfter(now);
    }

    private static int duration(Appointment appointment) {
        return appointment.getTreatment().getDuration();
    }

    private static LocalDateTime earliest(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static String label(LocalDateTime time) {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
    }

    static LocalDateTime parse(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessRuleException("Selecciona un día y una hora válidos.");
        }
    }

    private Center lockCenter(Long centerId) {
        return centerRepository.findByIdForUpdate(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
    }

    private Appointment findInCenter(Long centerId, Long appointmentId) {
        return appointmentRepository.findByIdAndCenterId(appointmentId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("La cita no existe en tu centro."));
    }

    private Appointment findForClient(Long clientId, Long appointmentId) {
        return appointmentRepository.findByIdAndClientId(appointmentId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("La cita no existe."));
    }

    /** El hueco está ocupado (409 en las llamadas fetch de la agenda). */
    public static class SlotUnavailableException extends BusinessRuleException {
        public SlotUnavailableException(String message) {
            super(message);
        }
    }

    /** La hora queda fuera del horario de reservas: la agenda pregunta si se quiere guardar igualmente. */
    public static class OutsideOpeningHoursException extends BusinessRuleException {
        public OutsideOpeningHoursException() {
            super("La cita queda fuera del horario de reservas del centro.");
        }
    }
}
