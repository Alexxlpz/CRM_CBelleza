package com.alexxlpz.crm_cbelleza.dto;

import java.util.Locale;

/** Nota media de un centro (de 1 a 5) y cuántas reseñas la forman. Sin reseñas, {@code count} es 0. */
public record CenterRating(double average, long count) {

    public static final CenterRating NONE = new CenterRating(0, 0);

    public boolean hasReviews() {
        return count > 0;
    }

    /** «4,7» */
    public String averageLabel() {
        return String.format(Locale.forLanguageTag("es"), "%.1f", average);
    }

    /** «1 reseña» / «23 reseñas» */
    public String countLabel() {
        return count == 1 ? "1 reseña" : count + " reseñas";
    }

    /** La media redondeada a la media estrella más cercana (4,3 → 4,5), para pintar las estrellas. */
    public double roundedToHalf() {
        return Math.round(average * 2) / 2.0;
    }
}
