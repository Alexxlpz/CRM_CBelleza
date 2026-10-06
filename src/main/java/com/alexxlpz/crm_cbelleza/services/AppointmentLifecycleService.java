package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persiste como COMPLETED las citas confirmadas cuya hora de fin ya ha pasado.
 * Las lecturas no lo necesitan ({@link Appointment#getStatus()} ya calcula el estado en tiempo real):
 * lo ejecuta periódicamente el {@code AppointmentStatusScheduler}.
 */
@Service
public class AppointmentLifecycleService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentLifecycleService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public int completePastAppointments(LocalDateTime now) {
        List<Appointment> finished = appointmentRepository
                .findByStatusAndDateTimeBefore(AppointmentStatus.CONFIRMED, now).stream()
                .filter(app -> !now.isBefore(app.getEndDateTime()))
                .toList();
        finished.forEach(app -> app.setStatus(AppointmentStatus.COMPLETED));
        appointmentRepository.saveAll(finished);
        return finished.size();
    }
}
