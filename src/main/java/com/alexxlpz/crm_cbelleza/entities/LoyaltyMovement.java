package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Entrada (tratamiento o producto) o salida (canje) de puntos de un cliente en un centro. El saldo es la suma. */
@Entity
@Table(name = "loyalty_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyMovement {

    public enum Reason { TREATMENT, PRODUCT, REDEMPTION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    /** Positivo al ganar, negativo al canjear. */
    @Column(nullable = false)
    private Integer points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Reason reason;

    /** Texto para el historial: tratamiento, producto o recompensa canjeada. */
    private String description;

    /** Cita que dio los puntos (única: una cita solo suma una vez). */
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "product_sale_id")
    private Long productSaleId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;
}
