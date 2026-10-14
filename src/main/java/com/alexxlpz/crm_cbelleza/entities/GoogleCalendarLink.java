package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Trabajador o cliente con sus citas sincronizadas con Google Calendar: token de actualización de Google
 * (cifrado, ver {@code GoogleTokenCipher}) y calendario «Cuquora» creado en su cuenta.
 */
@Entity
@Table(name = "google_calendar_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleCalendarLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Cuenta de Google en la que se guardan las citas (solo para mostrarla en el perfil). */
    @Column(name = "google_email")
    private String googleEmail;

    @Column(name = "encrypted_refresh_token", nullable = false, length = 2048)
    private String encryptedRefreshToken;

    @Column(name = "calendar_id", nullable = false)
    private String calendarId;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;
}
