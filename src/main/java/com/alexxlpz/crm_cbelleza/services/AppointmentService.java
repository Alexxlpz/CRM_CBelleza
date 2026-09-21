package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final CenterRepository centerRepository;
    private final TreatmentRepository treatmentRepository;
    private final UserRepository userRepository;

    public AppointmentService(AppointmentRepository appointmentRepository, CenterRepository centerRepository, TreatmentRepository treatmentRepository, UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.centerRepository = centerRepository;
        this.treatmentRepository = treatmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void createBooking(Long centerId, Long treatmentId, LocalDateTime dateTime, Long clientUserId, String guestName, String guestPhone) {
        if (dateTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No se puede reservar una fecha pasada");
        }
        Center center = centerRepository.findById(centerId).orElseThrow(() -> new IllegalArgumentException("Invalid center ID"));
        Treatment treatment = treatmentRepository.findById(treatmentId).orElseThrow(() -> new IllegalArgumentException("Invalid treatment ID"));
        if (!isWithinOpeningHours(dateTime, treatment.getDuration())) {
            throw new IllegalArgumentException("El servicio no cabe completo dentro del horario del centro");
        }
        LocalDateTime end = dateTime.plusMinutes(treatment.getDuration());
        boolean occupied = appointmentRepository.findByCenterIdAndStatusIn(
                        centerId, Arrays.asList(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED))
                .stream()
                .anyMatch(appointment -> dateTime.isBefore(appointment.getDateTime().plusMinutes(appointment.getTreatment().getDuration()))
                        && end.isAfter(appointment.getDateTime()));
        if (occupied) {
            throw new IllegalArgumentException("Esa hora ya está ocupada");
        }

        User client = null;
        if (clientUserId != null) {
            client = userRepository.findById(clientUserId).orElse(null);
        }

        Appointment appointment = Appointment.builder()
                .dateTime(dateTime)
                .treatment(treatment)
                .center(center)
                .status(AppointmentStatus.PENDING)
                .build();

        if (client != null) {
            appointment.setClient(client);
        } else {
            appointment.setGuestName(guestName);
            appointment.setGuestPhone(guestPhone);
        }

        appointmentRepository.save(appointment);
    }

    public List<Appointment> getBookableAppointmentsByCenter(Long centerId) {
        return appointmentRepository.findByCenterIdAndStatusInAndDateTimeAfterOrderByDateTimeAsc(
                centerId,
                Arrays.asList(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                LocalDateTime.now());
    }

    private boolean isWithinOpeningHours(LocalDateTime dateTime, int durationMinutes) {
        DayOfWeek day = dateTime.getDayOfWeek();
        int minutes = dateTime.getHour() * 60 + dateTime.getMinute();
        return day != DayOfWeek.SATURDAY
                && day != DayOfWeek.SUNDAY
                && dateTime.getSecond() == 0
                && dateTime.getNano() == 0
                && minutes >= 9 * 60
                && minutes + durationMinutes <= 20 * 60
                && dateTime.getMinute() % 30 == 0;
    }

    public List<Appointment> getClientAppointments(Long clientUserId, String guestPhone) {
        if (clientUserId != null) {
            return appointmentRepository.findByClientIdOrderByDateTimeDesc(clientUserId);
        } else if (guestPhone != null && !guestPhone.trim().isEmpty()) {
            return appointmentRepository.findByGuestPhoneOrderByDateTimeDesc(guestPhone);
        }
        return List.of();
    }
    
    public List<Appointment> getAppointmentsByCenter(Long centerId) {
        return appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId);
    }

    public void approveAppointment(Long id, String message, Long workerId) {
        Appointment app = appointmentRepository.findById(id).orElse(null);
        if (app != null) {
            app.setStatus(AppointmentStatus.CONFIRMED);
            app.setWorkerMessage(message);
            if (app.getWorker() == null && workerId != null) {
                app.setWorker(userRepository.findById(workerId).orElse(null));
            }
            appointmentRepository.save(app);
        }
    }

    public void rejectAppointment(Long id, String message) {
        Appointment app = appointmentRepository.findById(id).orElse(null);
        if (app != null) {
            app.setStatus(AppointmentStatus.REJECTED);
            app.setWorkerMessage(message);
            appointmentRepository.save(app);
        }
    }
}
