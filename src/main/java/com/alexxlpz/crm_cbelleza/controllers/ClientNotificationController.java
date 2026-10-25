package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.ClientNotificationService;
import com.alexxlpz.crm_cbelleza.services.ReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** La campana del cliente avisa al abrirse: lo que había deja de contar como nuevo. */
@RestController
@ApiEndpoint
public class ClientNotificationController {

    private final ClientNotificationService notifications;
    private final ReviewService reviews;

    public ClientNotificationController(ClientNotificationService notifications, ReviewService reviews) {
        this.notifications = notifications;
        this.reviews = reviews;
    }

    /** El aviso «¿Qué tal tu cita?» ya se ha enseñado: no vuelve a salir para esa cita. */
    @PostMapping("/client/appointments/{id}/review-prompt/seen")
    public Map<String, String> reviewPromptSeen(@AuthenticationPrincipal AppUserDetails client, @PathVariable Long id) {
        reviews.markPrompted(client.getId(), id);
        return Map.of("status", "OK");
    }

    /** «No mostrar más»: el aviso para valorar no vuelve a salir para ninguna cita. */
    @PostMapping("/client/review-prompts/off")
    public Map<String, String> reviewPromptsOff(@AuthenticationPrincipal AppUserDetails client) {
        reviews.stopPrompts(client.getId());
        return Map.of("status", "OK");
    }

    @PostMapping("/client/notifications/seen")
    public Map<String, String> seen(@AuthenticationPrincipal AppUserDetails client) {
        notifications.markSeen(client.getId());
        return Map.of("status", "OK");
    }
}
