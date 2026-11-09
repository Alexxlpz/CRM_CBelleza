package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.dto.AppointmentCalendarDTO;
import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AppointmentMediaService;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * Fotos y vídeos del resumen del tratamiento (modal del resumen en la agenda y en la ficha del cliente). Subir y quitar
 * devuelven la cita en el formato de la agenda, como el resto de acciones de la cita.
 */
@RestController
@ApiEndpoint
public class AppointmentMediaController {

    private final AppointmentMediaService mediaService;
    private final AppointmentService appointmentService;

    public AppointmentMediaController(AppointmentMediaService mediaService, AppointmentService appointmentService) {
        this.mediaService = mediaService;
        this.appointmentService = appointmentService;
    }

    @PostMapping("/worker/appointments/{id}/media")
    public AppointmentCalendarDTO upload(@AuthenticationPrincipal AppUserDetails worker,
                                         @PathVariable Long id,
                                         @RequestParam(required = false) MultipartFile file) {
        mediaService.add(worker.getCenterId(), id, worker.getId(), file);
        return appointmentService.getCalendarAppointment(worker.getCenterId(), id);
    }

    @PostMapping("/worker/appointments/{id}/media/{mediaId}/delete")
    public AppointmentCalendarDTO remove(@AuthenticationPrincipal AppUserDetails worker,
                                         @PathVariable Long id,
                                         @PathVariable Long mediaId) {
        mediaService.remove(worker.getCenterId(), id, mediaId);
        return appointmentService.getCalendarAppointment(worker.getCenterId(), id);
    }

    /**
     * El fichero, solo para el equipo del centro. Al devolverse como Resource, Spring atiende las peticiones por partes
     * (Range), que Safari necesita para reproducir un vídeo. Cada id es un fichero que no cambia: caché privada larga.
     */
    @GetMapping("/worker/media/{mediaId}")
    public ResponseEntity<Resource> file(@AuthenticationPrincipal AppUserDetails worker, @PathVariable Long mediaId) {
        AppointmentMediaService.MediaFile file = mediaService.file(worker.getCenterId(), mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(file.data()));
    }
}
