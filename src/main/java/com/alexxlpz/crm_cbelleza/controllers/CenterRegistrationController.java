package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.booking.CenterTimeZones;
import com.alexxlpz.crm_cbelleza.config.GoogleLoginSettings;
import com.alexxlpz.crm_cbelleza.config.SiteModelAdvice;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.forms.PasswordPolicy;
import com.alexxlpz.crm_cbelleza.mail.CenterOnboardingMailService;
import com.alexxlpz.crm_cbelleza.security.PublicFormLimiter;
import com.alexxlpz.crm_cbelleza.services.CenterPhotoService;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService.Registration;
import com.alexxlpz.crm_cbelleza.services.InvitationService;
import com.alexxlpz.crm_cbelleza.services.InvitationService.Renewal;
import com.alexxlpz.crm_cbelleza.services.OpeningHoursService;
import com.alexxlpz.crm_cbelleza.services.TeamService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Alta de centros: el formulario público, la página donde administración publica el centro
 * y la invitación con la que cada trabajador pone su contraseña.
 */
@Controller
public class CenterRegistrationController {

    /** Especialidades que se pueden marcar al dar de alta el centro. */
    private static final List<String> SPECIALTIES = List.of(
            "Estética facial y corporal", "Peluquería y coloración", "Manicura y uñas",
            "Spa y masajes", "Medicina estética", "Depilación láser o cera");

    private final CenterRegistrationService registrationService;
    private final InvitationService invitationService;
    private final CenterOnboardingMailService mailService;
    private final SiteModelAdvice site;
    private final GoogleLoginSettings google;
    private final UserService userService;
    private final OpeningHoursService openingHours;
    private final TeamService team;
    private final PublicFormLimiter limiter;

    public CenterRegistrationController(CenterRegistrationService registrationService,
                                        InvitationService invitationService,
                                        CenterOnboardingMailService mailService,
                                        SiteModelAdvice site,
                                        GoogleLoginSettings google,
                                        UserService userService,
                                        OpeningHoursService openingHours,
                                        TeamService team,
                                        PublicFormLimiter limiter) {
        this.openingHours = openingHours;
        this.team = team;
        this.limiter = limiter;
        this.registrationService = registrationService;
        this.invitationService = invitationService;
        this.mailService = mailService;
        this.site = site;
        this.google = google;
        this.userService = userService;
    }

    @GetMapping({"/register-center", "/alta-centro"})
    public String registerCenter(Model model) {
        if (!model.containsAttribute("registered")) {
            model.addAttribute("form", CenterRegistrationForm.empty());
            model.addAttribute("hourRows", openingHours.rows(openingHours.defaults()));
        }
        return registrationPage(model);
    }

    @PostMapping({"/register-center", "/alta-centro"})
    public String submitRegistration(@ModelAttribute CenterRegistrationForm form,
                                     @RequestParam(required = false) MultipartFile photo,
                                     @RequestParam(name = ContactController.HONEYPOT, required = false) String honeypot,
                                     HttpServletRequest request,
                                     Model model, RedirectAttributes redirect) {
        if (honeypot != null && !honeypot.isBlank()) {
            // Un bot: se le contesta como si nada, sin crear el centro ni enviar correos
            redirect.addFlashAttribute("registered", true);
            redirect.addFlashAttribute("registeredCenter", form.centerName());
            redirect.addFlashAttribute("registeredEmail", form.email());
            redirect.addFlashAttribute("registeredTeam", 0);
            return "redirect:/register-center";
        }
        Registration registration;
        try {
            limiter.check(PublicFormLimiter.Form.CENTER_REGISTRATION, request.getRemoteAddr());
            registration = registrationService.register(form, photo);
        } catch (BusinessRuleException e) {
            // Se vuelve a pintar el formulario con lo escrito: es largo y no se debe perder nada
            model.addAttribute("form", form);
            model.addAttribute("hourRows", openingHours.rows(form.openingHours()));
            model.addAttribute("errorMessage", e.getMessage());
            // El navegador no deja volver a rellenar un campo de archivo: hay que avisar de que la elija otra vez
            model.addAttribute("photoLost", photo != null && !photo.isEmpty());
            return registrationPage(model);
        }
        mailService.sendRegistration(form, registration, site.siteUrl());
        redirect.addFlashAttribute("registered", true);
        redirect.addFlashAttribute("registeredCenter", registration.centerName());
        redirect.addFlashAttribute("registeredEmail", registration.owner().email());
        redirect.addFlashAttribute("registeredTeam", registration.invitees().size() - 1);
        return "redirect:/register-center";
    }

    @GetMapping("/register-center/approve/{token}")
    public String approvalPage(@PathVariable String token, Model model) {
        model.addAttribute("token", token);
        return approvalView(registrationService.pendingCenter(token), model);
    }

    @PostMapping("/register-center/approve/{token}")
    public String approve(@PathVariable String token, Model model) {
        Center center = registrationService.publish(token);
        mailService.sendPublished(center.getName(), center.getEmail(), site.siteUrl());
        // Hasta ahora solo el responsable había recibido su enlace (ver TeamService.invitationsOnPublish)
        team.invitationsOnPublish(center.getId()).forEach(invitee -> mailService.sendInvitationAsync(
                invitee.name(), invitee.email(), invitee.centerName(), invitee.token(), site.siteUrl()));
        model.addAttribute("justPublished", true);
        return approvalView(center, model);
    }

    @GetMapping("/invitation/{token}")
    public String invitation(@PathVariable String token, Model model) {
        model.addAttribute("invitation", invitationService.view(token));
        model.addAttribute("token", token);
        model.addAttribute("minPasswordLength", PasswordPolicy.MIN_LENGTH);
        model.addAttribute("googleLoginEnabled", google.isEnabled());
        model.addAttribute("googleLoginUrl", GoogleLoginSettings.AUTHORIZATION_URL);
        return "invitation";
    }

    @PostMapping("/invitation/{token}")
    public String acceptInvitation(@PathVariable String token,
                                   @RequestParam(required = false) String password,
                                   @RequestParam(required = false) String confirmPassword,
                                   Model model, RedirectAttributes redirect) {
        try {
            var user = invitationService.accept(token, password, confirmPassword);
            redirect.addFlashAttribute("successMessage", "Contraseña guardada. Ya puedes entrar con " + user.getEmail() + ".");
            return "redirect:/login";
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return invitation(token, model);
        }
    }

    @PostMapping("/invitation/{token}/renew")
    public String renewInvitation(@PathVariable String token, Model model) {
        try {
            Renewal renewal = invitationService.renew(token);
            mailService.sendInvitationAsync(renewal.name(), renewal.email(), renewal.centerName(), renewal.token(), site.siteUrl());
            model.addAttribute("renewedEmail", renewal.email());
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
        }
        return invitation(token, model);
    }

    private String approvalView(Center center, Model model) {
        model.addAttribute("center", center);
        model.addAttribute("team", userService.getWorkersByCenter(center.getId()));
        model.addAttribute("mapUrl", "https://www.openstreetmap.org/?mlat=%s&mlon=%s#map=18/%s/%s"
                .formatted(center.getLatitude(), center.getLongitude(), center.getLatitude(), center.getLongitude()));
        model.addAttribute("timeZoneLabel", CenterTimeZones.OPTIONS.getOrDefault(center.getTimeZone(), center.getTimeZone()));
        model.addAttribute("openingHoursLines", openingHours.forCenter(center.getId()).describe());
        return "center-approval";
    }

    private String registrationPage(Model model) {
        model.addAttribute("activePage", "register-center");
        model.addAttribute("pageDescription", "Da de alta tu centro de belleza en Cuquora y recibe reservas online de nuevos clientes.");
        model.addAttribute("specialties", SPECIALTIES);
        model.addAttribute("timeZones", CenterTimeZones.OPTIONS);
        model.addAttribute("loyaltyModes", LoyaltyMode.values());
        model.addAttribute("maxExtraWorkers", CenterRegistrationService.MAX_EXTRA_WORKERS);
        model.addAttribute("hourOptions", openingHours.timeOptions());
        model.addAttribute("maxPhotoMb", CenterPhotoService.MAX_UPLOAD_MB);
        model.addAttribute("invitationDays", InvitationService.VALIDITY_DAYS);
        return "register-center";
    }
}
