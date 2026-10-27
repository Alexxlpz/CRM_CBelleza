package com.alexxlpz.crm_cbelleza.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Corta el envío del login (POST /login) mientras la cuenta o la IP estén cerradas por {@link LoginAttemptService}:
 * ni siquiera se comprueba la contraseña, así que seguir probando no sirve de nada. Vuelve a /login?blocked.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginAttemptService attempts;

    public LoginThrottleFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !"/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String identifier = request.getParameter("identifier");
        if (attempts.isBlocked(identifier, request.getRemoteAddr())) {
            if (identifier != null) {
                request.getSession().setAttribute(LoginFailureHandler.LAST_IDENTIFIER_ATTRIBUTE, identifier.trim());
            }
            response.sendRedirect(request.getContextPath() + "/login?blocked");
            return;
        }
        chain.doFilter(request, response);
    }
}
