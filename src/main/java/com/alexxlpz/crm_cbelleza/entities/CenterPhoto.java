package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Foto de presentación de un centro, ya redimensionada. Va en su propia tabla para que listar o cargar
 * centros no lea nunca la imagen; {@link Center#getPhotoUpdatedAt()} dice si hay foto.
 */
@Entity
@Table(name = "center_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CenterPhoto {
    @Id
    @Column(name = "center_id")
    private Long centerId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;
}
