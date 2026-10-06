package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Agenda mensual del centro. Las acciones sobre citas están en {@link WorkerAppointmentApiController}. */
@Controller
public class WorkerCalendarController {

    private final AppointmentService appointmentService;

    public WorkerCalendarController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/worker/calendar")
    public String calendar(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        model.addAttribute("appointments", appointmentService.getCalendarAppointments(worker.getCenterId()));
        return "worker/calendar";
    }
}
