package com.alexxlpz.crm_cbelleza.forms;

/** Utilidades para normalizar los textos que llegan de los formularios. */
public final class FormText {

    private FormText() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Devuelve el texto sin espacios laterales, o {@code null} si está vacío. */
    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /** Sin espacios laterales y con los de dentro reducidos a uno; {@code null} si está vacío. */
    public static String collapseSpaces(String value) {
        return isBlank(value) ? null : value.trim().replaceAll("\\s+", " ");
    }
}
