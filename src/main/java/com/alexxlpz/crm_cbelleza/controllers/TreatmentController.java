package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.TreatmentForm;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Carta de tratamientos del centro. */
@Controller
public class TreatmentController {

    private final TreatmentService treatmentService;

    public TreatmentController(TreatmentService treatmentService) {
        this.treatmentService = treatmentService;
    }

    @GetMapping("/worker/treatments")
    public String treatments(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        List<Treatment> treatments = treatmentService.getTreatmentsByCenter(worker.getCenterId());
        model.addAttribute("treatments", treatments);
        // Resumen y filtros calculados aquí para no meter lógica de colecciones en la plantilla
        model.addAttribute("averagePrice", treatments.stream()
                .map(Treatment::getPrice).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0));
        model.addAttribute("averageDuration", Math.round(treatments.stream()
                .map(Treatment::getDuration).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average().orElse(0)));
        model.addAttribute("treatmentTypes", Arrays.stream(TreatmentType.values())
                .filter(type -> treatments.stream().anyMatch(t -> t.getType() == type))
                .toList());
        return "worker/treatments";
    }

    @PostMapping("/worker/treatments/add")
    public String add(@AuthenticationPrincipal AppUserDetails worker,
                      @ModelAttribute TreatmentForm form,
                      RedirectAttributes redirect) {
        try {
            treatmentService.addTreatment(worker.getCenterId(), form);
            redirect.addFlashAttribute("successMessage", "Tratamiento añadido a la carta del centro.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/worker/treatments";
    }
}
