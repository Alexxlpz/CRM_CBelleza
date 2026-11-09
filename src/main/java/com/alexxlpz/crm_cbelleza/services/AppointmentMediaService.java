package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentMedia;
import com.alexxlpz.crm_cbelleza.entities.AppointmentMediaData;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentMediaDataRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentMediaRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fotos y vídeos del resumen del tratamiento: para enseñar al resto del equipo algo que se entiende mejor viéndolo
 * (un enrojecimiento poco habitual, cómo quedó una zona...). Como el resumen, se añaden cuando la cita ya ha empezado
 * y solo los ve el equipo del centro. Las fotos se reducen a {@value #PHOTO_SIDE} px; los vídeos se guardan tal cual
 * (MP4, MOV o WebM, hasta {@value #MAX_VIDEO_MB} MB) porque convertirlos necesitaría ffmpeg en el servidor.
 */
@Service
@Transactional
public class AppointmentMediaService {

    public static final int MAX_PER_APPOINTMENT = 6;
    public static final int MAX_VIDEO_MB = 30;
    public static final int MAX_PHOTO_MB = PhotoImages.MAX_UPLOAD_MB;
    /** Lado largo de la foto guardada: se ve bien a pantalla completa y pesa unos 300 KB. */
    static final int PHOTO_SIDE = 1600;

    private final AppointmentMediaRepository media;
    private final AppointmentMediaDataRepository mediaData;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final Clock clock;

    public AppointmentMediaService(AppointmentMediaRepository media, AppointmentMediaDataRepository mediaData,
                                   AppointmentRepository appointments, UserRepository users, Clock clock) {
        this.media = media;
        this.mediaData = mediaData;
        this.appointments = appointments;
        this.users = users;
        this.clock = clock;
    }

    /** Fichero con su tipo, para servirlo. */
    public record MediaFile(String contentType, byte[] data) {
    }

    /** Añade una foto o un vídeo a la cita. */
    public AppointmentMedia add(Long centerId, Long appointmentId, Long workerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Elige una foto o un vídeo.");
        }
        Appointment appointment = editableAppointment(centerId, appointmentId);
        if (media.countByAppointmentId(appointmentId) >= MAX_PER_APPOINTMENT) {
            throw new BusinessRuleException("Cada cita admite " + MAX_PER_APPOINTMENT + " fotos o vídeos. Quita alguno para añadir otro.");
        }
        boolean video = file.getContentType() != null && file.getContentType().startsWith("video/");
        AppointmentMedia.Kind kind;
        String contentType;
        byte[] data;
        if (video) {
            data = videoBytes(file);
            contentType = videoType(data);
            kind = AppointmentMedia.Kind.VIDEO;
        } else {
            data = PhotoImages.fit(PhotoImages.bytesOf(file), PHOTO_SIDE, 1, 1, false);
            contentType = "image/jpeg";
            kind = AppointmentMedia.Kind.PHOTO;
        }
        AppointmentMedia saved = media.save(AppointmentMedia.builder()
                .appointmentId(appointment.getId())
                .centerId(centerId)
                .kind(kind)
                .contentType(contentType)
                .sizeBytes(data.length)
                .createdAt(CenterClock.now(clock, appointment.getCenter()))
                .uploadedBy(users.getReferenceById(workerId))
                .build());
        mediaData.save(new AppointmentMediaData(saved.getId(), data));
        return saved;
    }

    /** Quita una foto o un vídeo (cualquiera del equipo, como el resumen). */
    public void remove(Long centerId, Long appointmentId, Long mediaId) {
        AppointmentMedia item = media.findByIdAndCenterId(mediaId, centerId)
                .filter(m -> m.getAppointmentId().equals(appointmentId))
                .orElseThrow(() -> new ResourceNotFoundException("La foto o el vídeo no existe."));
        editableAppointment(centerId, appointmentId);
        mediaData.deleteById(item.getId());
        media.delete(item);
    }

    /** El fichero, solo para el equipo del centro de la cita. */
    @Transactional(readOnly = true)
    public MediaFile file(Long centerId, Long mediaId) {
        AppointmentMedia item = media.findByIdAndCenterId(mediaId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("La foto o el vídeo no existe."));
        byte[] data = mediaData.findById(item.getId())
                .orElseThrow(() -> new ResourceNotFoundException("La foto o el vídeo no existe."))
                .getData();
        return new MediaFile(item.getContentType(), data);
    }

    @Transactional(readOnly = true)
    public List<AppointmentMedia> forAppointment(Long centerId, Long appointmentId) {
        return media.findByCenterIdAndAppointmentIdOrderByCreatedAtAscIdAsc(centerId, appointmentId);
    }

    /** Los de esas citas, agrupados por cita (ficha del cliente). */
    @Transactional(readOnly = true)
    public Map<Long, List<AppointmentMedia>> byAppointment(Long centerId, Collection<Long> appointmentIds) {
        if (appointmentIds.isEmpty()) {
            return Map.of();
        }
        return group(media.findByCenterIdAndAppointmentIdInOrderByCreatedAtAscIdAsc(centerId, appointmentIds));
    }

    /** Los de todas las citas del centro, agrupados por cita (agenda). */
    @Transactional(readOnly = true)
    public Map<Long, List<AppointmentMedia>> byAppointment(Long centerId) {
        return group(media.findByCenterIdOrderByCreatedAtAscIdAsc(centerId));
    }

    private static Map<Long, List<AppointmentMedia>> group(List<AppointmentMedia> items) {
        return items.stream().collect(Collectors.groupingBy(AppointmentMedia::getAppointmentId));
    }

    private Appointment editableAppointment(Long centerId, Long appointmentId) {
        Appointment appointment = appointments.findByIdAndCenterId(appointmentId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("La cita no existe en tu centro."));
        if (!appointment.isSummaryEditableAt(CenterClock.now(clock, appointment.getCenter()))) {
            throw new BusinessRuleException("Las fotos y vídeos se añaden cuando la cita ya ha empezado.");
        }
        return appointment;
    }

    private static byte[] videoBytes(MultipartFile file) {
        if (file.getSize() > MAX_VIDEO_MB * 1024L * 1024L) {
            throw new BusinessRuleException("El vídeo pesa más de " + MAX_VIDEO_MB + " MB. Grábalo más corto (unos 20 segundos bastan).");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("No se ha podido leer el vídeo. Prueba con otro en MP4 o MOV.");
        }
    }

    /**
     * Tipo real del vídeo según sus primeros bytes (no se fía del nombre ni de lo que diga el navegador): MP4 y MOV
     * llevan «ftyp» en el byte 4; WebM empieza por la cabecera EBML.
     */
    static String videoType(byte[] data) {
        if (data.length >= 12 && "ftyp".equals(new String(data, 4, 4, StandardCharsets.ISO_8859_1))) {
            return "qt  ".equals(new String(data, 8, 4, StandardCharsets.ISO_8859_1)) ? "video/quicktime" : "video/mp4";
        }
        if (data.length >= 4 && Arrays.equals(Arrays.copyOf(data, 4), new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3})) {
            return "video/webm";
        }
        throw new BusinessRuleException("Ese vídeo no se puede guardar: usa MP4, MOV o WebM.");
    }
}
