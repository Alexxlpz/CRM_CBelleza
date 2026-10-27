package com.alexxlpz.crm_cbelleza.forms;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Reglas únicas para contraseñas nuevas (registro, invitaciones y cambio de contraseña). */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /**
     * Las más usadas (listas públicas de filtraciones, también en español): son lo primero que se prueba al adivinar
     * una contraseña. Se comparan sin mayúsculas ni espacios.
     */
    private static final Set<String> COMMON = Set.of(
            "12345678", "123456789", "1234567890", "12345678910", "87654321", "11111111", "00000000", "12341234",
            "11223344", "12121212", "123123123", "1q2w3e4r", "1qaz2wsx", "qwertyui", "qwerty12", "qwerty123",
            "qwertyuiop", "asdfghjk", "asdfasdf", "zxcvbnm1", "abcd1234", "abc12345", "aa123456", "a1b2c3d4",
            "password", "password1", "password12", "password123", "passw0rd", "p@ssw0rd", "iloveyou", "princess",
            "sunshine", "football", "baseball", "superman", "starwars", "whatever", "trustno1", "welcome1",
            "letmein1", "dragon12", "master12", "monkey12", "admin123", "administrador", "contraseña", "contrasena",
            "contraseña1", "contrasena1", "contraseña123", "contrasena123", "micontraseña", "micontrasena", "tequiero",
            "teamo123", "cuquora1", "cuquora123", "belleza1", "belleza123", "barcelona", "realmadrid", "madrid123",
            "españa12", "espana12", "estrella", "mariposa", "princesa", "corazon1", "chocolate", "qwerty1234");

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

    /** ¿Está entre las contraseñas más usadas? */
    public static boolean isCommon(String password) {
        return COMMON.contains(password.strip().toLowerCase(Locale.ROOT));
    }

    private void validate(String password, String confirmation) {
        if (password.length() < MIN_LENGTH) {
            throw new BusinessRuleException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.");
        }
        if (isCommon(password) || password.chars().distinct().count() == 1) {
            throw new BusinessRuleException("Esa contraseña es de las más usadas y se adivina enseguida. Elige otra.");
        }
        if (!password.equals(confirmation)) {
            throw new BusinessRuleException("Las contraseñas no coinciden. Por favor, revísalas.");
        }
    }
}
