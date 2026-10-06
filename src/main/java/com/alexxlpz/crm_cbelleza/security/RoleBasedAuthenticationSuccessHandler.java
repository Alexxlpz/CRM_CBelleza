package com.alexxlpz.crm_cbelleza.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Tras iniciar sesión vuelve a la página protegida que se pidió o, si no había ninguna,
 * lleva a cada rol a su página de inicio.
 */
@Component
public class RoleBasedAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

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
                                        Authentication authentication) throws java.io.IOException, jakarta.servlet.ServletException {
        if (request.getSession(false) != null) {
            request.getSession(false).removeAttribute(LoginFailureHandler.LAST_IDENTIFIER_ATTRIBUTE);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
