package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Aprobar o rechazar solicitudes de cita desde la agenda (llamadas fetch).
 * Solo actúa sobre citas del centro del trabajador; si no, responde 404.
 * Si la franja ya está ocupada responde 409 con el motivo.
 */
@RestController
@ApiEndpoint
@RequestMapping("/worker/appointments/{id}")
public class WorkerAppointmentApiController {

    private final BookingService bookingService;

    public WorkerAppointmentApiController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/approve")
    public Map<String, String> approve(@AuthenticationPrincipal AppUserDetails worker,
                                       @PathVariable Long id,
                                       @RequestParam(required = false) String message) {
        bookingService.approve(worker.getCenterId(), id, worker.getId(), message);
        return Map.of("status", "CONFIRMED");
    }

    @PostMapping("/reject")
    public Map<String, String> reject(@AuthenticationPrincipal AppUserDetails worker,
                                      @PathVariable Long id,
                                      @RequestParam(required = false) String message) {
        bookingService.reject(worker.getCenterId(), id, message);
        return Map.of("status", "REJECTED");
    }
}
