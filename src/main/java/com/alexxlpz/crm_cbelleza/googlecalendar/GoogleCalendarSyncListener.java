package com.alexxlpz.crm_cbelleza.googlecalendar;

import com.alexxlpz.crm_cbelleza.services.AppointmentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Copia a Google Calendar cada cita aprobada o rechazada, en segundo plano y solo cuando el cambio ya
 * está guardado: si Google tarda o falla, el trabajador no lo nota al aceptar o rechazar la cita.
 */
@Component
public class GoogleCalendarSyncListener {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarSyncListener.class);

    private final GoogleCalendarService calendar;

    public GoogleCalendarSyncListener(GoogleCalendarService calendar) {
        this.calendar = calendar;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentChanged(AppointmentChangedEvent event) {
        try {
            calendar.syncAppointment(event.appointmentId());
        } catch (RuntimeException e) {
            log.error("Error al sincronizar la cita {} con Google Calendar", event.appointmentId(), e);
        }
    }
}
