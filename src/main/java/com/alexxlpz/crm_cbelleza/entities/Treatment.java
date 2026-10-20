package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Entity
@Table(name = "treatments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Treatment {
    private static final DateTimeFormatter OFFER_END = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"));
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Integer duration; // en minutos

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment_type", nullable = false)
    private TreatmentType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;

    /** Precio rebajado mientras dura la oferta; nulo = sin oferta. */
    @Column(name = "offer_price")
    private Double offerPrice;

    /** Último día (hora del centro) en que se reserva con el precio de oferta; nulo = hasta que se quite. */
    @Column(name = "offer_ends_on")
    private LocalDate offerEndsOn;

    public boolean hasOffer() {
        return offerPrice != null;
    }

    /** La oferta vale para las citas que se piden ese día, sea cual sea el día de la cita. */
    public boolean hasActiveOffer(LocalDate today) {
        return offerPrice != null && (offerEndsOn == null || !today.isAfter(offerEndsOn));
    }

    /** Lo que se cobra al reservar ese día. */
    public double priceOn(LocalDate today) {
        return hasActiveOffer(today) ? offerPrice : price;
    }

    /** «hasta el 30 oct» para las etiquetas; vacío si no tiene fecha de fin. */
    public String offerEndsLabel() {
        return offerEndsOn == null ? "" : "hasta el " + OFFER_END.format(offerEndsOn).replace(".", "");
    }

    /** Descuento redondeado para la etiqueta («-20 %»). */
    public int offerDiscountPercent() {
        if (offerPrice == null || price == null || price <= 0) {
            return 0;
        }
        return (int) Math.round((1 - offerPrice / price) * 100);
    }
}
