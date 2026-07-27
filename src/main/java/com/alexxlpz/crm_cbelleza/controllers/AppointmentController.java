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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final CenterService centerService;
    private final NotificationService notificationService;
    private final TreatmentService treatmentService;
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
    public String listCenters(@RequestParam(value = "searchLocation", required = false) String searchLocation,
                              Model model, HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if (role == null) {
            return "redirect:/home";
        }

        List<Center> centers = centerService.getCentersFilteredByLocation(searchLocation);
        if (searchLocation != null && !searchLocation.trim().isEmpty()) {
            model.addAttribute("currentSearchLocation", searchLocation);
        }

        model.addAttribute("centers", centers);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));

        return "client/centers";
    }

    // Client View: Center Details and Booking Trigger
    @GetMapping("/client/centers/{id}")
    public String centerDetails(@PathVariable("id") Long id, Model model, HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if (role == null) {
            return "redirect:/home";
        }

        Center center = centerService.getCenterById(id);
        List<Treatment> treatments = treatmentService.getTreatmentsByCenter(id);
        List<User> workers = userService.getWorkersByCenter(id);

        model.addAttribute("center", center);
        model.addAttribute("treatments", treatments);
        model.addAttribute("workers", workers);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));

        return "client/center_details";
    }

    // Client View: Create Booking
    @PostMapping("/client/booking")
    public String createBooking(@RequestParam("centerId") Long centerId,
                                @RequestParam("treatmentId") Long treatmentId,
                                @RequestParam("dateTime") String dateTimeStr,
                                @RequestParam(value = "guestName", required = false) String guestName,
                                @RequestParam(value = "guestPhone", required = false) String guestPhone,
                                HttpSession session) {

        Long clientUserId = (Long) session.getAttribute("sessionUserId");
        if (clientUserId == null && guestPhone != null) {
            session.setAttribute("sessionGuestPhone", guestPhone);
        }

        try {
            appointmentService.createBooking(centerId, treatmentId, java.time.LocalDateTime.parse(dateTimeStr), clientUserId, guestName, guestPhone);
        } catch (IllegalArgumentException e) {
            return "redirect:/client/centers/" + centerId + "?error=invalid_time";
        }

        return "redirect:/client/appointments";
    }

    // Client View: List Client Appointments
    @GetMapping("/client/appointments")
    public String clientAppointments(@RequestParam(value = "searchPhone", required = false) String searchPhone,
                                     Model model, HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if (role == null) {
            return "redirect:/home";
        }

        Long clientUserId = (Long) session.getAttribute("sessionUserId");
        String phone = searchPhone != null ? searchPhone : (String) session.getAttribute("sessionGuestPhone");

        List<Appointment> appointments = appointmentService.getClientAppointments(clientUserId, phone);
        if (clientUserId == null && phone != null && !phone.trim().isEmpty()) {
            model.addAttribute("currentSearchPhone", phone);
        }

        model.addAttribute("appointments", appointments);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));

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

        List<Appointment> appointments = appointmentService.getAppointmentsByCenter(centerId);
        
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
