package com.alexxlpz.productapi;

import java.util.Optional;

/** Base de datos abierta de cosméticos donde se busca un código que todavía no está aquí. */
public interface OpenBeautyFacts {

    Optional<ImportedProduct> find(String gtin);

    /** La foto del envase, solo si es de sus propios servidores. */
    Optional<byte[]> image(String url);
}
