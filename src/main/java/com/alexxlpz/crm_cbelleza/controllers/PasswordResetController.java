package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.config.SiteModelAdvice;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.PasswordPolicy;
import com.alexxlpz.crm_cbelleza.mail.CenterOnboardingMailService;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.AuthenticationSessionService;
import com.alexxlpz.crm_cbelleza.security.PublicFormLimiter;
import com.alexxlpz.crm_cbelleza.security.RoleHomes;
import com.alexxlpz.crm_cbelleza.services.PasswordResetService;
import com.alexxlpz.crm_cbelleza.services.PasswordResetService.Invitation;
import com.alexxlpz.crm_cbelleza.services.PasswordResetService.ResetLink;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * «¿Olvidaste tu contraseña?»: pedir el enlace por correo (/forgot-password) y poner la contraseña nueva con él
 * (/reset-password/{enlace}). La respuesta al pedirlo es la misma haya o no cuenta con ese correo. Al guardar la
 * contraseña nueva se entra directamente (el enlace demuestra que el correo es suyo) y se va a su pantalla principal.
 */
@Controller
public class PasswordResetController {

    private final PasswordResetService resetService;
    private final CenterOnboardingMailService mailService;
    private final PublicFormLimiter limiter;
    private final SiteModelAdvice site;
    private final UserService userService;
    private final AuthenticationSessionService authSession;

    public PasswordResetController(PasswordResetService resetService, CenterOnboardingMailService mailService,
                                   PublicFormLimiter limiter, SiteModelAdvice site, UserService userService,
                                   AuthenticationSessionService authSession) {
        this.resetService = resetService;
        this.mailService = mailService;
        this.limiter = limiter;
        this.site = site;
        this.userService = userService;
        this.authSession = authSession;
    }

    @GetMapping("/forgot-password")
    public String forgotPage(@AuthenticationPrincipal AppUserDetails user) {
        if (user != null) {
            return "redirect:" + RoleHomes.homeFor(user);
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestLink(@RequestParam(required = false) String email, HttpServletRequest request,
                              RedirectAttributes redirect) {
        try {
            limiter.check(PublicFormLimiter.Form.PASSWORD_RESET, request.getRemoteAddr());
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            redirect.addFlashAttribute("email", email);
            return "redirect:/forgot-password";
        }
        resetService.request(email).ifPresent(mail -> {
            switch (mail) {
                case ResetLink link -> mailService.sendPasswordReset(link.name(), link.email(), link.token(), site.siteUrl());
                case Invitation invitation -> mailService.sendInvitationAsync(invitation.name(), invitation.email(),
                        invitation.centerName(), invitation.token(), site.siteUrl());
            }
        });
        redirect.addFlashAttribute("sentTo", email == null ? "" : email.trim());
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password/{token}")
    public String resetPage(@PathVariable String token, Model model) {
        model.addAttribute("reset", resetService.view(token));
        model.addAttribute("token", token);
        model.addAttribute("minPasswordLength", PasswordPolicy.MIN_LENGTH);
        return "reset-password";
    }

    @PostMapping("/reset-password/{token}")
    public String reset(@PathVariable String token,
                        @RequestParam(required = false) String password,
                        @RequestParam(required = false) String confirmPassword,
                        HttpServletRequest request, HttpServletResponse response,
                        Model model, RedirectAttributes redirect) {
        try {
            Long userId = resetService.reset(token, password, confirmPassword).getId();
            // Se vuelve a leer con su centro: la sesión necesita el centro del trabajador
            AppUserDetails principal = AppUserDetails.from(userService.getUser(userId));
            authSession.login(principal, request, response);
            redirect.addFlashAttribute("successMessage", "Contraseña cambiada. Ya has entrado en tu cuenta.");
            return "redirect:" + RoleHomes.homeFor(principal);
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return resetPage(token, model);
        }
    }
}
