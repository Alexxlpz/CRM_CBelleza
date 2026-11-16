package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Enlace de un solo uso para que un trabajador dado de alta con su centro ponga su contraseña, o para que alguien que
 * ya tenía cuenta de trabajador sin centro acepte unirse a un equipo ({@link #center}).
 */
@Entity
@Table(name = "account_invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountInvitation {

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

    /** Cuándo se puso la contraseña; un enlace usado ya no vale. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** Equipo al que se le invita a unirse; nulo en las invitaciones para crear la contraseña de quien ya está en él. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id")
    private Center center;

    public boolean isTeamInvitation() {
        return center != null;
    }

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
