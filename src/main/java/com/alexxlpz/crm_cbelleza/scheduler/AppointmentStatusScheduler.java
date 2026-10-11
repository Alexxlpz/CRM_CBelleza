package com.alexxlpz.crm_cbelleza.scheduler;

import com.alexxlpz.crm_cbelleza.mail.AppointmentMailService;
import com.alexxlpz.crm_cbelleza.services.AppointmentLifecycleService;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Tareas de cada minuto: marca como completadas las citas confirmadas que ya han terminado,
 * anula las propuestas que han caducado sin respuesta y envía los recordatorios de 24 h.
 */
@Component
public class AppointmentStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentStatusScheduler.class);
    private final AppointmentLifecycleService lifecycleService;
    private final BookingService bookingService;
    private final AppointmentMailService mailService;
    private final Clock clock;

    public AppointmentStatusScheduler(AppointmentLifecycleService lifecycleService,
                                      BookingService bookingService,
                                      AppointmentMailService mailService,
                                      Clock clock) {
        this.lifecycleService = lifecycleService;
        this.bookingService = bookingService;
        this.mailService = mailService;
        this.clock = clock;
    }

    @Scheduled(fixedRate = 60_000)
    public void autoUpdateCompletedAppointments() {
        run("actualizar el estado de las citas finalizadas", () -> {
            int updated = lifecycleService.completePastAppointments(LocalDateTime.now(clock));
            if (updated > 0) {
                log.info("Marcadas como completadas {} cita(s) finalizadas", updated);
            }
        });
        run("anular las propuestas caducadas", () -> {
            int expired = bookingService.expireProposals();
            if (expired > 0) {
                log.info("Anuladas {} propuesta(s) caducadas sin respuesta", expired);
            }
        });
        run("enviar los recordatorios de cita", () -> {
            int sent = mailService.sendReminders();
            if (sent > 0) {
                log.info("Enviados {} recordatorio(s) de cita", sent);
            }
        });
    }

    /** Un fallo en una tarea no impide las demás. */
    private static void run(String task, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Error al {}", task, e);
        }
    }
}
