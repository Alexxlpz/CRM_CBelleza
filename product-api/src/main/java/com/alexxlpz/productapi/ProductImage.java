package com.alexxlpz.productapi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @Column(length = 14)
    private String gtin;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    protected ProductImage() {
    }

    public ProductImage(String gtin, String contentType, byte[] data) {
        this.gtin = gtin;
        this.contentType = contentType;
        this.data = data;
    }

    public String getGtin() { return gtin; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
}
