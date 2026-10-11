package com.alexxlpz.crm_cbelleza.forms;

/**
 * Cita creada por el trabajador desde la agenda. El cliente es uno de la cartera ({@code clientId})
 * o uno nuevo ({@code newClientName} + {@code newClientPhone}, correo opcional).
 * {@code workerId} vacío = la atiende quien la crea.
 */
public record WorkerAppointmentForm(Long clientId,
                                    String newClientName, String newClientPhone, String newClientEmail,
                                    Long treatmentId, String dateTime, Long workerId, String message,
                                    Boolean allowOutsideHours) {

    public boolean outsideHoursAllowed() {
        return Boolean.TRUE.equals(allowOutsideHours);
    }
}
