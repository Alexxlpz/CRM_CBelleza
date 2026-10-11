package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.booking.OpeningHoursPolicy;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AgendaBlockService;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import com.alexxlpz.crm_cbelleza.dto.AgendaBlockDTO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agenda del centro (vista mensual y semanal). Las acciones sobre citas y bloqueos están en
 * {@link WorkerAppointmentApiController}.
 */
@Controller
public class WorkerCalendarController {

    private final AppointmentService appointmentService;
    private final AgendaBlockService blockService;
    private final TreatmentService treatmentService;
    private final UserService userService;
    private final CenterService centerService;
    private final OpeningHoursPolicy openingHours;

    public WorkerCalendarController(AppointmentService appointmentService,
                                    AgendaBlockService blockService,
                                    TreatmentService treatmentService,
                                    UserService userService,
                                    CenterService centerService,
                                    OpeningHoursPolicy openingHours) {
        this.appointmentService = appointmentService;
        this.blockService = blockService;
        this.treatmentService = treatmentService;
        this.userService = userService;
        this.centerService = centerService;
        this.openingHours = openingHours;
    }

    @GetMapping("/worker/calendar")
    public String calendar(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        Long centerId = worker.getCenterId();
        model.addAttribute("appointments", appointmentService.getCalendarAppointments(centerId));
        model.addAttribute("blocks", blockService.upcoming(centerId).stream().map(AgendaBlockDTO::of).toList());
        model.addAttribute("openingHours", openingHours(centerService.getCenterById(centerId).getCabins()));
        model.addAttribute("calendarStaff", Map.of(
                "currentWorkerId", worker.getId(),
                "workers", userService.getWorkersByCenter(centerId).stream()
                        .map(w -> Map.of("id", w.getId(), "name", w.getName()))
                        .toList(),
                "treatments", treatmentService.getTreatmentsByCenter(centerId).stream()
                        .map(t -> Map.of("id", t.getId(), "name", t.getName(), "duration", t.getDuration(), "price", t.getPrice()))
                        .toList()));
        return "worker/calendar";
    }

    /** Horario de reservas para la rejilla (días ISO: 1 = lunes ... 7 = domingo) y cabinas del centro. */
    private Map<String, Object> openingHours(Integer cabins) {
        Map<String, Object> hours = new LinkedHashMap<>();
        hours.put("openHour", openingHours.openHour());
        hours.put("closeHour", openingHours.closeHour());
        hours.put("slotMinutes", openingHours.slotMinutes());
        hours.put("workingDays", openingHours.workingDays().stream().map(DayOfWeek::getValue).sorted().toList());
        hours.put("cabins", cabins != null ? cabins : 1);
        return hours;
    }
}
