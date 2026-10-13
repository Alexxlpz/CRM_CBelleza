package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Producto vendido por un centro a un cliente. Al registrarla se resta del inventario del centro. */
@Entity
@Table(name = "product_sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Quién la registró (nulo si ya no existe). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private User worker;

    @Column(nullable = false)
    private Integer quantity;

    /** Precio del momento de la venta: el historial no cambia si luego cambia el precio del producto. */
    @Column(name = "unit_price", nullable = false)
    private Double unitPrice;

    @Column(name = "sold_at", nullable = false)
    private LocalDateTime soldAt;

    public double getTotal() {
        return unitPrice * quantity;
    }
}
