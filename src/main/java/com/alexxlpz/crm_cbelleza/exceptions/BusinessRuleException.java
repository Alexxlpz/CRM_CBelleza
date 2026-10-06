package com.alexxlpz.crm_cbelleza.exceptions;

/** Regla de negocio incumplida. Su mensaje está pensado para mostrarse al usuario tal cual. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
