package com.alexxlpz.productapi;

import java.util.Locale;
import java.util.Set;

/** Las categorías de producto de Cuquora y cómo sacarlas de las etiquetas de Open Beauty Facts. */
public final class Categories {

    public static final Set<String> ALL = Set.of("Rostro", "Cabello", "Uñas", "Cuerpo");

    private Categories() {
    }

    /** Categoría de Cuquora a partir de etiquetas como "en:face-creams"; nula si no está clara. */
    public static String fromTags(String tags) {
        if (tags == null || tags.isBlank()) return null;
        String t = tags.toLowerCase(Locale.ROOT);
        if (t.matches("(?s).*(nail|vernis|esmalte|manicur).*")) return "Uñas";
        if (t.matches("(?s).*(hair|shampoo|champu|conditioner|capillaire|cheveux).*")) return "Cabello";
        if (t.matches("(?s).*(face|facial|visage|eye|lip|make-up|makeup|serum|mascara|foundation|cleansing-milk).*")) return "Rostro";
        if (t.matches("(?s).*(body|shower|bath|deodorant|hand|sun|corps|soap).*")) return "Cuerpo";
        return null;
    }

    /** La categoría si es una de las de Cuquora; si no, nula. */
    public static String valid(String category) {
        if (category == null) return null;
        String value = category.strip();
        return ALL.contains(value) ? value : null;
    }
}
