package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleCalendarService;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleCalendarSettings;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/** Interruptor «Sincronizar con Google Calendar» de «Mi Centro»: ida y vuelta a Google y desconexión. */
@Controller
public class GoogleCalendarController {

    /** Valor aleatorio que viaja a Google y vuelve: la respuesta tiene que ser de la petición que hizo este trabajador. */
    static final String STATE_ATTRIBUTE = "GOOGLE_CALENDAR_STATE";

    private final GoogleCalendarService calendar;
    private final String siteUrl;

    public GoogleCalendarController(GoogleCalendarService calendar, @Value("${crm.site-url:}") String siteUrl) {
        this.calendar = calendar;
        this.siteUrl = siteUrl.strip().replaceAll("/+$", "");
    }

    @PostMapping("/worker/google-calendar/connect")
    public String connect(HttpSession session, RedirectAttributes redirect) {
        try {
            String state = UUID.randomUUID().toString();
            String url = calendar.authorizationUrl(state, callbackUrl());
            session.setAttribute(STATE_ATTRIBUTE, state);
            return "redirect:" + url;
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/worker/center";
        }
    }

    @GetMapping(GoogleCalendarSettings.CALLBACK_PATH)
    public String callback(@AuthenticationPrincipal AppUserDetails worker,
                           @RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error,
                           HttpSession session,
                           RedirectAttributes redirect) {
        Object expectedState = session.getAttribute(STATE_ATTRIBUTE);
        session.removeAttribute(STATE_ATTRIBUTE);

        if (expectedState == null || !expectedState.equals(state)) {
            redirect.addFlashAttribute("errorMessage", "La conexión con Google ha caducado. Vuelve a activar la sincronización.");
        } else if (error != null || code == null) {
            redirect.addFlashAttribute("errorMessage", "Has cancelado la conexión con Google Calendar. Tus citas no se han sincronizado.");
        } else {
            try {
                int copied = calendar.connect(worker.getId(), code, callbackUrl());
                redirect.addFlashAttribute("successMessage", "Sincronización activada: tus citas confirmadas aparecerán en el calendario «Cuquora» de Google"
                        + (copied > 0 ? " (se han copiado " + copied + " " + (copied == 1 ? "cita" : "citas") + " próximas)." : "."));
            } catch (BusinessRuleException e) {
                redirect.addFlashAttribute("errorMessage", e.getMessage());
            }
        }
        return "redirect:/worker/center";
    }

    @PostMapping("/worker/google-calendar/disconnect")
    public String disconnect(@AuthenticationPrincipal AppUserDetails worker, RedirectAttributes redirect) {
        calendar.disconnect(worker.getId());
        redirect.addFlashAttribute("successMessage", "Sincronización con Google Calendar desactivada.");
        return "redirect:/worker/center";
    }

    /** Debe coincidir con una URI autorizada en Google Cloud: con SITE_URL si está configurada. */
    private String callbackUrl() {
        String base = siteUrl.isEmpty()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : siteUrl;
        return base + GoogleCalendarSettings.CALLBACK_PATH;
    }
}
