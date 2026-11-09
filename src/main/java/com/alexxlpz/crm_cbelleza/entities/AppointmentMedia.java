package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Foto o vídeo del resumen del tratamiento de una cita. Solo los datos: el fichero va en
 * {@link AppointmentMediaData}, para que listar los de una cita (o de toda la agenda) no lea nunca su contenido.
 */
@Entity
@Table(name = "appointment_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentMedia {

    public enum Kind { PHOTO, VIDEO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    /** Centro de la cita: así cada consulta filtra por centro sin cargar la cita. */
    @Column(name = "center_id", nullable = false)
    private Long centerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    public boolean isVideo() {
        return kind == Kind.VIDEO;
    }

    /** Dirección del fichero (solo para el equipo del centro). */
    public String getUrl() {
        return "/worker/media/" + id;
    }
}
