package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Foto de un producto, ya reducida. Va en su propia tabla para que listar el inventario no lea nunca la imagen;
 * {@link Product#getPhotoUrl()} dice si hay foto.
 */
@Entity
@Table(name = "product_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductPhoto {
    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;
}
