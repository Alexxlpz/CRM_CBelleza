package com.alexxlpz.crm_cbelleza.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** Datos comunes de «Recordar mi sesión» (casilla del login, con contraseña o con Google). */
public final class RememberMe {

    /** Nombre de la casilla del formulario de login y del parámetro del botón de Google. */
    public static final String PARAMETER = "rememberMe";
    public static final int VALIDITY_SECONDS = 14 * 24 * 60 * 60;

    private RememberMe() {
    }

    /**
     * La vuelta de Google no trae la casilla; Spring solo crea la cookie si la petición la lleva marcada,
     * así que se le presenta la petición como si la llevara.
     */
    static HttpServletRequest requested(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                return PARAMETER.equals(name) ? "true" : super.getParameter(name);
            }
        };
    }
}
