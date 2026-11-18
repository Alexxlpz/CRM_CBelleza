package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AgendaBlockService;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.OpeningHoursService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import com.alexxlpz.crm_cbelleza.services.WorkerHoursService;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.dto.AgendaBlockDTO;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    private final ClientCardService clientCardService;
    private final ObjectMapper json;

    public WorkerCalendarController(AppointmentService appointmentService,
                                    AgendaBlockService blockService,
                                    TreatmentService treatmentService,
                                    UserService userService,
                                    CenterService centerService,
                                    OpeningHoursService openingHours,
                                    WorkerHoursService workerHours,
                                    ClientCardService clientCardService,
                                    ObjectMapper json) {
        this.json = json;
        this.workerHours = workerHours;
        this.clientCardService = clientCardService;
        this.appointmentService = appointmentService;
        this.blockService = blockService;
        this.treatmentService = treatmentService;
        this.userService = userService;
        this.centerService = centerService;
        this.openingHours = openingHours;
    }

    @GetMapping("/worker/calendar")
    public String calendar(@AuthenticationPrincipal AppUserDetails worker,
                           @RequestParam(name = "nuevaCita", required = false) Long newAppointmentClientId,
                           Model model) {
        Long centerId = worker.getCenterId();
        Center center = centerService.getCenterById(centerId);
        List<User> team = userService.getWorkersByCenter(centerId);
        // Todo lo que pinta la agenda va en un bloque JSON (calendar.html, lo lee js/pages/calendar-data.js)
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appointments", appointmentService.getCalendarAppointments(centerId));
        data.put("hours", openingHours(centerId, center.getCabins()));
        data.put("blocks", blockService.upcoming(centerId).stream().map(AgendaBlockDTO::of).toList());
        data.put("staff", Map.of(
                "shifts", shifts(team, workerHours.forCenter(centerId)),
                "currentWorkerId", worker.getId(),
                // Solo el dueño cierra el centro o bloquea la agenda de otros (worker-agenda-forms.js)
                "isOwner", center.isOwnedBy(worker.getId()),
                "workers", team.stream()
                        .map(w -> Map.of("id", w.getId(), "name", w.getName()))
                        .toList(),
                "treatments", treatmentService.getVisibleTreatmentsByCenter(centerId).stream()
                        .map(t -> Map.of("id", t.getId(), "name", t.getName(), "duration", t.getDuration(), "price", t.getPrice()))
                        .toList()));
        data.put("newFor", newAppointmentClient(centerId, newAppointmentClientId));
        model.addAttribute("calendarData", toJson(data));
        return "worker/calendar";
    }

    /** «<» escapado: un «</script>» dentro de un nombre o una nota no puede cerrar el bloque antes de tiempo. */
    private String toJson(Object data) {
        try {
            return json.writeValueAsString(data).replace("<", "\\u003c");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se han podido preparar los datos de la agenda.", e);
        }
    }

    /**
     * «Darle cita» desde la ficha: el cliente que «Nueva cita» abre ya elegido (id, nombre y teléfono, como el buscador
     * de clientes). Solo si es de la cartera del centro y no ha borrado su cuenta; si no, la agenda se abre sin más.
     */
    private Map<String, Object> newAppointmentClient(Long centerId, Long clientId) {
        if (clientId == null) {
            return null;
        }
        try {
            User client = clientCardService.requireClientOfCenter(centerId, clientId);
            if (client.isDeleted()) {
                return null;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", client.getId());
            data.put("name", client.getName());
            data.put("phone", client.getPhone() != null ? client.getPhone() : "");
            return data;
        } catch (ResourceNotFoundException e) {
            return null;
        }
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
