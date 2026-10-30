package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;

/**
 * Enlace corto de cada centro ({@code /c/centro-historico}) para la bio de Instagram, Google Maps o WhatsApp.
 * Es su escaparate público: datos, carta, horario y opiniones sin iniciar sesión. Para reservar se pasa a la ficha del
 * cliente ({@code /client/centers/nombre}), que pide entrar y después vuelve a ella.
 */
@Controller
public class CenterLinkController {

    private final CenterService centers;
    private final CenterPageModel centerPage;

    public CenterLinkController(CenterService centers, CenterPageModel centerPage) {
        this.centers = centers;
        this.centerPage = centerPage;
    }

    @GetMapping("/c/{slug}")
    public String open(@PathVariable String slug, Model model) {
        Center center = published(slug);
        // Una sola dirección por centro: /c/Centro-Historico lleva a /c/centro-historico
        if (!slug.equals(slug.toLowerCase(Locale.ROOT))) {
            return "redirect:/c/" + slug.toLowerCase(Locale.ROOT);
        }
        centerPage.addCenter(model, center, true);
        model.addAttribute("pageDescription", "Tratamientos, precios, horario y opiniones de " + center.getName()
                + ". Reserva tu cita en Cuquora.");
        return "center_public";
    }

    @GetMapping("/c/{slug}/opiniones")
    public String reviews(@PathVariable String slug,
                          @RequestParam(name = "orden", required = false) String order,
                          @RequestParam(name = "estrellas", required = false) Integer stars,
                          @RequestParam(name = "comentario", defaultValue = "false") boolean withComment,
                          @RequestParam(name = "tratamiento", required = false) Long treatmentId,
                          Model model) {
        centerPage.addReviews(model, published(slug), true, order, stars, withComment, treatmentId);
        return "client/center_reviews";
    }

    private Center published(String slug) {
        return centers.findPublishedBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Este enlace no corresponde a ningún centro."));
    }
}
