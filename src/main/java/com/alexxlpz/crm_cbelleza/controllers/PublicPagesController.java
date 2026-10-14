package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.config.LegalOwner;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.RoleHomes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** Páginas públicas de información. */
@Controller
public class PublicPagesController {

    /* Apartados de cada página legal: índice lateral e índice dentro de la página */
    private static final List<String> TERMS_SECTIONS = List.of(
            "Quiénes somos", "Objeto y aceptación", "Cuentas de usuario", "Reserva de citas", "Centros de belleza",
            "Uso aceptable", "Datos personales", "Propiedad intelectual", "Responsabilidad", "Suspensión y baja",
            "Cambios", "Ley aplicable");

    private static final List<String> COOKIES_SECTIONS = List.of(
            "Qué son", "Cookies que usamos", "Almacenamiento local", "Servicios externos",
            "Por qué no pedimos permiso", "Cómo borrarlas");

    private static final List<String> PRIVACY_SECTIONS = List.of(
            "Responsable", "Qué datos tratamos", "Para qué y con qué base", "Fichas técnicas",
            "Iniciar sesión con Google", "Google Calendar", "Con quién los compartimos",
            "Cuánto tiempo los guardamos", "Seguridad", "Tus derechos", "Cambios");

    private final String contactEmail;
    private final LegalOwner legalOwner;

    public PublicPagesController(@Value("${crm.mail.admin-email}") String contactEmail, LegalOwner legalOwner) {
        this.contactEmail = contactEmail;
        this.legalOwner = legalOwner;
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

    /** La antigua página de funcionalidades ahora es una sección de Inicio; se mantiene la URL para no romper enlaces. */
    @GetMapping({"/features", "/funcionalidades"})
    public String features() {
        return "redirect:/home#funcionalidades";
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
        model.addAttribute("legalOwner", legalOwner);
        model.addAttribute("pageDescription", "Condiciones de uso de Cuquora para clientes y centros de belleza.");
        model.addAttribute("sections", TERMS_SECTIONS);
        return "terms";
    }

    @GetMapping({"/cookies", "/politica-cookies"})
    public String cookies(Model model) {
        model.addAttribute("contactEmail", contactEmail);
        model.addAttribute("pageDescription", "Qué cookies usa Cuquora, para qué sirven y cómo borrarlas.");
        model.addAttribute("sections", COOKIES_SECTIONS);
        return "cookies";
    }

    @GetMapping({"/privacy", "/privacidad"})
    public String privacy(Model model) {
        model.addAttribute("contactEmail", contactEmail);
        model.addAttribute("legalOwner", legalOwner);
        model.addAttribute("pageDescription",
                "Qué datos personales trata Cuquora, para qué, con quién los comparte y cómo ejercer tus derechos.");
        model.addAttribute("sections", PRIVACY_SECTIONS);
        return "privacy";
    }
}
