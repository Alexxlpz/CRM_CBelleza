package com.alexxlpz.productapi;

/** Lo que aporta un centro de Cuquora al dar de alta un producto con código: nunca su precio ni su stock. */
public record Contribution(String name, String brand, String category, String quantity, String description, byte[] image) {
}
