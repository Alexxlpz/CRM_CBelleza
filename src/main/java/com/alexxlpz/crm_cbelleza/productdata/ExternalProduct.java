package com.alexxlpz.crm_cbelleza.productdata;

/**
 * Lo que sabe la base de datos de productos de un producto por su código de barras. Cualquier campo puede faltar.
 *
 * @param description descripción que escribió el centro que lo dio de alta, o nula
 * @param category categoría de Cuquora (Rostro, Cabello, Uñas o Cuerpo), o nula si no está clara
 * @param imageUrl foto del frente del envase, o nula
 * @param origin   quién lo dio a conocer: otro centro de Cuquora u Open Beauty Facts
 */
public record ExternalProduct(String name, String brand, String quantity, String description, String category,
                              String imageUrl, ProductOrigin origin) {
}
