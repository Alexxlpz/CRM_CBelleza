package com.alexxlpz.crm_cbelleza.googlecalendar;

import com.alexxlpz.crm_cbelleza.config.GoogleLoginSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La sincronización con Google Calendar usa el mismo ID de cliente OAuth que «Iniciar sesión con Google»
 * (GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET) y, además, GOOGLE_TOKEN_KEY para cifrar los tokens guardados.
 * Si falta cualquiera de las tres, el interruptor del perfil aparece desactivado.
 */
@Component
public class GoogleCalendarSettings {

    /** Vueltas de Google tras dar permiso; con el dominio público, las dos deben estar autorizadas en Google Cloud. */
    public static final String CALLBACK_PATH = "/worker/google-calendar/callback";
    public static final String CLIENT_CALLBACK_PATH = "/client/google-calendar/callback";

    private final GoogleLoginSettings google;
    private final String tokenKey;

    public GoogleCalendarSettings(GoogleLoginSettings google,
                                  @Value("${crm.google-calendar.token-key:}") String tokenKey) {
        this.google = google;
        this.tokenKey = tokenKey.trim();
    }

    public boolean isEnabled() {
        return google.isEnabled() && !tokenKey.isEmpty();
    }

    public String clientId() {
        return google.clientId();
    }

    public String clientSecret() {
        return google.clientSecret();
    }

    String tokenKey() {
        return tokenKey;
    }
}
