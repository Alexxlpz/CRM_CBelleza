package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.ProfileForm;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleCalendarService;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.AuthenticationSessionService;
import com.alexxlpz.crm_cbelleza.services.AccountPrivacyService;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Perfil del cliente. */
@Controller
public class ClientProfileController {

    private final UserService userService;
    private final AppointmentService appointmentService;
    private final AuthenticationSessionService authSession;
    private final GoogleCalendarService googleCalendar;
    private final AccountPrivacyService privacy;
    private final RememberMeServices rememberMeServices;
    private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();

    public ClientProfileController(UserService userService,
                                   AppointmentService appointmentService,
                                   AuthenticationSessionService authSession,
                                   GoogleCalendarService googleCalendar,
                                   AccountPrivacyService privacy,
                                   RememberMeServices rememberMeServices) {
        this.userService = userService;
        this.appointmentService = appointmentService;
        this.authSession = authSession;
        this.googleCalendar = googleCalendar;
        this.privacy = privacy;
        this.rememberMeServices = rememberMeServices;
    }

    @GetMapping("/client/profile")
    public String profile(@AuthenticationPrincipal AppUserDetails user, Model model) {
        User client = userService.getUser(user.getId());
        model.addAttribute("client", client);
        // Cuenta creada con Google: la sección de contraseña sirve para añadir una, no para cambiarla
        model.addAttribute("hasPassword", client.getPassword() != null);
        model.addAttribute("appointmentsCount", appointmentService.countClientAppointments(user.getId()));
        model.addAttribute("calendarSync", googleCalendar.status(user.getId()));
        model.addAttribute("confirmationWord", AccountPrivacyService.CONFIRMATION_WORD);
        model.addAttribute("activePage", "profile");
        return "client/profile";
    }

    @PostMapping("/client/profile")
    public String update(@AuthenticationPrincipal AppUserDetails user,
                         @ModelAttribute ProfileForm form,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         RedirectAttributes redirect) {
        try {
            User updated = userService.updateProfile(user.getId(), form);
            authSession.refresh(updated, request, response);
            redirect.addFlashAttribute("successMessage", "Tus datos personales se han actualizado correctamente.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/client/profile";
    }

    /** «Descargar mis datos»: todo lo que Cuquora guarda del cliente, en un JSON. */
    @GetMapping("/client/profile/data")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AppUserDetails user) {
        String fileName = "mis-datos-cuquora-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(privacy.export(user.getId()));
    }

    /** «Borrar mi cuenta»: si la confirma, se borra, se cierra la sesión (también las recordadas) y vuelve al inicio de sesión. */
    @PostMapping("/client/profile/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails user,
                         @RequestParam(required = false) String confirmation,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         RedirectAttributes redirect) {
        try {
            privacy.deleteClientAccount(user.getId(), confirmation);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/client/profile#borrar-cuenta";
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (rememberMeServices instanceof LogoutHandler rememberMeLogout) {
            rememberMeLogout.logout(request, response, auth);
        }
        sessionLogout.logout(request, response, auth);
        redirect.addFlashAttribute("successMessage", "Tu cuenta se ha borrado. Gracias por haber usado Cuquora.");
        return "redirect:/login";
    }
}
