package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    private String category;
    
    @Column(nullable = false)
    private Double price;

    /** Centro que lo creó (solo lo ve ese centro); nulo = producto del catálogo general. */
    @Column(name = "center_id")
    private Long centerId;
}
