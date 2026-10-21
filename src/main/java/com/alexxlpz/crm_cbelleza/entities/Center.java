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

    /** Las citas que piden los clientes quedan confirmadas al momento, sin que nadie del centro las apruebe. */
    @Column(name = "auto_accept_bookings", nullable = false)
    @Builder.Default
    private boolean autoAcceptBookings = false;

    /** Sale en el buscador y admite reservas. Los centros dados de alta en /alta-centro esperan a la revisión. */
    @Column(nullable = false)
    @Builder.Default
    private boolean published = true;

    /** CIF o NIF que se revisa antes de publicar el centro. */
    @Column(name = "tax_id", length = 20)
    private String taxId;

    /** Huella del enlace «Publicar centro» que recibe administración; nula una vez publicado. */
    @Column(name = "approval_token_hash", unique = true, length = 64)
    private String approvalTokenHash;

    @Column(name = "registered_at")
    private java.time.LocalDateTime registeredAt;

    /** Cuándo se subió la foto de presentación (nula = sin foto); versiona la URL de la imagen. */
    @Column(name = "photo_updated_at")
    private java.time.LocalDateTime photoUpdatedAt;

    /** Dirección de la foto de presentación, o nula si el centro no tiene. */
    public String getPhotoUrl() {
        return photoUpdatedAt == null ? null
                : "/centers/" + id + "/photo?v=" + photoUpdatedAt.toEpochSecond(java.time.ZoneOffset.UTC);
    }

    /** Iniciales del nombre (como mucho dos) para el hueco de la foto cuando el centro no tiene. */
    public String getInitials() {
        if (name == null) {
            return "";
        }
        StringBuilder initials = new StringBuilder();
        for (String word : name.trim().split("[\\s\\-]+")) {
            if (!word.isEmpty() && Character.isLetterOrDigit(word.codePointAt(0)) && initials.length() < 2) {
                initials.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
            }
        }
        return initials.toString();
    }

    public boolean isLoyaltyEnabled() {
        return loyaltyMode != null && loyaltyMode != LoyaltyMode.OFF;
    }
}
