package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.CenterPhoto;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.CenterPhotoRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Foto de presentación de los centros. La imagen subida se lee como imagen (no se fía del nombre ni del tipo
 * que manda el navegador), se endereza según la orientación EXIF de las fotos de móvil, se reduce a
 * {@value #MAX_SIDE} px por el lado largo y se guarda como JPEG: lo que llega a la base de datos son unos
 * 100-250 KB aunque se suba una foto de 8 MB.
 */
@Service
@Transactional
public class CenterPhotoService {

    /** Tamaño máximo del fichero subido (también en spring.servlet.multipart.max-file-size). */
    public static final int MAX_UPLOAD_MB = PhotoImages.MAX_UPLOAD_MB;
    /** Lado largo de la foto guardada: de sobra para la cabecera del centro en una pantalla grande. */
    static final int MAX_SIDE = 1200;
    /** Por debajo de esto la foto se ve borrosa en la tarjeta del buscador. */
    static final int MIN_WIDTH = 400;
    static final int MIN_HEIGHT = 250;

    private final CenterPhotoRepository photos;
    private final CenterRepository centers;
    private final Clock clock;

    public CenterPhotoService(CenterPhotoRepository photos, CenterRepository centers, Clock clock) {
        this.photos = photos;
        this.centers = centers;
        this.clock = clock;
    }

    /** Foto ya preparada para guardar. */
    public record PreparedPhoto(byte[] jpeg) {
    }

    /**
     * Comprueba y redimensiona la foto sin guardar nada, para poder rechazarla antes de crear un centro.
     * Devuelve vacío si no se eligió ningún fichero.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.SUPPORTS)
    public Optional<PreparedPhoto> prepare(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PreparedPhoto(resize(PhotoImages.bytesOf(file))));
    }

    /** Sustituye la foto del centro. */
    public Center save(Long centerId, PreparedPhoto photo) {
        Center center = centers.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("El centro solicitado no existe."));
        photos.save(new CenterPhoto(centerId, "image/jpeg", photo.jpeg()));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        // La URL lleva la hora en segundos: dos fotos en el mismo segundo tendrían la misma URL en caché
        if (center.getPhotoUpdatedAt() != null && !now.isAfter(center.getPhotoUpdatedAt())) {
            now = center.getPhotoUpdatedAt().plusSeconds(1);
        }
        center.setPhotoUpdatedAt(now);
        return center;
    }

    public void remove(Long centerId) {
        Center center = centers.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("El centro solicitado no existe."));
        photos.deleteById(centerId);
        center.setPhotoUpdatedAt(null);
    }

    /**
     * Foto de un centro. Solo la de un centro publicado es pública; la de uno pendiente de revisión la ven
     * su propio equipo («Mi Centro») y administración desde el enlace de aprobación.
     */
    @Transactional(readOnly = true)
    public CenterPhoto photoOf(Long centerId, Long viewerCenterId) {
        Center center = centers.findById(centerId)
                .filter(c -> c.isPublished() || centerId.equals(viewerCenterId))
                .orElseThrow(() -> new ResourceNotFoundException("La foto no existe."));
        return photos.findById(center.getId())
                .orElseThrow(() -> new ResourceNotFoundException("La foto no existe."));
    }

    @Transactional(readOnly = true)
    public Optional<CenterPhoto> findPhoto(Long centerId) {
        return photos.findById(centerId);
    }

    static byte[] resize(byte[] original) throws BusinessRuleException {
        return PhotoImages.fit(original, MAX_SIDE, MIN_WIDTH, MIN_HEIGHT, false);
    }
}
