package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.RoleHomes;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Páginas públicas de información. */
@Controller
public class PublicPagesController {

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
        return "features";
    }

    /** Buscador público de centros. Los resultados se cargan desde /api/centers. */
    @GetMapping("/centers")
    public String centers(Model model) {
        model.addAttribute("activePage", "centers");
        return "centers";
    }
}
