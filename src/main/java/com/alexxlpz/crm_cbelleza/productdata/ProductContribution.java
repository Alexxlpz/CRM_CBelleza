package com.alexxlpz.crm_cbelleza.productdata;

/**
 * Lo que Cuquora comparte de un producto que da de alta un centro: nunca su precio ni su stock.
 *
 * @param photo foto JPEG del producto, o nula
 */
public record ProductContribution(String name, String category, String description, byte[] photo) {
}
