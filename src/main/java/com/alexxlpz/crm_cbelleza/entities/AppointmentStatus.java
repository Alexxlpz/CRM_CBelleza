package com.alexxlpz.crm_cbelleza.entities;

/** Estado de una cita. La etiqueta es la que se ve en pantalla (cliente y trabajador). */
public enum AppointmentStatus {
    /** Solicitud del cliente pendiente de que el centro responda. */
    PENDING("Pendiente"),
    /** El centro ha propuesto otras horas y espera a que el cliente elija. */
    PROPOSED("Propuesta"),
    CONFIRMED("Confirmada"),
    /** El centro rechazó la solicitud. */
    REJECTED("Rechazada"),
    /** Anulada por el cliente o por el centro, o propuesta sin respuesta. */
    CANCELLED("Anulada"),
    COMPLETED("Completada"),
    /** El cliente no vino a una cita confirmada (lo marca el centro): no suma puntos ni cuenta como visita. */
    NO_SHOW("No se presentó");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Solicitud o propuesta todavía abierta (se puede aceptar, proponer o anular). */
    public boolean isOpen() {
        return this == PENDING || this == PROPOSED;
    }
}
