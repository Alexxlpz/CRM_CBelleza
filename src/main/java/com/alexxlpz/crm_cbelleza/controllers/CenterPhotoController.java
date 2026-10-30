package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.CenterPhoto;
import com.alexxlpz.crm_cbelleza.entities.UserPhoto;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.CenterPhotoService;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService;
import com.alexxlpz.crm_cbelleza.services.WorkerPhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;

/** Fotos de los centros (buscador, ficha, «Mi Centro», revisión del alta) y de perfil de sus profesionales. */
@Controller
public class CenterPhotoController {

    private final CenterPhotoService photoService;
    private final CenterRegistrationService registrationService;
    private final WorkerPhotoService workerPhotos;

    public CenterPhotoController(CenterPhotoService photoService, CenterRegistrationService registrationService,
                                 WorkerPhotoService workerPhotos) {
        this.photoService = photoService;
        this.registrationService = registrationService;
        this.workerPhotos = workerPhotos;
    }

    /** La URL lleva la versión (?v=), así que el navegador puede guardarla un año sin volver a pedirla. */
    @GetMapping("/centers/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails viewer) {
        Long viewerCenterId = viewer == null ? null : viewer.getCenterId();
        CenterPhoto photo = photoService.photoOf(id, viewerCenterId);
        boolean ownCenter = id.equals(viewerCenterId);
        // La de un centro aún oculto solo la ve su equipo: que no quede en cachés compartidas
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(365));
        return image(photo, ownCenter ? cache.cachePrivate() : cache.cachePublic().immutable());
    }

    /** Para que administración vea la foto al revisar un alta, antes de publicar el centro. */
    @GetMapping("/register-center/approve/{token}/photo")
    public ResponseEntity<byte[]> pendingPhoto(@PathVariable String token) {
        Long centerId = registrationService.pendingCenter(token).getId();
        CenterPhoto photo = photoService.findPhoto(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("La foto no existe."));
        return image(photo, CacheControl.noStore());
    }

    /** Foto de perfil de un profesional («¿Con quién?» al reservar, equipo); versionada con ?v= como la del centro. */
    @GetMapping("/team/{id}/photo")
    public ResponseEntity<byte[]> workerPhoto(@PathVariable Long id) {
        UserPhoto photo = workerPhotos.photoOf(id);
        return image(photo.getContentType(), photo.getData(), CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    private static ResponseEntity<byte[]> image(CenterPhoto photo, CacheControl cache) {
        return image(photo.getContentType(), photo.getData(), cache);
    }

    private static ResponseEntity<byte[]> image(String contentType, byte[] data, CacheControl cache) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff")
                .body(data);
    }
}
