package com.alexxlpz.productapi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Una aportación de un centro de Cuquora: qué huecos de qué producto rellenó y cuándo. Sirve para saber de dónde
 * salió cada dato y, si un centro mete datos malos, encontrar y borrar lo suyo (SQL en el README).
 */
@Entity
@Table(name = "product_contributions")
public class ProductContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 14)
    private String gtin;

    /** El número del centro en Cuquora; nulo si la aportación llegó sin él. */
    @Column(name = "center_id")
    private Long centerId;

    /** Si este centro dio de alta el producto. */
    @Column(nullable = false)
    private boolean created;

    /** Los datos que rellenó, separados por comas (nombre, marca, categoria, formato, descripcion, foto). */
    @Column(nullable = false, length = 120)
    private String fields;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProductContribution() {
    }

    public ProductContribution(String gtin, Long centerId, boolean created, String fields, LocalDateTime now) {
        this.gtin = gtin;
        this.centerId = centerId;
        this.created = created;
        this.fields = fields;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public String getGtin() { return gtin; }
    public Long getCenterId() { return centerId; }
    public boolean isCreated() { return created; }
    public String getFields() { return fields; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
