package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.OpeningHoursPolicy;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cambios de estado de las citas: solicitud por el cliente y aprobación/rechazo por el centro.
 * Todas las operaciones bloquean la fila del centro para que dos peticiones simultáneas
 * no puedan ocupar la misma franja.
 */
@Service
public class BookingService {

    private final AppointmentRepository appointmentRepository;
    private final CenterRepository centerRepository;
    private final TreatmentRepository treatmentRepository;
    private final UserRepository userRepository;
    private final OpeningHoursPolicy openingHours;
    private final Clock clock;

    public BookingService(AppointmentRepository appointmentRepository,
                          CenterRepository centerRepository,
                          TreatmentRepository treatmentRepository,
                          UserRepository userRepository,
                          OpeningHoursPolicy openingHours,
                          Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.centerRepository = centerRepository;
        this.treatmentRepository = treatmentRepository;
        this.userRepository = userRepository;
        this.openingHours = openingHours;
        this.clock = clock;
    }

    /** El cliente solicita una cita; queda PENDING hasta que el centro la apruebe. */
    @Transactional
    public Appointment requestBooking(Long clientId, Long centerId, Long treatmentId, LocalDateTime start) {
        if (start.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("No se puede reservar una fecha pasada.");
        }
        Center center = lockCenter(centerId);
        Treatment treatment = treatmentRepository.findByIdAndCenterId(treatmentId, centerId)
                .orElseThrow(() -> new BusinessRuleException("El tratamiento seleccionado no pertenece a este centro."));
        if (!openingHours.fits(start, treatment.getDuration())) {
            throw new BusinessRuleException("El servicio no cabe completo dentro del horario del centro.");
        }
        ensureSlotIsFree(centerId, null, start, treatment.getDuration());

        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new BusinessRuleException("Cliente no encontrado en el sistema."));

        Appointment appointment = Appointment.builder()
                .dateTime(start)
                .treatment(treatment)
                .center(center)
                .client(client)
                .status(AppointmentStatus.PENDING)
                .workerMessage("")
                .build();
        return appointmentRepository.save(appointment);
    }

    /** El centro confirma una cita. Falla si otra cita confirmada ya ocupa esa franja. */
    @Transactional
    public void approve(Long centerId, Long appointmentId, Long workerId, String message) {
        lockCenter(centerId);
        Appointment appointment = findInCenter(centerId, appointmentId);
        ensureSlotIsFree(centerId, appointment.getId(), appointment.getDateTime(), appointment.getTreatment().getDuration());

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setWorkerMessage(message);
        if (appointment.getWorker() == null && workerId != null) {
            userRepository.findById(workerId).ifPresent(appointment::setWorker);
        }
        appointmentRepository.save(appointment);
    }

    @Transactional
    public void reject(Long centerId, Long appointmentId, String message) {
        Appointment appointment = findInCenter(centerId, appointmentId);
        appointment.setStatus(AppointmentStatus.REJECTED);
        appointment.setWorkerMessage(message);
        appointmentRepository.save(appointment);
    }

    private Center lockCenter(Long centerId) {
        return centerRepository.findByIdForUpdate(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
    }

    private Appointment findInCenter(Long centerId, Long appointmentId) {
        return appointmentRepository.findByIdAndCenterId(appointmentId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("La cita no existe en tu centro."));
    }

    private void ensureSlotIsFree(Long centerId, Long ignoredAppointmentId, LocalDateTime start, int durationMinutes) {
        LocalDateTime end = start.plusMinutes(durationMinutes);
        boolean occupied = appointmentRepository
                .findByCenterIdAndStatusIn(centerId, List.of(AppointmentStatus.CONFIRMED)).stream()
                .filter(other -> !other.getId().equals(ignoredAppointmentId))
                .anyMatch(other -> start.isBefore(other.getEndDateTime()) && end.isAfter(other.getDateTime()));
        if (occupied) {
            throw new BusinessRuleException("Esa hora ya está ocupada por otra cita confirmada.");
        }
    }
}
