package com.alexxlpz.crm_cbelleza.entities;

/** Cómo suma puntos un centro. La etiqueta es la que se ve en «Mi Centro». */
public enum LoyaltyMode {
    OFF("Desactivada"),
    /** Puntos por cada euro gastado en tratamientos y productos. */
    PER_EURO("Por euro gastado"),
    /** Puntos por cada tratamiento realizado y cada unidad de producto comprada. */
    PER_ITEM("Por tratamiento o producto");

    private final String label;

    LoyaltyMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Puntos que da un gasto de {@code amount} euros en {@code units} servicios o productos. */
    public int pointsFor(double amount, int units, int pointsPerUnit) {
        return switch (this) {
            case OFF -> 0;
            case PER_EURO -> (int) Math.floor(amount * pointsPerUnit + 1e-9);
            case PER_ITEM -> units * pointsPerUnit;
        };
    }
}
