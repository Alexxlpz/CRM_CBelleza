package com.alexxlpz.crm_cbelleza.exceptions;

import java.io.Serial;

/**
 * Un dato de un formulario no vale: además del mensaje, dice qué campo es ({@code name}, {@code phone}, {@code email}…)
 * para marcarlo en rojo con el mensaje debajo (fragments/alerts y app.js).
 */
public class FieldException extends BusinessRuleException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public FieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
