package com.alexxlpz.crm_cbelleza.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.io.IOException;

/**
 * Si un usuario entra en una zona de otro rol (un trabajador en /client/** o un cliente en /worker/**),
 * se le redirige a su página de inicio con un aviso en lugar de mostrar un error 403.
 * Las peticiones AJAX/API reciben un 403 normal.
 */
@Component
public class RoleAwareAccessDeniedHandler implements AccessDeniedHandler {

    private final SessionFlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        boolean isApiCall = request.getRequestURI().startsWith("/api/")
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || !"GET".equalsIgnoreCase(request.getMethod());
        if (isApiCall) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppUserDetails user = auth != null && auth.getPrincipal() instanceof AppUserDetails u ? u : null;
        String target = RoleHomes.homeFor(user);

        FlashMap flashMap = new FlashMap();
        flashMap.put("errorMessage", messageFor(user));
        flashMap.setTargetRequestPath(target);
        flashMapManager.saveOutputFlashMap(flashMap, request, response);

        response.sendRedirect(request.getContextPath() + target);
    }

    private String messageFor(AppUserDetails user) {
        if (user != null && user.isWorker()) {
            return "Has iniciado sesión como trabajador. Esa sección es exclusiva para clientes.";
        }
        if (user != null && user.isClient()) {
            return "Esa sección es exclusiva para el personal de los centros.";
        }
        return "No tienes permiso para acceder a esa página.";
    }
}
