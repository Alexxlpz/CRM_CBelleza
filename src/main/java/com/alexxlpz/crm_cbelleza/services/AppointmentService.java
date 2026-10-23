package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.ClientVisitAnalyzer;
import com.alexxlpz.crm_cbelleza.dto.AppointmentCalendarDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              ClientVisitAnalyzer visitAnalyzer,
                              AppointmentCalendarMapper calendarMapper,
                              TreatmentSummaryService summaryService,
                              Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.visitAnalyzer = visitAnalyzer;
        this.calendarMapper = calendarMapper;
        this.summaryService = summaryService;
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
        LocalDateTime now = LocalDateTime.now(clock);
        List<Appointment> ordered = new ArrayList<>(appointments.stream()
                .filter(app -> !app.getDateTime().isBefore(now))
                .sorted(Comparator.comparing(Appointment::getDateTime))
                .toList());
        appointments.stream()
                .filter(app -> app.getDateTime().isBefore(now))
                .forEach(ordered::add);
        return ordered;
    }

    /**
     * «Mis reservas» en dos bloques: las próximas (solicitudes, propuestas y citas confirmadas que aún no han
     * empezado), de la más cercana a la más lejana, y el historial con todo lo demás, de la más reciente a la más antigua.
     */
    public ClientAppointments getClientAppointmentsSplit(Long clientId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Appointment> upcoming = new ArrayList<>();
        List<Appointment> history = new ArrayList<>();
        for (Appointment app : getClientAppointments(clientId)) {
            AppointmentStatus status = app.getStatus();
            boolean pending = status.isOpen() || status == AppointmentStatus.CONFIRMED;
            (pending && app.getDateTime().isAfter(now) ? upcoming : history).add(app);
        }
        return new ClientAppointments(upcoming, history);
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
        return calendarMapper.toDto(appointment, summaryService.centerNow(centerId), alerts, noShows);
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
        List<Appointment> appointments = getCenterAppointments(centerId);
        Map<Long, Long> noShows = appointments.stream()
                .filter(app -> app.getClient() != null && app.getStatus() == AppointmentStatus.NO_SHOW)
                .collect(Collectors.groupingBy(app -> app.getClient().getId(), Collectors.counting()));
        return appointments.stream()
                .map(app -> app.getClient() == null
                        ? calendarMapper.toDto(app, now, List.of(), 0)
                        : calendarMapper.toDto(app, now, alerts.getOrDefault(app.getClient().getId(), List.of()),
                                noShows.getOrDefault(app.getClient().getId(), 0L).intValue()))
                .toList();
    }

    public List<Appointment> getClientAppointmentsInCenter(Long centerId, Long clientId) {
        return appointmentRepository.findByCenterIdAndClientIdOrderByDateTimeDesc(centerId, clientId);
    }

    public boolean hasVisitedCenter(Long centerId, Long clientId) {
        return appointmentRepository.existsByCenterIdAndClientIdAndStatusIn(centerId, clientId, VISIT_STATUSES);
    }
}
