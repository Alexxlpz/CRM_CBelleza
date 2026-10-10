package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.booking.OpeningHoursPolicy;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Agenda del centro (vista mensual y semanal). Las acciones sobre citas están en
 * {@link WorkerAppointmentApiController}.
 */
@Controller
public class WorkerCalendarController {

    private final AppointmentService appointmentService;
    private final OpeningHoursPolicy openingHours;

    public WorkerCalendarController(AppointmentService appointmentService, OpeningHoursPolicy openingHours) {
        this.appointmentService = appointmentService;
        this.openingHours = openingHours;
    }

    @GetMapping("/worker/calendar")
    public String calendar(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        model.addAttribute("appointments", appointmentService.getCalendarAppointments(worker.getCenterId()));
        // Horario de reservas para la rejilla de la vista semanal (días ISO: 1 = lunes ... 7 = domingo)
        model.addAttribute("openingHours", Map.of(
                "openHour", openingHours.openHour(),
                "closeHour", openingHours.closeHour(),
                "workingDays", openingHours.workingDays().stream().map(DayOfWeek::getValue).sorted().toList()));
        return "worker/calendar";
    }
}
