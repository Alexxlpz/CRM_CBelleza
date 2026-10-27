package com.alexxlpz.crm_cbelleza.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Tras iniciar sesión (con contraseña o con Google) vuelve a la página protegida que se pidió o,
 * si no había ninguna, lleva a cada rol a su página de inicio.
 */
@Component
public class RoleBasedAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AuthenticationSessionService authSession;
    private final RememberMeServices rememberMeServices;
    private final LoginAttemptService attempts;

    public RoleBasedAuthenticationSuccessHandler(AuthenticationSessionService authSession,
                                                 RememberMeServices rememberMeServices,
                                                 LoginAttemptService attempts) {
        this.authSession = authSession;
        this.rememberMeServices = rememberMeServices;
        this.attempts = attempts;
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUserDetails user) {
            return RoleHomes.homeFor(user);
        }
        return RoleHomes.PUBLIC_HOME;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof GoogleOidcUser google) {
            // Con Google se guarda en la sesión el mismo usuario que con contraseña (AppUserDetails)
            authSession.login(google.getAccount(), request, response);
            authentication = SecurityContextHolder.getContext().getAuthentication();
            // «Recordar mi sesión» marcado antes de ir a Google (lo anotó GoogleAuthorizationRequestResolver)
            Object remember = request.getSession().getAttribute(GoogleAuthorizationRequestResolver.REMEMBER_ME_ATTRIBUTE);
            request.getSession().removeAttribute(GoogleAuthorizationRequestResolver.REMEMBER_ME_ATTRIBUTE);
            if (Boolean.TRUE.equals(remember)) {
                rememberMeServices.loginSuccess(RememberMe.requested(request), response, authentication);
            }
        }
        attempts.recordSuccess(request.getParameter("identifier"));
        if (request.getSession(false) != null) {
            request.getSession(false).removeAttribute(LoginFailureHandler.LAST_IDENTIFIER_ATTRIBUTE);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
