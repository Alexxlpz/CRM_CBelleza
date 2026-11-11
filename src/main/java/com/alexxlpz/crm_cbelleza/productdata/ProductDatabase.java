package com.alexxlpz.crm_cbelleza.productdata;

import java.util.Optional;

/**
 * Base de datos de productos de belleza consultada por código de barras: la API de productos propia de Cuquora
 * ({@link ProductApiClient}) o, si no está configurada, Open Beauty Facts directamente ({@link OpenBeautyFacts}).
 * Si no responde o no conoce el código, devuelve vacío: el centro rellena el producto a mano.
 */
public interface ProductDatabase {

    Optional<ExternalProduct> find(String barcode);

    /** La foto del producto (los bytes tal cual), solo si viene del propio servicio. */
    Optional<byte[]> image(String imageUrl);

    /** Comparte un producto que acaba de dar de alta un centro, para que el siguiente lo encuentre. */
    default void contribute(String barcode, ProductContribution product) {
        // Open Beauty Facts no recibe nada de Cuquora
    }
}
