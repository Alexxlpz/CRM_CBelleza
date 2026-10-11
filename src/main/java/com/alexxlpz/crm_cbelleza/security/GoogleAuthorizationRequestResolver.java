package com.alexxlpz.crm_cbelleza.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Prepara el viaje a Google como el resolutor por defecto y, además, anota en la sesión si el usuario
 * marcó «Recordar mi sesión» (el botón añade {@code ?rememberMe=true}). A la vuelta de Google esa casilla
 * ya no viaja en la petición: la lee {@link RoleBasedAuthenticationSuccessHandler}.
 */
public class GoogleAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String REMEMBER_ME_ATTRIBUTE = "GOOGLE_LOGIN_REMEMBER_ME";

    private final OAuth2AuthorizationRequestResolver defaultResolver;

    public GoogleAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return remember(request, defaultResolver.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return remember(request, defaultResolver.resolve(request, clientRegistrationId));
    }

    private static OAuth2AuthorizationRequest remember(HttpServletRequest request, OAuth2AuthorizationRequest authorization) {
        if (authorization != null) {
            boolean rememberMe = "true".equals(request.getParameter(RememberMe.PARAMETER));
            request.getSession().setAttribute(REMEMBER_ME_ATTRIBUTE, rememberMe);
        }
        return authorization;
    }
}
