package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.ClientVisitAnalyzer;
import com.alexxlpz.crm_cbelleza.booking.OpeningHoursPolicy;
import com.alexxlpz.crm_cbelleza.dto.AppointmentCalendarDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Consultas de citas (solo lectura). Los cambios de estado están en {@link BookingService}. */
@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private static final List<AppointmentStatus> VISIT_STATUSES =
            List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);

    private final AppointmentRepository appointmentRepository;
    private final ClientVisitAnalyzer visitAnalyzer;
    private final AppointmentCalendarMapper calendarMapper;
    private final OpeningHoursPolicy openingHours;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              ClientVisitAnalyzer visitAnalyzer,
                              AppointmentCalendarMapper calendarMapper,
                              OpeningHoursPolicy openingHours,
                              Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.visitAnalyzer = visitAnalyzer;
        this.calendarMapper = calendarMapper;
        this.openingHours = openingHours;
        this.clock = clock;
    }

    public List<Appointment> getClientAppointments(Long clientId) {
        return appointmentRepository.findByClientIdOrderByDateTimeDesc(clientId);
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
        return getCenterAppointments(centerId).stream()
                .map(calendarMapper::toDto)
                .toList();
    }

    public List<Appointment> getClientAppointmentsInCenter(Long centerId, Long clientId) {
        return appointmentRepository.findByCenterIdAndClientIdOrderByDateTimeDesc(centerId, clientId);
    }

    public boolean hasVisitedCenter(Long centerId, Long clientId) {
        return appointmentRepository.existsByCenterIdAndClientIdAndStatusIn(centerId, clientId, VISIT_STATUSES);
    }

    /** Franjas ocupadas (inicio de cada bloque) por citas confirmadas futuras. Usado por el selector de hora. */
    public List<String> getOccupiedSlots(Long centerId) {
        int slot = openingHours.slotMinutes();
        return appointmentRepository.findByCenterIdAndStatusInAndDateTimeAfterOrderByDateTimeAsc(
                        centerId, List.of(AppointmentStatus.CONFIRMED), LocalDateTime.now(clock)).stream()
                .flatMap(app -> {
                    int blocks = (int) Math.ceil(app.getTreatment().getDuration() / (double) slot);
                    return IntStream.range(0, blocks)
                            .mapToObj(i -> app.getDateTime().plusMinutes((long) i * slot).toString());
                })
                .toList();
    }
}
