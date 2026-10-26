package com.alexxlpz.crm_cbelleza.exceptions;

import java.io.Serial;

/** Regla de negocio incumplida. Su mensaje está pensado para mostrarse al usuario tal cual. */
public class BusinessRuleException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
