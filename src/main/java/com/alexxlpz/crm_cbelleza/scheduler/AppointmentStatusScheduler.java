package com.alexxlpz.crm_cbelleza.scheduler;

import com.alexxlpz.crm_cbelleza.services.AppointmentLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Cada minuto marca como completadas las citas confirmadas que ya han terminado. */
@Component
public class AppointmentStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentStatusScheduler.class);
    private final AppointmentLifecycleService lifecycleService;

    public AppointmentStatusScheduler(AppointmentLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(fixedRate = 60_000)
    public void autoUpdateCompletedAppointments() {
        try {
            int updated = lifecycleService.completePastAppointments(LocalDateTime.now());
            if (updated > 0) {
                log.info("Marcadas como completadas {} cita(s) finalizadas", updated);
            }
        } catch (Exception e) {
            log.error("Error al actualizar el estado de las citas finalizadas", e);
        }
    }
}
