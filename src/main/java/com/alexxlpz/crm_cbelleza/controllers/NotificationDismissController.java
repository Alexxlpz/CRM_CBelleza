package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.NotificationDismissalService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Quitar avisos de la campana (trabajador o cliente): uno con su «x» o todos con «Limpiar todas».
 * Cada ruta cuelga de la zona de su rol, así que la seguridad ya decide quién entra.
 */
@RestController
@ApiEndpoint
public class NotificationDismissController {

    private final NotificationDismissalService dismissals;

    public NotificationDismissController(NotificationDismissalService dismissals) {
        this.dismissals = dismissals;
    }

    @PostMapping({"/worker/notifications/dismiss", "/client/notifications/dismiss"})
    public Map<String, Object> dismiss(@AuthenticationPrincipal AppUserDetails user,
                                       @RequestParam(name = "key", required = false) List<String> keys) {
        int dismissed = keys == null ? 0 : dismissals.dismiss(user.getId(), keys);
        return Map.of("status", "OK", "dismissed", dismissed);
    }
}
