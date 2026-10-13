package com.alexxlpz.crm_cbelleza.entities;

import com.alexxlpz.crm_cbelleza.booking.CenterTimeZones;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "centers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Center {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;
    private String phone;
    private String email;

    private Double latitude;
    private Double longitude;

    /** Zona horaria IANA del centro (ver {@link CenterTimeZones}); las citas se guardan en hora local del centro. */
    @Column(name = "time_zone", nullable = false, length = 64)
    @Builder.Default
    private String timeZone = CenterTimeZones.DEFAULT;

    /** Cabinas o puestos: cuántas citas puede atender el centro a la vez (si hay trabajadores libres). */
    @Column(nullable = false)
    @Builder.Default
    private Integer cabins = 1;

    /** Fidelización: cómo se suman puntos (ver {@link LoyaltyMode}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "loyalty_mode", nullable = false, length = 16)
    @Builder.Default
    private LoyaltyMode loyaltyMode = LoyaltyMode.OFF;

    /** Puntos por euro o por tratamiento/unidad de producto, según el modo. */
    @Column(name = "loyalty_points_per_unit", nullable = false)
    @Builder.Default
    private Integer loyaltyPointsPerUnit = 1;

    /** Puntos necesarios para canjear la recompensa. */
    @Column(name = "loyalty_reward_points", nullable = false)
    @Builder.Default
    private Integer loyaltyRewardPoints = 100;

    @Column(name = "loyalty_reward_text", length = 200)
    private String loyaltyRewardText;

    /** Desde cuándo suma puntos: las citas anteriores no cuentan. */
    @Column(name = "loyalty_since")
    private java.time.LocalDateTime loyaltySince;

    public boolean isLoyaltyEnabled() {
        return loyaltyMode != null && loyaltyMode != LoyaltyMode.OFF;
    }
}
