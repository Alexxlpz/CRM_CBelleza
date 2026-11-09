package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Enlace de un solo uso del correo «¿Olvidaste tu contraseña?». */
@Entity
@Table(name = "password_resets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Huella SHA-256 del enlace: si se filtra la base de datos, los enlaces no sirven. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Cuándo se puso la contraseña nueva; un enlace usado ya no vale. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
