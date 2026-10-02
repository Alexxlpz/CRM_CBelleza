package com.alexxlpz.crm_cbelleza.scheduler;

import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AppointmentStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentStatusScheduler.class);
    private final AppointmentRepository appointmentRepository;

    public AppointmentStatusScheduler(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Checks every minute for confirmed appointments whose end time (start time + treatment duration)
     * has elapsed, updating their status to COMPLETED automatically in real-time.
     */
    @Scheduled(fixedRate = 60000)
    public void autoUpdateCompletedAppointments() {
        try {
            int updated = appointmentRepository.updatePastConfirmedToCompleted(LocalDateTime.now());
            if (updated > 0) {
                log.info("Auto-completed {} past appointment(s) at {}", updated, LocalDateTime.now());
            }
        } catch (Exception e) {
            log.error("Error executing autoUpdateCompletedAppointments scheduler", e);
        }
    }
}
