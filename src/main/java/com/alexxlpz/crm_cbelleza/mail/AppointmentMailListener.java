package com.alexxlpz.crm_cbelleza.mail;

import com.alexxlpz.crm_cbelleza.services.AppointmentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Envía los correos de cada cambio de cita en segundo plano, cuando el cambio ya está guardado. */
@Component
public class AppointmentMailListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentMailListener.class);

    private final AppointmentMailService mail;

    public AppointmentMailListener(AppointmentMailService mail) {
        this.mail = mail;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentChanged(AppointmentChangedEvent event) {
        try {
            mail.notifyChange(event.appointmentId(), event.change());
        } catch (RuntimeException e) {
            log.error("Error al preparar los correos de la cita {}", event.appointmentId(), e);
        }
    }
}
