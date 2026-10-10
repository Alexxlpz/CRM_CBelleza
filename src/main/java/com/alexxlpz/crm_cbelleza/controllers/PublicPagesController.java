package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.RoleHomes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Páginas públicas de información. */
@Controller
public class PublicPagesController {

    private final String contactEmail;

    public PublicPagesController(@Value("${crm.mail.admin-email}") String contactEmail) {
        this.contactEmail = contactEmail;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal AppUserDetails user) {
        return "redirect:" + RoleHomes.homeFor(user);
    }

    @GetMapping("/home")
    public String landing(Model model) {
        model.addAttribute("activePage", "home");
        return "landing";
    }

    @GetMapping({"/features", "/funcionalidades"})
    public String features(Model model) {
        model.addAttribute("activePage", "features");
        model.addAttribute("pageDescription",
                "Agenda de citas online, fichas técnicas de clientes, inventario y gestión del centro: todo lo que incluye Cuquora.");
        return "features";
    }

    /** Buscador público de centros. Los resultados se cargan desde /api/centers. */
    @GetMapping("/centers")
    public String centers(Model model) {
        model.addAttribute("activePage", "centers");
        model.addAttribute("pageDescription",
                "Encuentra centros de belleza cerca de ti, consulta sus tratamientos y pide cita online.");
        return "centers";
    }

    @GetMapping({"/terms", "/terminos"})
    public String terms(Model model) {
        model.addAttribute("contactEmail", contactEmail);
        model.addAttribute("pageDescription", "Condiciones de uso de Cuquora para clientes y centros de belleza.");
        return "terms";
    }

    @GetMapping({"/cookies", "/politica-cookies"})
    public String cookies(Model model) {
        model.addAttribute("contactEmail", contactEmail);
        model.addAttribute("pageDescription", "Qué cookies usa Cuquora, para qué sirven y cómo borrarlas.");
        return "cookies";
    }
}
