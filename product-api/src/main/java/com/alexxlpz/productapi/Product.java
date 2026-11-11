package com.alexxlpz.productapi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(length = 14)
    private String gtin;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String brand;

    @Column(length = 40)
    private String category;

    @Column(length = 40)
    private String quantity;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "image_updated_at")
    private LocalDateTime imageUpdatedAt;

    protected Product() {
    }

    public Product(String gtin, String name, Source source, LocalDateTime now) {
        this.gtin = gtin;
        this.name = name;
        this.source = source;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getGtin() { return gtin; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getCategory() { return category; }
    public String getQuantity() { return quantity; }
    public String getDescription() { return description; }
    public Source getSource() { return source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getImageUpdatedAt() { return imageUpdatedAt; }

    public void setName(String name) { this.name = name; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setCategory(String category) { this.category = category; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public void setDescription(String description) { this.description = description; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setImageUpdatedAt(LocalDateTime imageUpdatedAt) { this.imageUpdatedAt = imageUpdatedAt; }
}
