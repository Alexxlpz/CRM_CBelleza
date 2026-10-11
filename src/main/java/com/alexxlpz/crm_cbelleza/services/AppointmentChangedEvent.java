package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;

/**
 * Una cita ha cambiado. Lo escuchan, tras guardar el cambio, la sincronización con Google Calendar
 * y los correos de citas.
 */
public record AppointmentChangedEvent(Long appointmentId, AppointmentChange change) {
}
