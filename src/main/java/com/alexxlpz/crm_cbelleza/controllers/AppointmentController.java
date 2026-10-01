package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.NotificationService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import com.alexxlpz.crm_cbelleza.dto.AppointmentCalendarDTO;

@Controller
public class AppointmentController {

    @Autowired
    private final AppointmentService appointmentService;
    @Autowired
    private final CenterService centerService;
    @Autowired
    private final NotificationService notificationService;
    @Autowired
    private final TreatmentService treatmentService;
    @Autowired
    private final UserService userService;

    public AppointmentController(AppointmentService appointmentService,
                                 CenterService centerService,
                                 NotificationService notificationService,
                                 TreatmentService treatmentService,
                                 UserService userService) {
        this.appointmentService = appointmentService;
        this.centerService = centerService;
        this.notificationService = notificationService;
        this.treatmentService = treatmentService;
        this.userService = userService;
    }

    // Client View: List Centers with location filter
    @GetMapping("/client/centers")
    public String listCenters(@RequestParam(value = "searchLocation", required = false) String searchLocation) {
        if (searchLocation != null && !searchLocation.trim().isEmpty()) {
            return "redirect:/centers?searchLocation=" + searchLocation;
        }
        return "redirect:/centers";
    }

    // Client View: Center Details and Booking Trigger
    @GetMapping("/client/centers/{id}")
    public String centerDetails(@PathVariable("id") Long id, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        String role = (String) session.getAttribute("sessionRole");
        Long clientUserId = (Long) session.getAttribute("sessionUserId");

        if ("WORKER".equals(role)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Has iniciado sesión como trabajador. Solo los clientes pueden acceder a los centros.");
            return "redirect:/centers";
        }

        if (!"CLIENT".equals(role) || clientUserId == null) {
            return "redirect:/login";
        }

        Center center = centerService.getCenterById(id);
        List<Treatment> treatments = treatmentService.getTreatmentsByCenter(id);
        List<User> workers = userService.getWorkersByCenter(id);

        model.addAttribute("center", center);
        model.addAttribute("treatments", treatments);
        model.addAttribute("workers", workers);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("activePage", "centers");

        return "client/center_details";
    }

    // Client View: Create Booking
    @PostMapping("/client/booking")
    public String createBooking(@RequestParam("centerId") Long centerId,
                                @RequestParam("treatmentId") Long treatmentId,
                                @RequestParam("dateTime") String dateTimeStr,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {

        String role = (String) session.getAttribute("sessionRole");
        Long clientUserId = (Long) session.getAttribute("sessionUserId");

        if ("WORKER".equals(role)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Has iniciado sesión como trabajador. Solo los clientes pueden reservar citas.");
            return "redirect:/centers";
        }

        if (!"CLIENT".equals(role) || clientUserId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Debes iniciar sesión con tu cuenta de cliente para reservar una cita.");
            return "redirect:/login";
        }

        try {
            appointmentService.createBooking(centerId, treatmentId, java.time.LocalDateTime.parse(dateTimeStr), clientUserId, null, null);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("bookingError",
                    e.getMessage() != null ? e.getMessage() : "No se ha podido crear la reserva.");
            return "redirect:/client/centers/" + centerId;
        }

        return "redirect:/client/appointments";
    }

    // Client View: List Client Appointments
    @GetMapping("/client/appointments")
    public String clientAppointments(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        String role = (String) session.getAttribute("sessionRole");
        Long clientUserId = (Long) session.getAttribute("sessionUserId");

        if ("WORKER".equals(role)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Has iniciado sesión como trabajador. Solo los clientes pueden acceder al historial de reservas.");
            return "redirect:/worker/dashboard";
        }

        if (!"CLIENT".equals(role) || clientUserId == null) {
            return "redirect:/login";
        }

        List<Appointment> appointments = appointmentService.getClientAppointments(clientUserId, null);

        model.addAttribute("appointments", appointments);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("activePage", "appointments");

        return "client/appointments";
    }

    // Worker View: Agenda Calendar
    @GetMapping("/worker/calendar")
    public String workerCalendar(Model model, HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        if (!"WORKER".equals(role) || centerId == null) {
            return "redirect:/home";
        }

        List<AppointmentCalendarDTO> appointments = appointmentService.getCalendarAppointmentsByCenter(centerId);
        
        model.addAttribute("appointments", appointments);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("sessionCenterName", session.getAttribute("sessionCenterName"));
        
        model.addAttribute("notifications", notificationService.getNotificationsForCenter(centerId));

        return "worker/calendar";
    }

    // Worker Action: Approve
    @PostMapping("/worker/appointments/{id}/approve")
    public String approveAppointment(@PathVariable("id") Long id,
                                     @RequestParam(value = "message", required = false) String message,
                                     @RequestParam(value = "selectedDate", required = false) String selectedDate,
                                     HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if (!"WORKER".equals(role)) {
            return "redirect:/home";
        }

        Long workerId = (Long) session.getAttribute("sessionUserId");
        appointmentService.approveAppointment(id, message, workerId);

        String redirectUrl = "redirect:/worker/calendar";
        if (selectedDate != null && !selectedDate.trim().isEmpty()) {
            redirectUrl += "?date=" + selectedDate;
        }
        return redirectUrl;
    }

    // Worker Action: Reject
    @PostMapping("/worker/appointments/{id}/reject")
    public String rejectAppointment(@PathVariable("id") Long id,
                                    @RequestParam(value = "message", required = false) String message,
                                    @RequestParam(value = "selectedDate", required = false) String selectedDate,
                                    HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if (!"WORKER".equals(role)) {
            return "redirect:/home";
        }

        appointmentService.rejectAppointment(id, message);

        String redirectUrl = "redirect:/worker/calendar";
        if (selectedDate != null && !selectedDate.trim().isEmpty()) {
            redirectUrl += "?date=" + selectedDate;
        }
        return redirectUrl;
    }
}
