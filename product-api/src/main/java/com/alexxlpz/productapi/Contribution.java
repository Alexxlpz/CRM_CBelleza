package com.alexxlpz.productapi;

/**
 * Lo que aporta un centro de Cuquora al dar de alta un producto con código: nunca su precio ni su stock.
 *
 * @param centerId el número del centro en Cuquora, o nulo si no lo manda (versiones antiguas)
 */
public record Contribution(String name, String brand, String category, String quantity, String description, byte[] image,
                           Long centerId) {
}
