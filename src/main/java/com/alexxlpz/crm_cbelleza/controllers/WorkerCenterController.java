package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.config.SiteModelAdvice;
import com.alexxlpz.crm_cbelleza.mail.CenterOnboardingMailService;
import com.alexxlpz.crm_cbelleza.security.CenterOwnerOnly;
import com.alexxlpz.crm_cbelleza.booking.CenterTimeZones;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.forms.LoyaltySettingsForm;
import com.alexxlpz.crm_cbelleza.forms.OpeningHoursForm;
import com.alexxlpz.crm_cbelleza.services.CenterAccess;
import com.alexxlpz.crm_cbelleza.services.OpeningHoursService;
import com.alexxlpz.crm_cbelleza.services.TeamService;
import com.alexxlpz.crm_cbelleza.services.TeamService.Invitee;
import com.alexxlpz.crm_cbelleza.services.LoyaltyService;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.WorkerCenterForm;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleCalendarService;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.AuthenticationSessionService;
import com.alexxlpz.crm_cbelleza.services.CenterPhotoService;
import com.alexxlpz.crm_cbelleza.services.WorkerPhotoService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import com.alexxlpz.crm_cbelleza.services.WorkerHoursService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "Mi centro": datos públicos del centro, horario, equipo y configuración (solo el dueño los cambia; el resto del
 * equipo los ve) y la cuenta del profesional (cada uno la suya).
 */
@Controller
public class WorkerCenterController {

    private final CenterService centerService;
    private final UserService userService;
    private final TreatmentService treatmentService;
    private final AuthenticationSessionService authSession;
    private final GoogleCalendarService googleCalendar;
    private final LoyaltyService loyaltyService;
    private final CenterPhotoService photoService;
    private final WorkerPhotoService workerPhotos;
    private final OpeningHoursService openingHours;
    private final CenterAccess access;
    private final TeamService team;
    private final CenterOnboardingMailService onboardingMail;
    private final SiteModelAdvice site;
    private final WorkerHoursService workerHours;

    public WorkerCenterController(CenterService centerService,
                                  UserService userService,
                                  TreatmentService treatmentService,
                                  AuthenticationSessionService authSession,
                                  GoogleCalendarService googleCalendar,
                                  LoyaltyService loyaltyService,
                                  CenterPhotoService photoService,
                                  WorkerPhotoService workerPhotos,
                                  OpeningHoursService openingHours,
                                  CenterAccess access,
                                  TeamService team,
                                  CenterOnboardingMailService onboardingMail,
                                  SiteModelAdvice site,
                                  WorkerHoursService workerHours) {
        this.workerHours = workerHours;
        this.team = team;
        this.onboardingMail = onboardingMail;
        this.site = site;
        this.openingHours = openingHours;
        this.access = access;
        this.loyaltyService = loyaltyService;
        this.photoService = photoService;
        this.workerPhotos = workerPhotos;
        this.centerService = centerService;
        this.userService = userService;
        this.treatmentService = treatmentService;
        this.authSession = authSession;
        this.googleCalendar = googleCalendar;
    }

    @GetMapping("/worker/center")
    public String view(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        Long centerId = worker.getCenterId();
        Center center = centerService.getCenterById(centerId);
        OpeningHours hours = openingHours.forCenter(centerId);
        boolean owner = center.isOwnedBy(worker.getId());
        model.addAttribute("center", center);
        model.addAttribute("isOwner", owner);
        model.addAttribute("ownerName", access.ownerName(centerId));
        model.addAttribute("openingHoursLines", hours.describe());
        if (!model.containsAttribute("hourRows")) {
            model.addAttribute("hourRows", openingHours.rows(hours));
        }
        model.addAttribute("hourOptions", openingHours.timeOptions());
        model.addAttribute("teamMembers", team.members(centerId));
        // Horario propio de quien lo tiene («Lunes a viernes: 09:00 - 14:00»); el resto trabaja todo el del centro
        Map<Long, List<String>> teamHours = new HashMap<>();
        workerHours.forCenter(centerId).forEach((id, ownHours) -> teamHours.put(id, ownHours.describe()));
        model.addAttribute("teamHours", teamHours);
        model.addAttribute("maxTeamSize", TeamService.MAX_TEAM_SIZE);
        if (owner) {
            // Quien puede quedarse con el centro: el resto del equipo con la cuenta ya activada
            model.addAttribute("transferCandidates", userService.getWorkersByCenter(centerId).stream()
                    .filter(w -> !w.getId().equals(worker.getId()) && w.hasAccount())
                    .toList());
        }
        model.addAttribute("worker", userService.getUser(worker.getId()));
        model.addAttribute("shortLink", site.siteUrl() + "/c/" + centerService.shortSlug(center));
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
            boolean owner = access.isOwner(worker.getCenterId(), worker.getId());
            if (!owner && form.hasCenterData()) {
                throw new BusinessRuleException(access.ownerOnlyMessage(worker.getCenterId()));
            }
            User updatedWorker = userService.updateProfile(worker.getId(), form.worker());
            if (owner) {
                centerService.updateCenter(worker.getCenterId(), form.center());
            }
            // Recarga nombre de usuario y de centro mostrados en la navbar.
            authSession.refresh(userService.getUser(updatedWorker.getId()), request, response);
            redirect.addFlashAttribute("successMessage", owner
                    ? "Los datos de tu centro y tu cuenta se han actualizado correctamente."
                    : "Los datos de tu cuenta se han actualizado correctamente.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/worker/center";
    }

    /** Horario de un profesional del equipo: todo el del centro o uno propio (solo el dueño lo cambia). */
    @CenterOwnerOnly
    @GetMapping("/worker/center/team/{workerId}/horario")
    public String workerHoursForm(@AuthenticationPrincipal AppUserDetails worker,
                                  @PathVariable Long workerId, Model model) {
        Long centerId = worker.getCenterId();
        User member = userService.getWorkersByCenter(centerId).stream()
                .filter(w -> w.getId().equals(workerId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Ese profesional no es de tu centro."));
        OpeningHours centerHours = openingHours.forCenter(centerId);
        Optional<OpeningHours> own = workerHours.forWorker(workerId);
        model.addAttribute("member", member);
        model.addAttribute("customHours", model.containsAttribute("customHours") ? model.getAttribute("customHours") : own.isPresent());
        model.addAttribute("centerHoursLines", centerHours.describe());
        if (!model.containsAttribute("hourRows")) {
            model.addAttribute("hourRows", openingHours.rows(own.orElse(centerHours)));
        }
        model.addAttribute("hourOptions", openingHours.timeOptions());
        model.addAttribute("activePage", "center");
        return "worker/worker_hours";
    }

    @CenterOwnerOnly
    @PostMapping("/worker/center/team/{workerId}/horario")
    public String updateWorkerHours(@AuthenticationPrincipal AppUserDetails worker,
                                    @PathVariable Long workerId,
                                    @RequestParam(name = "custom", defaultValue = "false") boolean custom,
                                    @ModelAttribute OpeningHoursForm form,
                                    RedirectAttributes redirect) {
        try {
            workerHours.replace(worker.getCenterId(), workerId, custom, form);
            String name = userService.getUser(workerId).getName();
            redirect.addFlashAttribute("teamMessage", custom
                    ? "Horario de " + name + " guardado. Solo recibirá citas dentro de él."
                    : name + " vuelve a trabajar todo el horario del centro.");
            return "redirect:/worker/center#equipo";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            redirect.addFlashAttribute("customHours", custom);
            redirect.addFlashAttribute("hourRows", openingHours.rows(form));
            return "redirect:/worker/center/team/" + workerId + "/horario";
        }
    }

    @CenterOwnerOnly
    @PostMapping("/worker/center/hours")
    public String updateHours(@AuthenticationPrincipal AppUserDetails worker,
                              @ModelAttribute OpeningHoursForm form,
                              RedirectAttributes redirect) {
        // Igual que la tarjeta de puntos: el aviso va dentro de su tarjeta (#horario)
        try {
            openingHours.replace(worker.getCenterId(), form);
            redirect.addFlashAttribute("hoursMessage", "Horario guardado. Los clientes ya solo pueden reservar dentro de él.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("hoursError", e.getMessage());
            redirect.addFlashAttribute("hourRows", openingHours.rows(form));
        }
        return "redirect:/worker/center#horario";
    }

    @CenterOwnerOnly
    @PostMapping("/worker/center/owner")
    public String transferOwnership(@AuthenticationPrincipal AppUserDetails worker,
                                    @RequestParam(required = false) Long newOwnerId,
                                    RedirectAttributes redirect) {
        try {
            User newOwner = access.transferOwnership(worker.getCenterId(), worker.getId(), newOwnerId);
            redirect.addFlashAttribute("successMessage", newOwner.getName()
                    + " es ahora el dueño del centro. Tú sigues en el equipo como trabajador.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/worker/center";
    }

    @CenterOwnerOnly
    @PostMapping("/worker/center/team")
    public String invite(@AuthenticationPrincipal AppUserDetails worker,
                         @RequestParam(required = false) String name,
                         @RequestParam(required = false) String email,
                         RedirectAttributes redirect) {
        // Los avisos van dentro de la tarjeta del equipo (#equipo)
        try {
            Invitee invitee = team.invite(worker.getCenterId(), worker.getId(), name, email);
            if (invitee.token() != null) {
                onboardingMail.sendInvitationAsync(invitee.name(), invitee.email(), invitee.centerName(), invitee.token(), site.siteUrl());
                redirect.addFlashAttribute("teamMessage", "Invitación enviada a " + invitee.email()
                        + ". " + invitee.name() + " entrará en cuanto cree su contraseña.");
            } else {
                redirect.addFlashAttribute("teamMessage", invitee.name()
                        + " ya tenía cuenta en Cuquora: desde ahora entra en tu centro con su correo y su contraseña.");
            }
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("teamError", e.getMessage());
            redirect.addFlashAttribute("inviteName", name);
            redirect.addFlashAttribute("inviteEmail", email);
        }
        return "redirect:/worker/center#equipo";
    }

    @CenterOwnerOnly
    @PostMapping("/worker/center/team/{workerId}/resend")
    public String resendInvitation(@AuthenticationPrincipal AppUserDetails worker,
                                   @PathVariable Long workerId,
                                   RedirectAttributes redirect) {
        try {
            Invitee invitee = team.resend(worker.getCenterId(), worker.getId(), workerId);
            onboardingMail.sendInvitationAsync(invitee.name(), invitee.email(), invitee.centerName(), invitee.token(), site.siteUrl());
            redirect.addFlashAttribute("teamMessage", "Invitación enviada de nuevo a " + invitee.email() + ". El enlace anterior ya no vale.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("teamError", e.getMessage());
        }
        return "redirect:/worker/center#equipo";
    }

    @CenterOwnerOnly
    @PostMapping("/worker/center/team/{workerId}/remove")
    public String removeFromTeam(@AuthenticationPrincipal AppUserDetails worker,
                                 @PathVariable Long workerId,
                                 RedirectAttributes redirect) {
        try {
            User removed = team.remove(worker.getCenterId(), worker.getId(), workerId);
            redirect.addFlashAttribute("teamMessage", removed.getName() + (removed.hasAccount()
                    ? " ya no está en el equipo. Sus citas pasadas siguen en la agenda y en las fichas."
                    : " ya no está en el equipo y su invitación ha dejado de valer."));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("teamError", e.getMessage());
        }
        return "redirect:/worker/center#equipo";
    }

    @CenterOwnerOnly
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

    @CenterOwnerOnly
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

    @CenterOwnerOnly
    @PostMapping("/worker/center/reschedule")
    public String updateClientReschedule(@AuthenticationPrincipal AppUserDetails worker,
                                         @RequestParam int hours,
                                         RedirectAttributes redirect) {
        centerService.setClientRescheduleHours(worker.getCenterId(), hours);
        redirect.addFlashAttribute("bookingsMessage", hours == 0
                ? "Guardado: los clientes tendrán que llamaros para cambiar la hora de una cita."
                : "Guardado: los clientes podrán cambiar la hora de sus citas hasta " + hours + " h antes.");
        return "redirect:/worker/center#reservas";
    }

    @CenterOwnerOnly
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

    @CenterOwnerOnly
    @PostMapping("/worker/center/photo/delete")
    public String removePhoto(@AuthenticationPrincipal AppUserDetails worker, RedirectAttributes redirect) {
        photoService.remove(worker.getCenterId());
        redirect.addFlashAttribute("photoMessage", "Foto quitada.");
        return "redirect:/worker/center#foto";
    }

    /** Foto de perfil: cada profesional la suya (no hace falta ser el dueño). */
    @PostMapping("/worker/center/my-photo")
    public String updateMyPhoto(@AuthenticationPrincipal AppUserDetails worker,
                                @RequestParam(required = false) MultipartFile photo,
                                RedirectAttributes redirect) {
        try {
            workerPhotos.save(worker.getId(), photo);
            redirect.addFlashAttribute("myPhotoMessage", "Foto guardada: ya la ven tus clientes al reservar.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("myPhotoError", e.getMessage());
        }
        return "redirect:/worker/center#mi-foto";
    }

    @PostMapping("/worker/center/my-photo/delete")
    public String removeMyPhoto(@AuthenticationPrincipal AppUserDetails worker, RedirectAttributes redirect) {
        workerPhotos.remove(worker.getId());
        redirect.addFlashAttribute("myPhotoMessage", "Foto quitada.");
        return "redirect:/worker/center#mi-foto";
    }
}
