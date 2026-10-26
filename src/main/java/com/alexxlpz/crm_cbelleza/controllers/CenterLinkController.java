package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Locale;

/**
 * Enlace corto de cada centro ({@code /c/centro-historico}) para la bio de Instagram, Google Maps o WhatsApp.
 * Lleva a la ficha del centro para reservar (también por nombre, sin el id); sin sesión, el inicio de sesión devuelve
 * después a la ficha.
 */
@Controller
public class CenterLinkController {

    private final CenterService centers;

    public CenterLinkController(CenterService centers) {
        this.centers = centers;
    }

    @GetMapping("/c/{slug}")
    public String open(@PathVariable String slug) {
        return centers.findPublishedBySlug(slug)
                .map(center -> "redirect:/client/centers/" + slug.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Este enlace no corresponde a ningún centro."));
    }
}
