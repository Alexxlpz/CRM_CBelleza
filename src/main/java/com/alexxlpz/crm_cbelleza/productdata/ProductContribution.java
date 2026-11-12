package com.alexxlpz.crm_cbelleza.productdata;

/**
 * Lo que Cuquora comparte de un producto que da de alta un centro: nunca su precio ni su stock.
 *
 * @param photo    foto JPEG del producto, o nula
 * @param centerId el centro que lo aporta (solo el número): la API lo apunta para saber de dónde salió cada dato
 */
public record ProductContribution(String name, String category, String description, byte[] photo, Long centerId) {
}
