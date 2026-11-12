package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

    /** Código de barras del envase (EAN/UPC, solo dígitos); nulo si no se ha escaneado. */
    @Column(length = 32)
    private String barcode;

    /** Cuándo se puso la foto actual (nula = sin foto); versiona la URL de la imagen. */
    @Column(name = "photo_updated_at")
    private LocalDateTime photoUpdatedAt;

    /** De dónde salió la foto: nulo = la subió el centro; {@link #OPEN_BEAUTY_FACTS} = la encontrada al escanear. */
    @Column(name = "photo_source", length = 30)
    private String photoSource;

    public static final String OPEN_BEAUTY_FACTS = "OPEN_BEAUTY_FACTS";

    /** Si la foto es de Open Beauty Facts (licencia CC BY-SA): hay que decirlo al enseñarla. */
    public boolean isPhotoFromOpenBeautyFacts() {
        return photoUpdatedAt != null && OPEN_BEAUTY_FACTS.equals(photoSource);
    }

    /** La ficha del producto en Open Beauty Facts, para el crédito de la foto. */
    public String getOpenBeautyFactsUrl() {
        return barcode == null ? "https://world.openbeautyfacts.org" : "https://world.openbeautyfacts.org/product/" + barcode;
    }

    /** Dirección de la foto, o nula si no tiene (se pinta la imagen genérica de su categoría). */
    public String getPhotoUrl() {
        return photoUpdatedAt == null ? null
                : "/worker/products/" + id + "/photo?v=" + photoUpdatedAt.toEpochSecond(ZoneOffset.UTC);
    }

    /** Foto del producto o, si no tiene, la genérica de su categoría. */
    public String getImageUrl() {
        String photo = getPhotoUrl();
        if (photo != null) return photo;
        if ("Rostro".equals(category)) return "/images/face_serum.webp";
        if ("Cabello".equals(category)) return "/images/hair_oil.webp";
        return "/images/beauty_salon_hero.webp";
    }
}
