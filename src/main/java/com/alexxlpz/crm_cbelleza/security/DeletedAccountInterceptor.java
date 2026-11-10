package com.alexxlpz.crm_cbelleza.security;

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
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;

/**
 * Quien borra su cuenta desde un dispositivo deja de entrar al momento en los demás donde tuviera la sesión abierta:
 * en cada petición del área de cliente se comprueba que la cuenta sigue activa. Si no, se cierra la sesión y vuelve
 * al inicio de sesión con un aviso.
 */
@Component
public class DeletedAccountInterceptor implements HandlerInterceptor {

    static final String DELETED_MESSAGE = "Esta cuenta se ha borrado.";

    private final UserRepository users;
    private final RememberMeServices rememberMeServices;
    private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();

    public DeletedAccountInterceptor(UserRepository users, RememberMeServices rememberMeServices) {
        this.users = users;
        this.rememberMeServices = rememberMeServices;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails user)
                || !user.isClient() || users.existsByIdAndDeletedAtIsNull(user.getId())) {
            return true;
        }
        if (rememberMeServices instanceof LogoutHandler rememberMeLogout) {
            rememberMeLogout.logout(request, response, auth);
        }
        sessionLogout.logout(request, response, auth);
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || request.getHeader("Accept") != null && request.getHeader("Accept").contains(MediaType.APPLICATION_JSON_VALUE)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"" + DELETED_MESSAGE + "\"}");
            return false;
        }
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("errorMessage", DELETED_MESSAGE);
        RequestContextUtils.saveOutputFlashMap("/login", request, response);
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }
}
