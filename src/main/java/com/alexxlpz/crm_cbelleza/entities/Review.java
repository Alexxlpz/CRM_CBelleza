package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Reseña verificada: la deja el cliente de una cita completada (una por cita). El centro puede responderla
 * en público, pero no borrarla.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    public static final int MAX_TEXT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    /** De 1 a 5 estrellas. */
    @Column(nullable = false)
    private Integer rating;

    @Column(length = MAX_TEXT)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Respuesta pública del centro; nula si aún no ha respondido. */
    @Column(length = MAX_TEXT)
    private String reply;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    /** «Sofía M.»: el nombre y la inicial del apellido, que es lo que se enseña en público. */
    public String getAuthorName() {
        String name = client != null && client.getName() != null ? client.getName().trim() : "";
        if (name.isEmpty()) {
            return "Cliente";
        }
        String[] parts = name.split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[1].substring(0, 1).toUpperCase() + ".";
    }

    public boolean hasReply() {
        return reply != null && !reply.isBlank();
    }
}
