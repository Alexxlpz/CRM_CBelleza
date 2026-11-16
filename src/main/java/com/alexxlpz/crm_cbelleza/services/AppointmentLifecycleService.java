package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Persiste como COMPLETED las citas confirmadas cuya hora de fin ya ha pasado en la hora local de su centro.
 * Las lecturas no lo necesitan ({@link Appointment#getStatus()} ya calcula el estado en tiempo real):
 * lo ejecuta periódicamente el {@code AppointmentStatusScheduler}.
 */
@Service
public class AppointmentLifecycleService {

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public AppointmentLifecycleService(AppointmentRepository appointmentRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    /**
     * Las citas se guardan en la hora local de cada centro: se buscan con un día de margen sobre la hora del servidor
     * (cubre cualquier zona horaria) y cada una se compara con el «ahora» de su centro.
     */
    @Transactional
    public int completePastAppointments() {
        List<Appointment> finished = appointmentRepository
                .findByStatusAndDateTimeBefore(AppointmentStatus.CONFIRMED, LocalDateTime.now(clock).plusDays(1)).stream()
                .filter(app -> !CenterClock.now(clock, app.getCenter()).isBefore(app.getEndDateTime()))
                .toList();
        finished.forEach(app -> app.setStatus(AppointmentStatus.COMPLETED));
        appointmentRepository.saveAll(finished);
        return finished.size();
    }
}
