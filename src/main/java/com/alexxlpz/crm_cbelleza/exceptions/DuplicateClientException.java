package com.alexxlpz.crm_cbelleza.exceptions;

import java.io.Serial;

/**
 * Alta manual de un cliente con el teléfono o el correo de alguien que ya está en la cartera del centro. No se
 * reutiliza sin preguntar (podría ser otra persona con el mismo teléfono, como una madre y su hija): la pantalla de
 * clientes ofrece abrir su ficha o darlo de alta igualmente.
 */
public class DuplicateClientException extends BusinessRuleException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long clientId;
    private final String clientName;
    private final boolean samePhone;

    public DuplicateClientException(Long clientId, String clientName, boolean samePhone) {
        super("Ya tienes a " + clientName + " en tu cartera con ese " + (samePhone ? "teléfono" : "correo")
                + ". Elige su nombre en el buscador de clientes; si es otra persona, da de alta a la nueva desde «Clientes».");
        this.clientId = clientId;
        this.clientName = clientName;
        this.samePhone = samePhone;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    /** Coincide el teléfono (si no, el correo). */
    public boolean isSamePhone() {
        return samePhone;
    }
}
