package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventories", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"center_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer stock;
}
