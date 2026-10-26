package com.alexxlpz.crm_cbelleza.googlecalendar;

import java.io.Serial;

/** Error al hablar con Google. {@code accessRevoked} indica que el trabajador quitó el permiso desde su cuenta. */
public class GoogleCalendarException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean accessRevoked;

    public GoogleCalendarException(String message, boolean accessRevoked, Throwable cause) {
        super(message, cause);
        this.accessRevoked = accessRevoked;
    }

    public boolean isAccessRevoked() {
        return accessRevoked;
    }
}
