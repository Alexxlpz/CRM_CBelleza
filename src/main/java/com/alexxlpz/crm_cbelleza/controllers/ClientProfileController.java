package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.ProfileForm;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleCalendarService;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.AuthenticationSessionService;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Perfil del cliente. */
@Controller
public class ClientProfileController {

    private final UserService userService;
    private final AppointmentService appointmentService;
    private final AuthenticationSessionService authSession;
    private final GoogleCalendarService googleCalendar;

    public ClientProfileController(UserService userService,
                                   AppointmentService appointmentService,
                                   AuthenticationSessionService authSession,
                                   GoogleCalendarService googleCalendar) {
        this.userService = userService;
        this.appointmentService = appointmentService;
        this.authSession = authSession;
        this.googleCalendar = googleCalendar;
    }

    @GetMapping("/client/profile")
    public String profile(@AuthenticationPrincipal AppUserDetails user, Model model) {
        User client = userService.getUser(user.getId());
        model.addAttribute("client", client);
        // Cuenta creada con Google: la sección de contraseña sirve para añadir una, no para cambiarla
        model.addAttribute("hasPassword", client.getPassword() != null);
        model.addAttribute("appointmentsCount", appointmentService.countClientAppointments(user.getId()));
        model.addAttribute("calendarSync", googleCalendar.status(user.getId()));
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
}
