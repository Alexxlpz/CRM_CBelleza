package com.alexxlpz.crm_cbelleza.forms;

import java.util.Locale;
import java.util.regex.Pattern;

/** Correos: la misma regla en todos los formularios y en el navegador ({@code EMAIL} de form-validation.js). */
public final class Emails {

    /** Algo, «@», dominio y una terminación de al menos dos caracteres. */
    public static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    public static final int MAX_LENGTH = 254;

    private Emails() {
    }

    /** Sin espacios y en minúsculas; {@code null} si está vacío. */
    public static String normalize(String email) {
        return FormText.isBlank(email) ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** ¿Tiene forma de correo? Recibe el correo ya normalizado. */
    public static boolean isValid(String email) {
        return email != null && email.length() <= MAX_LENGTH && PATTERN.matcher(email).matches();
    }
}
