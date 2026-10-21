package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * Quien ha salido del equipo de un centro deja de entrar en su panel al momento, aunque tenga la sesión abierta:
 * la sesión guarda el centro, así que en cada petición del panel se comprueba que sigue en él. Si no, se cierra
 * su sesión (también la recordada en sus dispositivos) y vuelve al login con un aviso.
 */
@Component
public class CenterMembershipInterceptor implements HandlerInterceptor {

    static final String REMOVED_MESSAGE = "Ya no formas parte del equipo de %s. Si crees que es un error, habla con el dueño del centro.";

    private final UserRepository users;
    private final RememberMeServices rememberMeServices;
    private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();

    public CenterMembershipInterceptor(UserRepository users, RememberMeServices rememberMeServices) {
        this.users = users;
        this.rememberMeServices = rememberMeServices;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails user)
                || user.getCenterId() == null || users.existsByIdAndCenterId(user.getId(), user.getCenterId())) {
            return true;
        }
        String message = REMOVED_MESSAGE.formatted(user.getCenterName());
        if (rememberMeServices instanceof LogoutHandler rememberMeLogout) {
            rememberMeLogout.logout(request, response, auth);
        }
        sessionLogout.logout(request, response, auth);
        if ((handler instanceof HandlerMethod method && method.getBeanType().isAnnotationPresent(ApiEndpoint.class))
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
            return false;
        }
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("errorMessage", message);
        RequestContextUtils.saveOutputFlashMap("/login", request, response);
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }
}
