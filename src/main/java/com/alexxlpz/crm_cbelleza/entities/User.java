package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /** Dominio de los correos internos que se inventan para clientes dados de alta sin correo (ver ClientCardService). */
    public static final String PLACEHOLDER_EMAIL_DOMAIN = "@cbelleza.local";
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;

    private String phone;

    @Column
    private String password;

    /** Identificador ("sub") de la cuenta de Google vinculada; nulo si no se ha entrado nunca con Google. */
    @Column(name = "google_id", unique = true)
    private String googleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id")
    private Center center;

    /** Última vez que abrió su campana de avisos: lo posterior cuenta como nuevo. */
    @Column(name = "notifications_seen_at")
    private LocalDateTime notificationsSeenAt;

    /** Ha pulsado «No mostrar más» en el aviso para valorar una cita: no se le vuelve a enseñar. */
    @Column(name = "review_prompts_off", nullable = false)
    private boolean reviewPromptsOff;

    /** Cuándo subió su foto de perfil un profesional (nula = sin foto); versiona la URL de la imagen. */
    @Column(name = "photo_updated_at")
    private LocalDateTime photoUpdatedAt;

    /**
     * Si se ha comprobado que el correo es de esta persona (enlace de un correo de Cuquora o Google). El registro público
     * crea la cuenta sin comprobar: hasta entonces su contraseña no basta para quedarse con una cuenta que entre con Google.
     */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = true;

    /**
     * Tiene cuenta propia (puede iniciar sesión con contraseña o con Google).
     * Los clientes dados de alta a mano desde la cartera no tienen ninguna de las dos.
     */
    public boolean hasAccount() {
        return password != null || googleId != null;
    }

    /** Dirección de la foto de perfil, o nula si no tiene. */
    public String getPhotoUrl() {
        return photoUpdatedAt == null ? null
                : "/team/" + id + "/photo?v=" + photoUpdatedAt.toEpochSecond(ZoneOffset.UTC);
    }

    /** Inicial del nombre para el hueco de la foto cuando no tiene. */
    public String getInitial() {
        return name == null || name.isBlank() ? "?" : name.strip().substring(0, 1).toUpperCase();
    }
}
