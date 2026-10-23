package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.security.CenterOwnerOnly;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.TreatmentForm;
import com.alexxlpz.crm_cbelleza.forms.TreatmentOfferForm;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Carta de tratamientos del centro. */
@Controller
public class TreatmentController {

    private final TreatmentService treatmentService;
    private final UserService userService;

    public TreatmentController(TreatmentService treatmentService, UserService userService) {
        this.treatmentService = treatmentService;
        this.userService = userService;
    }

    @GetMapping("/worker/treatments")
    public String treatments(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        List<Treatment> treatments = treatmentService.getTreatmentsByCenter(worker.getCenterId());
        LocalDate today = treatmentService.todayAt(worker.getCenterId());
        model.addAttribute("treatments", treatments);
        model.addAttribute("team", userService.getWorkersByCenter(worker.getCenterId()));
        model.addAttribute("today", today);
        model.addAttribute("activeOfferCount", treatments.stream().filter(t -> t.hasActiveOffer(today)).count());
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

    @CenterOwnerOnly
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

    @CenterOwnerOnly
    @PostMapping("/worker/treatments/{id}/workers")
    public String saveWorkers(@AuthenticationPrincipal AppUserDetails worker,
                              @PathVariable Long id,
                              @RequestParam(name = "workerIds", required = false) List<Long> workerIds,
                              RedirectAttributes redirect) {
        treatmentService.setWorkers(worker.getCenterId(), id, workerIds);
        redirect.addFlashAttribute("successMessage", workerIds == null || workerIds.isEmpty()
                ? "Guardado: lo hace todo el equipo."
                : "Guardado. Al reservar, los clientes solo verán a quien lo hace.");
        return "redirect:/worker/treatments";
    }

    @CenterOwnerOnly
    @PostMapping("/worker/treatments/{id}/offer")
    public String saveOffer(@AuthenticationPrincipal AppUserDetails worker,
                            @PathVariable Long id,
                            @ModelAttribute TreatmentOfferForm form,
                            RedirectAttributes redirect) {
        try {
            treatmentService.saveOffer(worker.getCenterId(), id, form);
            redirect.addFlashAttribute("successMessage", "Oferta guardada. Los clientes ya la ven al reservar.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/worker/treatments";
    }

    @CenterOwnerOnly
    @PostMapping("/worker/treatments/{id}/offer/delete")
    public String removeOffer(@AuthenticationPrincipal AppUserDetails worker,
                              @PathVariable Long id,
                              RedirectAttributes redirect) {
        treatmentService.removeOffer(worker.getCenterId(), id);
        redirect.addFlashAttribute("successMessage", "Oferta quitada. El tratamiento vuelve a su precio de carta.");
        return "redirect:/worker/treatments";
    }
}
