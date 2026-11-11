package com.alexxlpz.productapi;

import java.time.ZoneOffset;

/**
 * Un producto tal como lo devuelve la API.
 *
 * @param image ruta de la foto dentro de la API (cambia al cambiar la foto), o nula si no hay
 */
public record ProductJson(String gtin, String name, String brand, String category, String quantity, String description,
                          Source source, String image) {

    static ProductJson of(Product p) {
        String image = p.getImageUpdatedAt() == null ? null
                : "/api/products/" + p.getGtin() + "/image?v=" + p.getImageUpdatedAt().toEpochSecond(ZoneOffset.UTC);
        return new ProductJson(p.getGtin(), p.getName(), p.getBrand(), p.getCategory(), p.getQuantity(), p.getDescription(),
                p.getSource(), image);
    }
}
