package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.RegistrationForm;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.AuthenticationSessionService;
import com.alexxlpz.crm_cbelleza.security.LoginFailureHandler;
import com.alexxlpz.crm_cbelleza.security.RoleHomes;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Acceso a la cuenta: pantalla de login (el POST lo procesa Spring Security), registro de clientes
 * y redirección de /profile a la página de perfil de cada rol.
 */
@Controller
public class AuthController {

    private final UserService userService;
    private final AuthenticationSessionService authSession;

    public AuthController(UserService userService, AuthenticationSessionService authSession) {
        this.userService = userService;
        this.authSession = authSession;
    }

    @GetMapping("/login")
    public String loginPage(@AuthenticationPrincipal AppUserDetails user, HttpSession session, Model model) {
        if (user != null) {
            return "redirect:" + RoleHomes.homeFor(user);
        }
        Object lastIdentifier = session.getAttribute(LoginFailureHandler.LAST_IDENTIFIER_ATTRIBUTE);
        if (lastIdentifier != null) {
            model.addAttribute("identifier", lastIdentifier);
            session.removeAttribute(LoginFailureHandler.LAST_IDENTIFIER_ATTRIBUTE);
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(@AuthenticationPrincipal AppUserDetails user) {
        return user != null ? "redirect:" + RoleHomes.homeFor(user) : "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegistrationForm form,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           RedirectAttributes redirect) {
        try {
            User client = userService.registerClient(form);
            authSession.login(client, request, response);
            return "redirect:" + RoleHomes.CLIENT_HOME;
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            redirect.addFlashAttribute("name", form.name());
            redirect.addFlashAttribute("email", form.email());
            redirect.addFlashAttribute("phone", form.phone());
            return "redirect:/register";
        }
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserDetails user) {
        return user.isWorker() ? "redirect:/worker/center" : "redirect:/client/profile";
    }
}
