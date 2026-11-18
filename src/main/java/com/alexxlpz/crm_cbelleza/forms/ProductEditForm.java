package com.alexxlpz.crm_cbelleza.forms;

import java.util.List;

/** Cambios de un producto creado por el centro, desde «Ver Info» del inventario. */
public record ProductEditForm(String name, String category, Double price, String description) {

    /** Las categorías del inventario (las mismas que al darlo de alta y que los filtros). */
    public static final List<String> CATEGORIES = List.of("Rostro", "Cabello", "Uñas", "Cuerpo");
}
