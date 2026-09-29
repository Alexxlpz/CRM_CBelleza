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
import java.util.ArrayList;
import com.alexxlpz.crm_cbelleza.dto.AppointmentCalendarDTO;

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
                        centerId, List.of(AppointmentStatus.CONFIRMED))
                .stream()
                .anyMatch(appointment -> dateTime.isBefore(appointment.getDateTime().plusMinutes(appointment.getTreatment().getDuration()))
                        && end.isAfter(appointment.getDateTime()));
        if (occupied) {
            throw new IllegalArgumentException("Esa hora ya está ocupada");
        }

        if (clientUserId == null) {
            throw new IllegalArgumentException("Es necesario disponer de una cuenta de cliente registrada para reservar una cita");
        }
        User client = userRepository.findById(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado en el sistema"));

        Appointment appointment = Appointment.builder()
                .dateTime(dateTime)
                .treatment(treatment)
                .center(center)
                .client(client)
                .status(AppointmentStatus.PENDING)
                .workerMessage("")
                .build();

        appointmentRepository.save(appointment);
    }

    public List<Appointment> getBookableAppointmentsByCenter(Long centerId) {
        return appointmentRepository.findByCenterIdAndStatusInAndDateTimeAfterOrderByDateTimeAsc(
                centerId,
                List.of(AppointmentStatus.CONFIRMED),
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
        appointmentRepository.updatePastConfirmedToCompleted(LocalDateTime.now());
        if (clientUserId != null) {
            return appointmentRepository.findByClientIdOrderByDateTimeDesc(clientUserId);
        } else if (guestPhone != null && !guestPhone.trim().isEmpty()) {
            return appointmentRepository.findByGuestPhoneOrderByDateTimeDesc(guestPhone);
        }
        return List.of();
    }
    
    public List<Appointment> getAppointmentsByCenter(Long centerId) {
        appointmentRepository.updatePastConfirmedToCompleted(LocalDateTime.now());
        List<Appointment> list = appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId);
        for (Appointment app : list) {
            app.setIsNewClient(isFirstVisitForCenter(app));
        }
        return list;
    }

    public List<AppointmentCalendarDTO> getCalendarAppointmentsByCenter(Long centerId) {
        appointmentRepository.updatePastConfirmedToCompleted(LocalDateTime.now());
        List<Appointment> list = appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId);
        List<AppointmentCalendarDTO> dtos = new ArrayList<>();
        for (Appointment app : list) {
            boolean isNew = isFirstVisitForCenter(app);
            app.setIsNewClient(isNew);

            AppointmentCalendarDTO.ClientDTO clientDTO = null;
            if (app.getClient() != null) {
                clientDTO = AppointmentCalendarDTO.ClientDTO.builder()
                        .id(app.getClient().getId())
                        .name(app.getClient().getName())
                        .phone(app.getClient().getPhone())
                        .build();
            }

            AppointmentCalendarDTO.WorkerDTO workerDTO = null;
            if (app.getWorker() != null) {
                workerDTO = AppointmentCalendarDTO.WorkerDTO.builder()
                        .id(app.getWorker().getId())
                        .name(app.getWorker().getName())
                        .build();
            }

            AppointmentCalendarDTO.TreatmentDTO treatmentDTO = null;
            if (app.getTreatment() != null) {
                treatmentDTO = AppointmentCalendarDTO.TreatmentDTO.builder()
                        .id(app.getTreatment().getId())
                        .name(app.getTreatment().getName())
                        .price(app.getTreatment().getPrice())
                        .duration(app.getTreatment().getDuration())
                        .build();
            }

            dtos.add(AppointmentCalendarDTO.builder()
                    .id(app.getId())
                    .dateTime(app.getDateTime() != null ? app.getDateTime().toString() : null)
                    .status(app.getStatus() != null ? app.getStatus().name() : null)
                    .workerMessage(app.getWorkerMessage())
                    .isNewClient(isNew)
                    .guestName(app.getGuestName())
                    .guestPhone(app.getGuestPhone())
                    .client(clientDTO)
                    .worker(workerDTO)
                    .treatment(treatmentDTO)
                    .build());
        }
        return dtos;
    }

    public boolean isFirstVisitForCenter(Appointment app) {
        if (app == null || app.getCenter() == null) return false;
        Long centerId = app.getCenter().getId();
        LocalDateTime dt = app.getDateTime() != null ? app.getDateTime() : LocalDateTime.now();

        if (app.getClient() != null) {
            long priorCount = appointmentRepository.countByCenterIdAndClientIdAndStatusInAndDateTimeBefore(
                    centerId, app.getClient().getId(), List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED), dt);
            return priorCount == 0;
        } else if (app.getGuestPhone() != null && !app.getGuestPhone().trim().isEmpty()) {
            long priorCount = appointmentRepository.countByCenterIdAndGuestPhoneAndStatusInAndDateTimeBefore(
                    centerId, app.getGuestPhone().trim(), List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED), dt);
            return priorCount == 0;
        }
        return true;
    }

    public boolean hasConfirmedAppointment(Long centerId, Long clientId, String guestPhone) {
        if (clientId != null) {
            return appointmentRepository.existsByCenterIdAndClientIdAndStatusIn(
                    centerId, clientId, List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED));
        } else if (guestPhone != null && !guestPhone.trim().isEmpty()) {
            return appointmentRepository.existsByCenterIdAndGuestPhoneAndStatusIn(
                    centerId, guestPhone.trim(), List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED));
        }
        return false;
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
