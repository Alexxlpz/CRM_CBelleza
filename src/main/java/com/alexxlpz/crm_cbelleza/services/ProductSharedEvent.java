package com.alexxlpz.crm_cbelleza.services;

/** Un centro ha dado de alta un producto con código de barras o le ha cambiado la foto: se comparte con la API de productos. */
public record ProductSharedEvent(Long productId) {
}
