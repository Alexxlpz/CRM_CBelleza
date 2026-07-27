package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "treatments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Treatment {
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
    private Integer duration; // in minutes

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment_type", nullable = false)
    private TreatmentType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;
}
