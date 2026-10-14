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

/**
 * Interruptor «Guardar mis citas en Google Calendar»: el del trabajador en «Mi Centro» y el del cliente
 * en «Mi Perfil». Ida y vuelta a Google y desconexión; cada rol vuelve a su propia página.
 */
@Controller
public class GoogleCalendarController {

    /** Valor aleatorio que viaja a Google y vuelve: la respuesta tiene que ser de la petición que hizo esta persona. */
    static final String STATE_ATTRIBUTE = "GOOGLE_CALENDAR_STATE";

    private static final String WORKER_PAGE = "/worker/center";
    private static final String CLIENT_PAGE = "/client/profile";

    private final GoogleCalendarService calendar;
    private final String siteUrl;

    public GoogleCalendarController(GoogleCalendarService calendar, @Value("${crm.site-url:}") String siteUrl) {
        this.calendar = calendar;
        this.siteUrl = siteUrl.strip().replaceAll("/+$", "");
    }

    @PostMapping("/worker/google-calendar/connect")
    public String connect(HttpSession session, RedirectAttributes redirect) {
        return startConnection(session, redirect, GoogleCalendarSettings.CALLBACK_PATH, WORKER_PAGE);
    }

    @GetMapping(GoogleCalendarSettings.CALLBACK_PATH)
    public String callback(@AuthenticationPrincipal AppUserDetails worker,
                           @RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error,
                           HttpSession session,
                           RedirectAttributes redirect) {
        finishConnection(worker, code, state, error, session, redirect, GoogleCalendarSettings.CALLBACK_PATH,
                "Sincronización activada: tus citas confirmadas aparecerán en el calendario «Cuquora» de Google");
        return "redirect:" + WORKER_PAGE;
    }

    @PostMapping("/worker/google-calendar/disconnect")
    public String disconnect(@AuthenticationPrincipal AppUserDetails worker, RedirectAttributes redirect) {
        calendar.disconnect(worker.getId());
        redirect.addFlashAttribute("successMessage", "Sincronización con Google Calendar desactivada.");
        return "redirect:" + WORKER_PAGE;
    }

    @PostMapping("/client/google-calendar/connect")
    public String connectClient(HttpSession session, RedirectAttributes redirect) {
        return startConnection(session, redirect, GoogleCalendarSettings.CLIENT_CALLBACK_PATH, CLIENT_PAGE);
    }

    @GetMapping(GoogleCalendarSettings.CLIENT_CALLBACK_PATH)
    public String clientCallback(@AuthenticationPrincipal AppUserDetails client,
                                 @RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(required = false) String error,
                                 HttpSession session,
                                 RedirectAttributes redirect) {
        finishConnection(client, code, state, error, session, redirect, GoogleCalendarSettings.CLIENT_CALLBACK_PATH,
                "Listo: tus citas confirmadas aparecerán en el calendario «Mis citas · Cuquora» de Google");
        return "redirect:" + CLIENT_PAGE;
    }

    @PostMapping("/client/google-calendar/disconnect")
    public String disconnectClient(@AuthenticationPrincipal AppUserDetails client, RedirectAttributes redirect) {
        calendar.disconnect(client.getId());
        redirect.addFlashAttribute("successMessage", "Tus citas ya no se copian a Google Calendar.");
        return "redirect:" + CLIENT_PAGE;
    }

    private String startConnection(HttpSession session, RedirectAttributes redirect, String callbackPath, String page) {
        try {
            String state = UUID.randomUUID().toString();
            String url = calendar.authorizationUrl(state, callbackUrl(callbackPath));
            session.setAttribute(STATE_ATTRIBUTE, state);
            return "redirect:" + url;
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:" + page;
        }
    }

    private void finishConnection(AppUserDetails user, String code, String state, String error,
                                  HttpSession session, RedirectAttributes redirect,
                                  String callbackPath, String successText) {
        Object expectedState = session.getAttribute(STATE_ATTRIBUTE);
        session.removeAttribute(STATE_ATTRIBUTE);

        if (expectedState == null || !expectedState.equals(state)) {
            redirect.addFlashAttribute("errorMessage", "La conexión con Google ha caducado. Vuelve a activar la sincronización.");
        } else if (error != null || code == null) {
            redirect.addFlashAttribute("errorMessage", "Has cancelado la conexión con Google Calendar. Tus citas no se han sincronizado.");
        } else {
            try {
                int copied = calendar.connect(user.getId(), code, callbackUrl(callbackPath));
                redirect.addFlashAttribute("successMessage", successText
                        + (copied > 0 ? " (se han copiado " + copied + " " + (copied == 1 ? "cita próxima" : "citas próximas") + ")." : "."));
            } catch (BusinessRuleException e) {
                redirect.addFlashAttribute("errorMessage", e.getMessage());
            }
        }
    }

    /** Debe coincidir con una URI autorizada en Google Cloud: con SITE_URL si está configurada. */
    private String callbackUrl(String path) {
        String base = siteUrl.isEmpty()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : siteUrl;
        return base + path;
    }
}
