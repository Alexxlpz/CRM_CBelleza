package com.alexxlpz.productapi;

/** Lo que trae Open Beauty Facts de un producto. Cualquier campo puede faltar salvo el nombre. */
public record ImportedProduct(String name, String brand, String quantity, String category, String imageUrl) {
}
