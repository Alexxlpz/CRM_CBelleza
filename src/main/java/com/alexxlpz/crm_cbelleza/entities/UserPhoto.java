package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Foto de perfil de un profesional, ya recortada. Va en su propia tabla para que cargar usuarios no lea nunca la
 * imagen; {@link User#getPhotoUrl()} dice si hay foto.
 */
@Entity
@Table(name = "user_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserPhoto {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;
}
