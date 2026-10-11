package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.ClientNotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** La campana del cliente avisa al abrirse: lo que había deja de contar como nuevo. */
@RestController
@ApiEndpoint
public class ClientNotificationController {

    private final ClientNotificationService notifications;

    public ClientNotificationController(ClientNotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping("/client/notifications/seen")
    public Map<String, String> seen(@AuthenticationPrincipal AppUserDetails client) {
        notifications.markSeen(client.getId());
        return Map.of("status", "OK");
    }
}
