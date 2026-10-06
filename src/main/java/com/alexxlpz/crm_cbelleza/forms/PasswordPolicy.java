package com.alexxlpz.crm_cbelleza.forms;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import org.springframework.stereotype.Component;

/** Reglas únicas para contraseñas nuevas (registro y cambio de contraseña). */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 6;

    /** Valida una contraseña obligatoria (registro). */
    public void validateRequired(String password, String confirmation) {
        if (FormText.isBlank(password)) {
            throw new BusinessRuleException("Por favor, completa todos los campos requeridos.");
        }
        validate(password, confirmation);
    }

    /** Valida una contraseña opcional (perfil): si viene vacía no se cambia. */
    public void validateOptional(String password, String confirmation) {
        if (!FormText.isBlank(password)) {
            validate(password, confirmation);
        }
    }

    private void validate(String password, String confirmation) {
        if (password.length() < MIN_LENGTH) {
            throw new BusinessRuleException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.");
        }
        if (!password.equals(confirmation)) {
            throw new BusinessRuleException("Las contraseñas no coinciden. Por favor, revísalas.");
        }
    }
}
