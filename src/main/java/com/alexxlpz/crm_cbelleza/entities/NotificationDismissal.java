package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Aviso de la campana que un usuario ha quitado. Se guarda su huella ({@link com.alexxlpz.crm_cbelleza.services.NotificationKeys}). */
@Entity
@Table(name = "notification_dismissals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDismissal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notification_key", nullable = false, length = 64)
    private String notificationKey;

    @Column(name = "dismissed_at", nullable = false)
    private LocalDateTime dismissedAt;
}
