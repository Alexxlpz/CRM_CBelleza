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
}
