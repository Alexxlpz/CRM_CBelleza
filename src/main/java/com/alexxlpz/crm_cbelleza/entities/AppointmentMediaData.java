package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

/** Contenido de una foto o vídeo del resumen del tratamiento ({@link AppointmentMedia}). */
@Entity
@Table(name = "appointment_media_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentMediaData {
    @Id
    @Column(name = "media_id")
    private Long mediaId;

    @Column(nullable = false)
    private byte[] data;
}
