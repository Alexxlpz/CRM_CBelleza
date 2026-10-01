package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.NotificationService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final UserService userService;
    private final CenterService centerService;
    private final AppointmentService appointmentService;
    private final NotificationService notificationService;
    private final TreatmentService treatmentService;

    public ProfileController(UserService userService,
                             CenterService centerService,
                             AppointmentService appointmentService,
                             NotificationService notificationService,
                             TreatmentService treatmentService) {
        this.userService = userService;
        this.centerService = centerService;
        this.appointmentService = appointmentService;
        this.notificationService = notificationService;
        this.treatmentService = treatmentService;
    }

    // Smart redirect route
    @GetMapping("/profile")
    public String smartProfileRedirect(HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if ("CLIENT".equals(role)) {
            return "redirect:/client/profile";
        } else if ("WORKER".equals(role)) {
            return "redirect:/worker/center";
        }
        return "redirect:/login";
    }

    // Client Profile View
    @GetMapping("/client/profile")
    public String clientProfileView(HttpSession session, Model model) {
        String role = (String) session.getAttribute("sessionRole");
        Long userId = (Long) session.getAttribute("sessionUserId");

        if (!"CLIENT".equals(role) || userId == null) {
            return "redirect:/login";
        }

        User user = userService.getUserById(userId);
        if (user == null) {
            return "redirect:/login";
        }

        int appointmentsCount = appointmentService.getClientAppointments(userId, null).size();

        model.addAttribute("client", user);
        model.addAttribute("appointmentsCount", appointmentsCount);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", user.getName());
        model.addAttribute("activePage", "profile");

        return "client/profile";
    }

    // Client Profile Update
    @PostMapping("/client/profile")
    public String updateClientProfile(@RequestParam("name") String name,
                                      @RequestParam(value = "phone", required = false) String phone,
                                      @RequestParam(value = "newPassword", required = false) String newPassword,
                                      @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {

        String role = (String) session.getAttribute("sessionRole");
        Long userId = (Long) session.getAttribute("sessionUserId");

        if (!"CLIENT".equals(role) || userId == null) {
            return "redirect:/login";
        }

        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "El nombre no puede estar vacío.");
            return "redirect:/client/profile";
        }

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            if (newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("errorMessage", "La nueva contraseña debe tener al menos 6 caracteres.");
                return "redirect:/client/profile";
            }
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Las contraseñas no coinciden.");
                return "redirect:/client/profile";
            }
        }

        User updatedUser = userService.updateUserProfile(userId, name, phone, newPassword);
        if (updatedUser != null) {
            session.setAttribute("sessionUserName", updatedUser.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Tus datos personales se han actualizado correctamente.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "No se pudieron actualizar los datos.");
        }

        return "redirect:/client/profile";
    }

    // Worker Center View (Mi Centro)
    @GetMapping("/worker/center")
    public String workerCenterView(HttpSession session, Model model) {
        String role = (String) session.getAttribute("sessionRole");
        Long userId = (Long) session.getAttribute("sessionUserId");
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        if (!"WORKER".equals(role) || userId == null) {
            return "redirect:/login";
        }

        User worker = userService.getUserById(userId);
        if (worker == null) {
            return "redirect:/login";
        }

        Center center = null;
        if (centerId != null) {
            center = centerService.getCenterById(centerId);
        } else if (worker.getCenter() != null) {
            center = worker.getCenter();
        }

        if (center != null) {
            model.addAttribute("center", center);
            model.addAttribute("treatmentsCount", treatmentService.getTreatmentsByCenter(center.getId()).size());
            model.addAttribute("workersCount", userService.getWorkersByCenter(center.getId()).size());
        }

        model.addAttribute("worker", worker);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", worker.getName());
        if (center != null) {
            model.addAttribute("sessionCenterId", center.getId());
            model.addAttribute("sessionCenterName", center.getName());
            model.addAttribute("notifications", notificationService.getNotificationsForCenter(center.getId()));
        }
        model.addAttribute("activePage", "center");

        return "worker/center";
    }

    // Worker Center Update (Mi Centro)
    @PostMapping("/worker/center")
    public String updateWorkerCenter(@RequestParam(value = "centerName", required = false) String centerName,
                                     @RequestParam(value = "centerAddress", required = false) String centerAddress,
                                     @RequestParam(value = "centerPhone", required = false) String centerPhone,
                                     @RequestParam(value = "centerEmail", required = false) String centerEmail,
                                     @RequestParam(value = "latitude", required = false) Double latitude,
                                     @RequestParam(value = "longitude", required = false) Double longitude,
                                     @RequestParam(value = "workerName", required = false) String workerName,
                                     @RequestParam(value = "workerPhone", required = false) String workerPhone,
                                     @RequestParam(value = "newPassword", required = false) String newPassword,
                                     @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {

        String role = (String) session.getAttribute("sessionRole");
        Long userId = (Long) session.getAttribute("sessionUserId");
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        if (!"WORKER".equals(role) || userId == null) {
            return "redirect:/login";
        }

        // Validate password if supplied
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            if (newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("errorMessage", "La nueva contraseña debe tener al menos 6 caracteres.");
                return "redirect:/worker/center";
            }
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Las contraseñas no coinciden.");
                return "redirect:/worker/center";
            }
        }

        // Update Worker Personal Profile
        if (workerName != null && !workerName.trim().isEmpty()) {
            User updatedWorker = userService.updateUserProfile(userId, workerName, workerPhone, newPassword);
            if (updatedWorker != null) {
                session.setAttribute("sessionUserName", updatedWorker.getName());
            }
        }

        // Update Center Information if centerId is present
        if (centerId != null) {
            Center updatedCenter = centerService.updateCenter(centerId, centerName, centerAddress, centerPhone, centerEmail, latitude, longitude);
            if (updatedCenter != null) {
                session.setAttribute("sessionCenterName", updatedCenter.getName());
            }
        }

        redirectAttributes.addFlashAttribute("successMessage", "Los datos de tu centro y tu cuenta se han actualizado correctamente.");
        return "redirect:/worker/center";
    }
}
