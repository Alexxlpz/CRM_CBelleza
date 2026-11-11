package com.alexxlpz.crm_cbelleza.productdata;

import java.util.Locale;

/** Categoría de Cuquora a partir de las etiquetas de Open Beauty Facts ("en:face-creams", ...). */
public final class ProductCategories {

    private ProductCategories() {
    }

    /** La categoría, o nula si no está clara. */
    public static String fromTags(String tags) {
        if (tags == null || tags.isBlank()) return null;
        String t = tags.toLowerCase(Locale.ROOT);
        if (t.matches("(?s).*(nail|vernis|esmalte|manicur).*")) return "Uñas";
        if (t.matches("(?s).*(hair|shampoo|champu|conditioner|capillaire|cheveux).*")) return "Cabello";
        if (t.matches("(?s).*(face|facial|visage|eye|lip|make-up|makeup|serum|mascara|foundation|cleansing-milk).*")) return "Rostro";
        if (t.matches("(?s).*(body|shower|bath|deodorant|hand|sun|corps|soap).*")) return "Cuerpo";
        return null;
    }
}
