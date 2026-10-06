package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.WorkerCenterForm;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.AuthenticationSessionService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** "Mi centro": datos públicos del centro y cuenta del profesional. */
@Controller
public class WorkerCenterController {

    private final CenterService centerService;
    private final UserService userService;
    private final TreatmentService treatmentService;
    private final AuthenticationSessionService authSession;

    public WorkerCenterController(CenterService centerService,
                                  UserService userService,
                                  TreatmentService treatmentService,
                                  AuthenticationSessionService authSession) {
        this.centerService = centerService;
        this.userService = userService;
        this.treatmentService = treatmentService;
        this.authSession = authSession;
    }

    @GetMapping("/worker/center")
    public String view(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        Long centerId = worker.getCenterId();
        model.addAttribute("center", centerService.getCenterById(centerId));
        model.addAttribute("worker", userService.getUser(worker.getId()));
        model.addAttribute("treatmentsCount", treatmentService.countByCenter(centerId));
        model.addAttribute("workersCount", userService.countWorkersByCenter(centerId));
        model.addAttribute("activePage", "center");
        return "worker/center";
    }

    @PostMapping("/worker/center")
    public String update(@AuthenticationPrincipal AppUserDetails worker,
                         @ModelAttribute WorkerCenterForm form,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         RedirectAttributes redirect) {
        try {
            User updatedWorker = userService.updateProfile(worker.getId(), form.worker());
            centerService.updateCenter(worker.getCenterId(), form.center());
            // Recarga nombre de usuario y de centro mostrados en la navbar.
            authSession.refresh(userService.getUser(updatedWorker.getId()), request, response);
            redirect.addFlashAttribute("successMessage", "Los datos de tu centro y tu cuenta se han actualizado correctamente.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/worker/center";
    }
}
