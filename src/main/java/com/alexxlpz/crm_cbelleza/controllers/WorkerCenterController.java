package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.booking.CenterTimeZones;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.forms.LoyaltySettingsForm;
import com.alexxlpz.crm_cbelleza.services.LoyaltyService;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.WorkerCenterForm;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleCalendarService;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.AuthenticationSessionService;
import com.alexxlpz.crm_cbelleza.services.CenterPhotoService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** "Mi centro": datos públicos del centro y cuenta del profesional. */
@Controller
public class WorkerCenterController {

    private final CenterService centerService;
    private final UserService userService;
    private final TreatmentService treatmentService;
    private final AuthenticationSessionService authSession;
    private final GoogleCalendarService googleCalendar;
    private final LoyaltyService loyaltyService;
    private final CenterPhotoService photoService;

    public WorkerCenterController(CenterService centerService,
                                  UserService userService,
                                  TreatmentService treatmentService,
                                  AuthenticationSessionService authSession,
                                  GoogleCalendarService googleCalendar,
                                  LoyaltyService loyaltyService,
                                  CenterPhotoService photoService) {
        this.loyaltyService = loyaltyService;
        this.photoService = photoService;
        this.centerService = centerService;
        this.userService = userService;
        this.treatmentService = treatmentService;
        this.authSession = authSession;
        this.googleCalendar = googleCalendar;
    }

    @GetMapping("/worker/center")
    public String view(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        Long centerId = worker.getCenterId();
        model.addAttribute("center", centerService.getCenterById(centerId));
        model.addAttribute("worker", userService.getUser(worker.getId()));
        model.addAttribute("treatmentsCount", treatmentService.countByCenter(centerId));
        model.addAttribute("workersCount", userService.countWorkersByCenter(centerId));
        model.addAttribute("timeZones", CenterTimeZones.OPTIONS);
        model.addAttribute("loyaltyModes", LoyaltyMode.values());
        model.addAttribute("calendarSync", googleCalendar.status(worker.getId()));
        model.addAttribute("maxPhotoMb", CenterPhotoService.MAX_UPLOAD_MB);
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

    @PostMapping("/worker/center/loyalty")
    public String updateLoyalty(@AuthenticationPrincipal AppUserDetails worker,
                                @ModelAttribute LoyaltySettingsForm form,
                                RedirectAttributes redirect) {
        // El aviso va dentro de la propia tarjeta: al volver, la página baja a #puntos y no se vería el de arriba
        boolean wasEnabled = centerService.getCenterById(worker.getCenterId()).isLoyaltyEnabled();
        try {
            Center center = loyaltyService.updateSettings(worker.getCenterId(), form);
            String message;
            if (!center.isLoyaltyEnabled()) {
                message = "Tarjeta de puntos desactivada. Los puntos ya ganados se conservan.";
            } else if (wasEnabled) {
                message = "Tarjeta de puntos guardada. Los cambios cuentan desde ahora.";
            } else {
                message = "Tarjeta de puntos activada. Tus clientes suman puntos desde hoy.";
            }
            redirect.addFlashAttribute("loyaltyMessage", message);
        } catch (BusinessRuleException e) {
            // Se devuelve lo escrito para no perderlo
            redirect.addFlashAttribute("loyaltyError", e.getMessage());
            redirect.addFlashAttribute("loyaltyForm", form);
        }
        return "redirect:/worker/center#puntos";
    }

    @PostMapping("/worker/center/bookings")
    public String updateBookings(@AuthenticationPrincipal AppUserDetails worker,
                                 @RequestParam boolean autoAccept,
                                 RedirectAttributes redirect) {
        centerService.setAutoAcceptBookings(worker.getCenterId(), autoAccept);
        redirect.addFlashAttribute("bookingsMessage", autoAccept
                ? "Activado: las citas que pidan tus clientes en un hueco libre quedarán confirmadas al momento."
                : "Desactivado: las nuevas solicitudes esperarán a que alguien del equipo las acepte.");
        return "redirect:/worker/center#reservas";
    }

    @PostMapping("/worker/center/photo")
    public String updatePhoto(@AuthenticationPrincipal AppUserDetails worker,
                              @RequestParam(required = false) MultipartFile photo,
                              RedirectAttributes redirect) {
        try {
            var prepared = photoService.prepare(photo)
                    .orElseThrow(() -> new BusinessRuleException("Elige una foto para subirla."));
            photoService.save(worker.getCenterId(), prepared);
            redirect.addFlashAttribute("photoMessage", "Foto guardada.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("photoError", e.getMessage());
        }
        return "redirect:/worker/center#foto";
    }

    @PostMapping("/worker/center/photo/delete")
    public String removePhoto(@AuthenticationPrincipal AppUserDetails worker, RedirectAttributes redirect) {
        photoService.remove(worker.getCenterId());
        redirect.addFlashAttribute("photoMessage", "Foto quitada.");
        return "redirect:/worker/center#foto";
    }
}
