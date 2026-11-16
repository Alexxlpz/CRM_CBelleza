package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;

/**
 * Saca de la cuenta a quien tiene abierta una sesión iniciada antes del último cambio de contraseña
 * ({@link PasswordChangeSessions}): se cierra la sesión y vuelve al inicio de sesión con un aviso; las peticiones
 * fetch reciben un 403 en JSON (como los demás avisos de sesión).
 */
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    static final String MESSAGE = "Tu contraseña ha cambiado. Vuelve a iniciar sesión.";

    private final PasswordChangeSessions sessions;
    private final RememberMeServices rememberMeServices;
    private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();

    public PasswordChangeInterceptor(PasswordChangeSessions sessions, RememberMeServices rememberMeServices) {
        this.sessions = sessions;
        this.rememberMeServices = rememberMeServices;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (session == null || auth == null || !(auth.getPrincipal() instanceof AppUserDetails user)
                || sessions.isStillValid(user.getId(), auth, session)) {
            return true;
        }
        if (rememberMeServices instanceof LogoutHandler rememberMeLogout) {
            rememberMeLogout.logout(request, response, auth);
        }
        sessionLogout.logout(request, response, auth);
        if (method.getBeanType().isAnnotationPresent(ApiEndpoint.class)
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || request.getHeader("Accept") != null && request.getHeader("Accept").contains(MediaType.APPLICATION_JSON_VALUE)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"" + MESSAGE + "\"}");
            return false;
        }
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("errorMessage", MESSAGE);
        RequestContextUtils.saveOutputFlashMap("/login", request, response);
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }
}
