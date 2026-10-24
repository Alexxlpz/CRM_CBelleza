package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AgendaBlockService;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.OpeningHoursService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import com.alexxlpz.crm_cbelleza.services.WorkerHoursService;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.dto.AgendaBlockDTO;
import com.alexxlpz.crm_cbelleza.entities.Center;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final OpeningHoursService openingHours;
    private final WorkerHoursService workerHours;

    public WorkerCalendarController(AppointmentService appointmentService,
                                    AgendaBlockService blockService,
                                    TreatmentService treatmentService,
                                    UserService userService,
                                    CenterService centerService,
                                    OpeningHoursService openingHours,
                                    WorkerHoursService workerHours) {
        this.workerHours = workerHours;
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
        Center center = centerService.getCenterById(centerId);
        model.addAttribute("openingHours", openingHours(centerId, center.getCabins()));
        List<User> team = userService.getWorkersByCenter(centerId);
        model.addAttribute("calendarStaff", Map.of(
                "shifts", shifts(team, workerHours.forCenter(centerId)),
                "currentWorkerId", worker.getId(),
                // Solo el dueño cierra el centro o bloquea la agenda de otros (worker-agenda-forms.js)
                "isOwner", center.isOwnedBy(worker.getId()),
                "workers", team.stream()
                        .map(w -> Map.of("id", w.getId(), "name", w.getName()))
                        .toList(),
                "treatments", treatmentService.getTreatmentsByCenter(centerId).stream()
                        .map(t -> Map.of("id", t.getId(), "name", t.getName(), "duration", t.getDuration(), "price", t.getPrice()))
                        .toList()));
        return "worker/calendar";
    }

    /** Quien tiene horario propio: su nombre y, por día (1 = lunes), sus horas en texto; sin entrada, no trabaja. */
    private static List<Map<String, Object>> shifts(List<User> team, Map<Long, OpeningHours> own) {
        return team.stream()
                .filter(w -> own.containsKey(w.getId()))
                .map(w -> {
                    Map<String, String> days = new LinkedHashMap<>();
                    for (DayOfWeek day : DayOfWeek.values()) {
                        String hours = own.get(w.getId()).hoursOf(day);
                        if (hours != null) {
                            days.put(String.valueOf(day.getValue()), hours);
                        }
                    }
                    return Map.<String, Object>of("name", w.getName(), "days", days);
                })
                .toList();
    }

    /** Horario del centro para la rejilla (días ISO: 1 = lunes ... 7 = domingo, con sus tramos) y cabinas. */
    private Map<String, Object> openingHours(Long centerId, Integer cabins) {
        Map<String, Object> hours = new LinkedHashMap<>(openingHours.forCenter(centerId).toJson());
        hours.put("cabins", cabins != null ? cabins : 1);
        return hours;
    }
}
