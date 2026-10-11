package com.alexxlpz.crm_cbelleza.entities;

/**
 * Último cambio de una cita y quién lo hizo. Lo usan las campanas de avisos (cliente y trabajador)
 * y los correos: cada cambio sabe a quién hay que avisar.
 */
public enum AppointmentChange {
    REQUESTED(Side.CLIENT),
    CREATED_BY_CENTER(Side.CENTER),
    CONFIRMED(Side.CENTER),
    REJECTED(Side.CENTER),
    RESCHEDULED(Side.CENTER),
    PROPOSED(Side.CENTER),
    PROPOSAL_WITHDRAWN(Side.CENTER),
    CANCELLED_BY_CENTER(Side.CENTER),
    PROPOSAL_ACCEPTED(Side.CLIENT),
    PROPOSAL_DECLINED(Side.CLIENT),
    CANCELLED_BY_CLIENT(Side.CLIENT),
    PROPOSAL_EXPIRED(Side.SYSTEM);

    /** Quién provoca el cambio: se avisa a la otra parte. */
    public enum Side { CLIENT, CENTER, SYSTEM }

    private final Side side;

    AppointmentChange(Side side) {
        this.side = side;
    }

    public Side side() {
        return side;
    }
}
