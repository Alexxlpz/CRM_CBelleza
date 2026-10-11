package com.alexxlpz.crm_cbelleza.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Component;

/**
 * Credenciales de «Iniciar sesión con Google» (GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET).
 * Si faltan, el acceso con Google queda desactivado y el botón no se muestra.
 */
@Component
public class GoogleLoginSettings {

    public static final String REGISTRATION_ID = "google";
    /** Enlace del botón: Spring Security redirige desde aquí a la pantalla de Google. */
    public static final String AUTHORIZATION_URL = "/oauth2/authorization/" + REGISTRATION_ID;

    private final String clientId;
    private final String clientSecret;

    public GoogleLoginSettings(@Value("${crm.security.google.client-id:}") String clientId,
                               @Value("${crm.security.google.client-secret:}") String clientSecret) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
    }

    public boolean isEnabled() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    /** Google con OpenID Connect (ámbitos openid, profile y email); vuelve a /login/oauth2/code/google. */
    public ClientRegistration registration() {
        return CommonOAuth2Provider.GOOGLE.getBuilder(REGISTRATION_ID)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
    }
}
