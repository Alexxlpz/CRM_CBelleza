package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.booking.ClientVisitAnalyzer;
import com.alexxlpz.crm_cbelleza.dto.AppointmentCalendarDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentMedia;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Consultas de citas (solo lectura). Los cambios de estado están en {@link BookingService}. */
@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private static final List<AppointmentStatus> VISIT_STATUSES =
            List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);

    private final AppointmentRepository appointmentRepository;
    private final ClientVisitAnalyzer visitAnalyzer;
    private final AppointmentCalendarMapper calendarMapper;
    private final TreatmentSummaryService summaryService;
    private final AppointmentMediaService mediaService;
    private final ClientAllergyService allergyService;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              ClientVisitAnalyzer visitAnalyzer,
                              AppointmentCalendarMapper calendarMapper,
                              TreatmentSummaryService summaryService,
                              AppointmentMediaService mediaService,
                              ClientAllergyService allergyService,
                              Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.visitAnalyzer = visitAnalyzer;
        this.calendarMapper = calendarMapper;
        this.summaryService = summaryService;
        this.mediaService = mediaService;
        this.allergyService = allergyService;
        this.clock = clock;
    }

    /**
     * Citas del cliente para «Mis Citas»: primero las próximas, de la más cercana a la más lejana, y después
     * las pasadas, de la más reciente a la más antigua. En las que tienen propuesta abierta se cargan ya las
     * horas propuestas (las pinta la vista).
     */
    public List<Appointment> getClientAppointments(Long clientId) {
        List<Appointment> appointments = appointmentRepository.findByClientIdOrderByDateTimeDesc(clientId);
        appointments.stream()
                .filter(app -> app.getStatus() == AppointmentStatus.PROPOSED)
                .forEach(app -> app.getProposals().size());
        // Cada cita, con la hora local de su centro (el cliente puede tener citas en centros de otra zona)
        List<Appointment> ordered = new ArrayList<>(appointments.stream()
                .filter(app -> !hasStarted(app))
                .sorted(Comparator.comparing(Appointment::getDateTime))
                .toList());
        appointments.stream()
                .filter(this::hasStarted)
                .forEach(ordered::add);
        return ordered;
    }

    /**
     * «Mis reservas» en dos bloques: las próximas (solicitudes, propuestas y citas confirmadas que aún no han
     * empezado), de la más cercana a la más lejana, y el historial con todo lo demás, de la más reciente a la más antigua.
     */
    public ClientAppointments getClientAppointmentsSplit(Long clientId) {
        List<Appointment> upcoming = new ArrayList<>();
        List<Appointment> history = new ArrayList<>();
        for (Appointment app : getClientAppointments(clientId)) {
            AppointmentStatus status = app.getStatus();
            boolean pending = status.isOpen() || status == AppointmentStatus.CONFIRMED;
            (pending && app.getDateTime().isAfter(CenterClock.now(clock, app.getCenter())) ? upcoming : history).add(app);
        }
        return new ClientAppointments(upcoming, history);
    }

    /** Ya ha llegado su hora, en la hora local de su centro. */
    private boolean hasStarted(Appointment app) {
        return app.getDateTime().isBefore(CenterClock.now(clock, app.getCenter()));
    }

    /** Una cita del cliente, con tratamiento (y quién lo hace), centro y profesional. */
    public Appointment getClientAppointment(Long clientId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdAndClientId(appointmentId, clientId)
                .orElseThrow(() -> new com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException("La cita no existe."));
        appointment.getTreatment().getWorkerIds().size();
        return appointment;
    }

    public record ClientAppointments(List<Appointment> upcoming, List<Appointment> history) {
        public boolean isEmpty() {
            return upcoming.isEmpty() && history.isEmpty();
        }

        /** Las mismas citas, con la que se acaba de pedir la primera de las próximas (si está entre ellas). */
        public ClientAppointments withFirst(Long appointmentId) {
            if (appointmentId == null || upcoming.stream().noneMatch(a -> appointmentId.equals(a.getId()))) {
                return this;
            }
            List<Appointment> reordered = new ArrayList<>(upcoming.size());
            upcoming.stream().filter(a -> appointmentId.equals(a.getId())).forEach(reordered::add);
            upcoming.stream().filter(a -> !appointmentId.equals(a.getId())).forEach(reordered::add);
            return new ClientAppointments(reordered, history);
        }
    }

    /**
     * La próxima cita del cliente para la tarjeta de arriba del buscador de centros (la misma que «Tu próxima cita» de
     * «Mis reservas»), con el día dicho como se dice («Hoy», «Mañana», «vie 20 nov») y cuántas tiene por venir.
     */
    public Optional<NextAppointment> nextForClient(Long clientId) {
        List<Appointment> upcoming = getClientAppointmentsSplit(clientId).upcoming();
        if (upcoming.isEmpty()) {
            return Optional.empty();
        }
        Appointment next = upcoming.get(0);
        String day = FriendlyDates.day(next.getDateTime().toLocalDate(), CenterClock.now(clock, next.getCenter()).toLocalDate());
        return Optional.of(new NextAppointment(next, day, upcoming.size()));
    }

    /** @param dayLabel «Hoy», «Mañana» o «vie 20 nov»; {@code upcomingCount} = citas por venir (para «Mis citas (N)») */
    public record NextAppointment(Appointment appointment, String dayLabel, int upcomingCount) {
    }

    /** Fecha de cada cita como se dice («Hoy · 11:00», «Mañana, lun 16 nov · 09:00»), con el hoy de su centro. */
    public Map<Long, String> friendlyDates(List<Appointment> appointments) {
        Map<Long, String> labels = new HashMap<>();
        for (Appointment app : appointments) {
            labels.put(app.getId(), FriendlyDates.dateTime(app.getDateTime(),
                    CenterClock.now(clock, app.getCenter()).toLocalDate()));
        }
        return labels;
    }

    /** Una cita del centro en el formato de la agenda (respuesta de las acciones de la agenda). */
    public AppointmentCalendarDTO getCalendarAppointment(Long centerId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdAndCenterId(appointmentId, centerId)
                .orElseThrow(() -> new com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException("La cita no existe en tu centro."));
        Long clientId = appointment.getClient() != null ? appointment.getClient().getId() : null;
        List<Appointment> alerts = clientId == null ? List.of()
                : summaryService.alertsByClient(centerId).getOrDefault(clientId, List.of());
        int noShows = clientId == null ? 0
                : (int) appointmentRepository.countByCenterIdAndClientIdAndStatus(centerId, clientId, AppointmentStatus.NO_SHOW);
        AppointmentCalendarDTO dto = calendarMapper.toDto(appointment, summaryService.centerNow(centerId), alerts, noShows,
                mediaService.forAppointment(centerId, appointmentId));
        dto.setClientAllergies(allergyService.of(centerId, clientId));
        return dto;
    }

    public long countClientAppointments(Long clientId) {
        return appointmentRepository.countByClientId(clientId);
    }

    /** Citas del centro, marcando las que son la primera visita del cliente. */
    public List<Appointment> getCenterAppointments(Long centerId) {
        List<Appointment> appointments = appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId);
        Map<Long, Boolean> firstVisit = visitAnalyzer.firstVisitFlags(appointments);
        appointments.forEach(app -> app.setIsNewClient(firstVisit.get(app.getId())));
        return appointments;
    }

    public List<AppointmentCalendarDTO> getCalendarAppointments(Long centerId) {
        LocalDateTime now = summaryService.centerNow(centerId);
        Map<Long, List<Appointment>> alerts = summaryService.alertsByClient(centerId);
        Map<Long, List<AppointmentMedia>> media = mediaService.byAppointment(centerId);
        Map<Long, String> allergies = allergyService.byClient(centerId);
        List<Appointment> appointments = getCenterAppointments(centerId);
        Map<Long, Long> noShows = appointments.stream()
                .filter(app -> app.getClient() != null && app.getStatus() == AppointmentStatus.NO_SHOW)
                .collect(Collectors.groupingBy(app -> app.getClient().getId(), Collectors.counting()));
        return appointments.stream()
                .map(app -> {
                    if (app.getClient() == null) {
                        return calendarMapper.toDto(app, now, List.of(), 0, media.getOrDefault(app.getId(), List.of()));
                    }
                    Long clientId = app.getClient().getId();
                    AppointmentCalendarDTO dto = calendarMapper.toDto(app, now, alerts.getOrDefault(clientId, List.of()),
                            noShows.getOrDefault(clientId, 0L).intValue(), media.getOrDefault(app.getId(), List.of()));
                    dto.setClientAllergies(allergies.get(clientId));
                    return dto;
                })
                .toList();
    }

    public List<Appointment> getClientAppointmentsInCenter(Long centerId, Long clientId) {
        return appointmentRepository.findByCenterIdAndClientIdOrderByDateTimeDesc(centerId, clientId);
    }

    public boolean hasVisitedCenter(Long centerId, Long clientId) {
        return appointmentRepository.existsByCenterIdAndClientIdAndStatusIn(centerId, clientId, VISIT_STATUSES);
    }
}
