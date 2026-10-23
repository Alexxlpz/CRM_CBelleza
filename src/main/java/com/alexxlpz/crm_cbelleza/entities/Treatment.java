package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    /** Profesionales que lo hacen; vacío = todo el equipo. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "treatment_workers", joinColumns = @JoinColumn(name = "treatment_id"))
    @Column(name = "worker_id")
    @Builder.Default
    private Set<Long> workerIds = new HashSet<>();

    /** true si lo hace ese profesional (o lo hace todo el equipo). */
    public boolean isDoneBy(Long workerId) {
        return workerIds.isEmpty() || workerIds.contains(workerId);
    }

    /** Los del equipo que lo hacen. Si los elegidos ya no están en el centro, lo hace todo el equipo. */
    public List<User> doneBy(List<User> team) {
        List<User> chosen = team.stream().filter(w -> workerIds.contains(w.getId())).toList();
        return chosen.isEmpty() ? team : chosen;
    }

    /** true si lo hace todo el equipo (para las plantillas). */
    public boolean isDoneByWholeTeam(List<User> team) {
        return doneBy(team).size() == team.size();
    }

    /** Ids de quienes lo hacen separados por comas, para data-workers (booking-picker.js y treatment-workers.js). */
    public String doneByIds(List<User> team) {
        return String.join(",", doneBy(team).stream().map(w -> String.valueOf(w.getId())).toList());
    }

    /** Nombres de quienes lo hacen («Lucía Romero, Carlos Mendoza»). */
    public String doneByNames(List<User> team) {
        return String.join(", ", doneBy(team).stream().map(User::getName).toList());
    }

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
