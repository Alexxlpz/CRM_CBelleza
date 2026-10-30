package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.entities.UserPhoto;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.UserPhotoRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Foto de perfil de los profesionales: la sube cada uno desde «Mi Centro» y la ven los clientes al elegir con quién
 * reservar. Se recorta en cuadrado y se reduce a {@value #SIDE} px (unos 30 KB), como la del centro.
 */
@Service
@Transactional
public class WorkerPhotoService {

    public static final int MAX_UPLOAD_MB = PhotoImages.MAX_UPLOAD_MB;
    /** Lado de la foto guardada: de sobra para el círculo más grande en que sale (pantallas de alta densidad). */
    static final int SIDE = 400;
    /** Por debajo de esto la cara se ve borrosa. */
    static final int MIN_SIDE = 160;

    private final UserPhotoRepository photos;
    private final UserRepository users;
    private final Clock clock;

    public WorkerPhotoService(UserPhotoRepository photos, UserRepository users, Clock clock) {
        this.photos = photos;
        this.users = users;
        this.clock = clock;
    }

    /** Sustituye la foto del profesional. */
    public User save(Long workerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Elige una foto para subirla.");
        }
        byte[] jpeg = PhotoImages.fit(PhotoImages.bytesOf(file), SIDE, MIN_SIDE, MIN_SIDE, true);
        User worker = worker(workerId);
        photos.save(new UserPhoto(workerId, "image/jpeg", jpeg));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        // La URL lleva la hora en segundos: dos fotos en el mismo segundo tendrían la misma URL en caché
        if (worker.getPhotoUpdatedAt() != null && !now.isAfter(worker.getPhotoUpdatedAt())) {
            now = worker.getPhotoUpdatedAt().plusSeconds(1);
        }
        worker.setPhotoUpdatedAt(now);
        return worker;
    }

    public void remove(Long workerId) {
        User worker = worker(workerId);
        photos.deleteById(workerId);
        worker.setPhotoUpdatedAt(null);
    }

    /**
     * Foto de un profesional: es pública (sale al reservar) cuando su centro ya está publicado; antes solo la ve su
     * equipo, para que el alta pública no sirva para colgar imágenes en Cuquora. Los clientes no tienen foto.
     */
    @Transactional(readOnly = true)
    public UserPhoto photoOf(Long workerId, Long viewerCenterId) {
        return users.findWithCenterById(workerId)
                .filter(u -> u.getRole() == Role.WORKER && u.getCenter() != null)
                .filter(u -> u.getCenter().isPublished() || u.getCenter().getId().equals(viewerCenterId))
                .flatMap(u -> photos.findById(u.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("La foto no existe."));
    }

    private User worker(Long workerId) {
        return users.findById(workerId)
                .filter(u -> u.getRole() == Role.WORKER)
                .orElseThrow(() -> new ResourceNotFoundException("El profesional no existe."));
    }
}
