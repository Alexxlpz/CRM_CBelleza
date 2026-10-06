package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AuthenticationSessionService;
import com.alexxlpz.crm_cbelleza.security.RoleHomes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Selector de perfiles para pruebas: entra como cualquier usuario sin contraseña.
 * SOLO existe con el perfil "dev" (spring.profiles.active=dev); en cualquier otro entorno
 * estas rutas devuelven 404.
 */
@Controller
@Profile("dev")
public class DevLoginController {

    private final UserRepository userRepository;
    private final AuthenticationSessionService authSession;

    public DevLoginController(UserRepository userRepository, AuthenticationSessionService authSession) {
        this.userRepository = userRepository;
        this.authSession = authSession;
    }

    @GetMapping("/login-selector")
    public String selector(Model model) {
        model.addAttribute("clients", userRepository.findByRole(Role.CLIENT));
        model.addAttribute("workers", userRepository.findByRole(Role.WORKER));
        return "login-selector";
    }

    @PostMapping("/select-session")
    public String selectSession(@RequestParam("userId") Long userId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        return userRepository.findWithCenterById(userId)
                .map(user -> loginAs(user, request, response))
                .orElse("redirect:/login-selector");
    }

    private String loginAs(User user, HttpServletRequest request, HttpServletResponse response) {
        authSession.login(user, request, response);
        return "redirect:" + RoleHomes.homeFor(user.getRole());
    }
}
