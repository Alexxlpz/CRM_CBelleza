package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.services.NotificationService;
import com.alexxlpz.crm_cbelleza.services.NotificationService.NotificationItem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Añade a todas las vistas los datos del usuario autenticado que usan la navbar y los fragmentos
 * (rol, nombre, centro y notificaciones). Así ningún controlador tiene que repetirlos.
 */
@ControllerAdvice(annotations = Controller.class)
public class CurrentUserModelAdvice {

    private final NotificationService notificationService;
    private final boolean devLoginEnabled;

    public CurrentUserModelAdvice(NotificationService notificationService, Environment environment) {
        this.notificationService = notificationService;
        this.devLoginEnabled = environment.acceptsProfiles(Profiles.of("dev"));
    }

    @ModelAttribute
    public void addCurrentUser(@AuthenticationPrincipal AppUserDetails user,
                               HttpServletRequest request,
                               Model model) {
        model.addAttribute("devLoginEnabled", devLoginEnabled);
        if (user == null) {
            return;
        }
        model.addAttribute("currentUser", user);
        model.addAttribute("sessionRole", user.getRole().name());
        model.addAttribute("sessionUserName", user.getName());
        model.addAttribute("accountSection", accountSection(request.getRequestURI()));
        if (user.isWorker()) {
            model.addAttribute("sessionCenterId", user.getCenterId());
            model.addAttribute("sessionCenterName", user.getCenterName());
            if ("GET".equalsIgnoreCase(request.getMethod()) && user.getCenterId() != null) {
                List<NotificationItem> notifications = notificationService.getNotificationsForCenter(user.getCenterId());
                model.addAttribute("notifications", notifications);
            }
        }
    }

    /**
     * Apartado de "Mi Cuenta" al que pertenece la página actual; el menú lateral móvil lo marca como activo.
     * Todas las pantallas de gestión (agenda, inventario, clientes...) cuelgan de "Mi Panel".
     */
    static String accountSection(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.equals("/worker/center") || uri.startsWith("/worker/center/")) {
            return "center";
        }
        if (uri.startsWith("/worker/")) {
            return "panel";
        }
        if (uri.startsWith("/client/appointments")) {
            return "appointments";
        }
        if (uri.startsWith("/client/profile")) {
            return "profile";
        }
        return null;
    }
}
