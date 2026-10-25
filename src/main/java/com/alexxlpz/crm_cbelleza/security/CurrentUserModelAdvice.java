package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.services.CenterAccess;
import com.alexxlpz.crm_cbelleza.services.ClientNotificationService;
import com.alexxlpz.crm_cbelleza.services.NotificationDismissalService;
import com.alexxlpz.crm_cbelleza.services.NotificationService;
import com.alexxlpz.crm_cbelleza.services.NotificationService.NotificationItem;
import com.alexxlpz.crm_cbelleza.services.ReviewService;
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
    private final ClientNotificationService clientNotificationService;
    private final ReviewService reviewService;
    private final NotificationDismissalService dismissals;
    private final CenterAccess centerAccess;
    private final boolean devLoginEnabled;

    public CurrentUserModelAdvice(NotificationService notificationService,
                                  ClientNotificationService clientNotificationService,
                                  ReviewService reviewService,
                                  NotificationDismissalService dismissals,
                                  CenterAccess centerAccess,
                                  Environment environment) {
        this.dismissals = dismissals;
        this.centerAccess = centerAccess;
        this.notificationService = notificationService;
        this.clientNotificationService = clientNotificationService;
        this.reviewService = reviewService;
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
            // Dueño del centro o trabajador estándar: las pantallas esconden lo que solo puede hacer el dueño
            boolean owner = centerAccess.isOwner(user.getCenterId(), user.getId());
            model.addAttribute("isCenterOwner", owner);
            if (!owner && user.getCenterId() != null && "GET".equalsIgnoreCase(request.getMethod())) {
                model.addAttribute("ownerName", centerAccess.ownerName(user.getCenterId()));
            }
            if ("GET".equalsIgnoreCase(request.getMethod()) && user.getCenterId() != null) {
                // Cada trabajador quita los avisos de su campana sin quitárselos a sus compañeros
                List<NotificationItem> notifications = dismissals.visible(user.getId(),
                        notificationService.getNotificationsForCenter(user.getCenterId()), NotificationItem::key);
                model.addAttribute("notifications", notifications);
            }
        } else if ("GET".equalsIgnoreCase(request.getMethod())) {
            model.addAttribute("clientNotifications", clientNotificationService.forClient(user.getId()));
            // «¿Qué tal tu cita?»: una vez por cita completada, en la primera página que abra después
            model.addAttribute("reviewPrompt", reviewService.toPrompt(user.getId()).orElse(null));
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
