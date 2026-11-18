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

    /** Con estas unidades o menos avisa «Stock bajo»; nulo = no avisa (solo «Agotado» al llegar a 0). */
    @Column(name = "low_stock_alert")
    private Integer lowStockAlert;

    /** Le quedan pocas unidades según el aviso que puso el centro (agotado no cuenta: tiene su propio aviso). */
    public boolean isLowStock() {
        return stock != null && stock > 0 && lowStockAlert != null && stock <= lowStockAlert;
    }
}
