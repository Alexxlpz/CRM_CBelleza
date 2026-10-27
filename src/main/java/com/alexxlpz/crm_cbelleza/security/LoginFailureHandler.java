package com.alexxlpz.crm_cbelleza.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Vuelve al formulario de login conservando el identificador escrito para no obligar a teclearlo de nuevo,
 * y apunta el fallo en {@link LoginAttemptService}.
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public static final String LAST_IDENTIFIER_ATTRIBUTE = "LOGIN_LAST_IDENTIFIER";

    private final LoginAttemptService attempts;

    public LoginFailureHandler(LoginAttemptService attempts) {
        super("/login?error");
        this.attempts = attempts;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String identifier = request.getParameter("identifier");
        attempts.recordFailure(identifier, request.getRemoteAddr());
        if (identifier != null) {
            request.getSession().setAttribute(LAST_IDENTIFIER_ATTRIBUTE, identifier.trim());
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
