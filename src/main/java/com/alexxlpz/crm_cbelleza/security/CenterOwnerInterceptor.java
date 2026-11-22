package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.services.CenterAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;

/**
 * Cerrado por defecto: en {@code /worker/**} solo el dueño del centro puede usar lo que no esté en
 * {@link TeamRoutes#FOR_WHOLE_TEAM}. Al resto del equipo se le frena la acción. Los formularios vuelven a su pantalla con el aviso; las peticiones fetch reciben un 403 en JSON.
 * Se consulta la base de datos en cada petición: si el dueño cede el centro, vale al momento.
 */
@Component
public class CenterOwnerInterceptor implements HandlerInterceptor {

    private final CenterAccess access;

    public CenterOwnerInterceptor(CenterAccess access) {
        this.access = access;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod method)) {
            return true; // recursos estáticos y rutas que no existen (404)
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null && TeamRoutes.allows(request.getMethod(), pattern.toString())) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppUserDetails user = auth != null && auth.getPrincipal() instanceof AppUserDetails u ? u : null;
        if (user != null && access.isOwner(user.getCenterId(), user.getId())) {
            return true;
        }
        String message = user != null ? access.ownerOnlyMessage(user.getCenterId()) : "No tienes permiso para hacer este cambio.";
        if (method.getBeanType().isAnnotationPresent(ApiEndpoint.class)
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
            return false;
        }
        String target = screenOf(request.getRequestURI());
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("errorMessage", message);
        RequestContextUtils.saveOutputFlashMap(target, request, response);
        response.sendRedirect(request.getContextPath() + target);
        return false;
    }

    /** Pantalla a la que se vuelve tras un intento sin permiso. */
    static String screenOf(String uri) {
        for (String screen : new String[]{"/worker/center", "/worker/treatments", "/worker/inventory", "/worker/clients", "/worker/reviews"}) {
            if (uri.equals(screen) || uri.startsWith(screen + "/")) {
                return screen;
            }
        }
        return RoleHomes.WORKER_HOME;
    }
}
