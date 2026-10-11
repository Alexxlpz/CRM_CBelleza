package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.dto.AgendaBlockDTO;
import com.alexxlpz.crm_cbelleza.dto.AppointmentCalendarDTO;
import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.forms.WorkerAppointmentForm;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AgendaBlockService;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Acciones de la agenda (llamadas fetch): responder solicitudes, crear, mover y anular citas, proponer
 * otras horas y bloquear la agenda. Todo usa el centro de la sesión: un recurso de otro centro responde 404.
 * Si el hueco no está libre responde 409 con el motivo (y {@code code: OUTSIDE_HOURS} si solo falla el horario).
 */
@RestController
@ApiEndpoint
public class WorkerAppointmentApiController {

    private final BookingService bookingService;
    private final AppointmentService appointmentService;
    private final AgendaBlockService blockService;
    private final ClientCardService clientCardService;

    public WorkerAppointmentApiController(BookingService bookingService,
                                          AppointmentService appointmentService,
                                          AgendaBlockService blockService,
                                          ClientCardService clientCardService) {
        this.bookingService = bookingService;
        this.appointmentService = appointmentService;
        this.blockService = blockService;
        this.clientCardService = clientCardService;
    }

    /** Citas y bloqueos actuales: la agenda se recarga con esto tras cada cambio. */
    @GetMapping("/worker/calendar/data")
    public Map<String, Object> data(@AuthenticationPrincipal AppUserDetails worker) {
        return Map.of(
                "appointments", appointmentService.getCalendarAppointments(worker.getCenterId()),
                "blocks", blockService.upcoming(worker.getCenterId()).stream().map(AgendaBlockDTO::of).toList());
    }

    /** Buscador de clientes de la cartera del centro (modal «Nueva cita»). */
    @GetMapping("/worker/clients/search")
    public List<Map<String, Object>> searchClients(@AuthenticationPrincipal AppUserDetails worker,
                                                   @RequestParam(defaultValue = "") String q) {
        return clientCardService.getClientsForCenter(worker.getCenterId(), q).stream()
                .limit(12)
                .map(c -> Map.<String, Object>of("id", c.getId(), "name", c.getName(), "phone", c.getPhone()))
                .toList();
    }

    @PostMapping("/worker/appointments")
    public AppointmentCalendarDTO create(@AuthenticationPrincipal AppUserDetails worker,
                                         @ModelAttribute WorkerAppointmentForm form) {
        Long id = bookingService.createByWorker(worker.getCenterId(), worker.getId(), form).getId();
        return appointmentService.getCalendarAppointment(worker.getCenterId(), id);
    }

    @PostMapping("/worker/appointments/{id}/approve")
    public AppointmentCalendarDTO approve(@AuthenticationPrincipal AppUserDetails worker,
                                          @PathVariable Long id,
                                          @RequestParam(required = false) Long workerId,
                                          @RequestParam(required = false) String message) {
        bookingService.approve(worker.getCenterId(), id, worker.getId(), workerId, message);
        return appointmentService.getCalendarAppointment(worker.getCenterId(), id);
    }

    @PostMapping("/worker/appointments/{id}/reject")
    public AppointmentCalendarDTO reject(@AuthenticationPrincipal AppUserDetails worker,
                                         @PathVariable Long id,
                                         @RequestParam(required = false) String message) {
        bookingService.reject(worker.getCenterId(), id, message);
        return appointmentService.getCalendarAppointment(worker.getCenterId(), id);
    }

    @PostMapping("/worker/appointments/{id}/propose")
    public AppointmentCalendarDTO propose(@AuthenticationPrincipal AppUserDetails worker,
                                          @PathVariable Long id,
                                          @RequestParam(name = "dateTime", required = false) List<String> dateTimes,
                                          @RequestParam(required = false) String message) {
        bookingService.propose(worker.getCenterId(), id, worker.getId(), dateTimes, message);
        return appointmentService.getCalendarAppointment(worker.getCenterId(), id);
    }

    @PostMapping("/worker/appointments/{id}/withdraw-proposal")
    public AppointmentCalendarDTO withdrawProposal(@AuthenticationPrincipal AppUserDetails worker, @PathVariable Long id) {
        bookingService.withdrawProposal(worker.getCenterId(), id);
        return appointmentService.getCalendarAppointment(worker.getCenterId(), id);
    }

    @PostMapping("/worker/appointments/{id}/reschedule")
    public AppointmentCalendarDTO reschedule(@AuthenticationPrincipal AppUserDetails worker,
                                             @PathVariable Long id,
                                             @RequestParam String dateTime,
                                             @RequestParam(required = false) Long workerId,
                                             @RequestParam(defaultValue = "false") boolean allowOutsideHours) {
        bookingService.reschedule(worker.getCenterId(), id, dateTime, workerId, allowOutsideHours);
        return appointmentService.getCalendarAppointment(worker.getCenterId(), id);
    }

    @PostMapping("/worker/appointments/{id}/cancel")
    public AppointmentCalendarDTO cancel(@AuthenticationPrincipal AppUserDetails worker,
                                         @PathVariable Long id,
                                         @RequestParam(required = false) String message) {
        bookingService.cancelByCenter(worker.getCenterId(), id, message);
        return appointmentService.getCalendarAppointment(worker.getCenterId(), id);
    }

    @PostMapping("/worker/agenda-blocks")
    public AgendaBlockDTO createBlock(@AuthenticationPrincipal AppUserDetails worker,
                                      @RequestParam(required = false) Long workerId,
                                      @RequestParam(defaultValue = "false") boolean wholeCenter,
                                      @RequestParam String startsAt,
                                      @RequestParam String endsAt,
                                      @RequestParam(required = false) String reason) {
        return AgendaBlockDTO.of(blockService.create(worker.getCenterId(), worker.getId(), workerId, wholeCenter, startsAt, endsAt, reason));
    }

    @PostMapping("/worker/agenda-blocks/{id}/delete")
    public Map<String, String> deleteBlock(@AuthenticationPrincipal AppUserDetails worker, @PathVariable Long id) {
        blockService.delete(worker.getCenterId(), id);
        return Map.of("status", "DELETED");
    }
}
