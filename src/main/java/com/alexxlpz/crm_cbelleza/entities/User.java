package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

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
    private java.time.LocalDateTime notificationsSeenAt;

    /**
     * Tiene cuenta propia (puede iniciar sesión con contraseña o con Google).
     * Los clientes dados de alta a mano desde la cartera no tienen ninguna de las dos.
     */
    public boolean hasAccount() {
        return password != null || googleId != null;
    }
}
