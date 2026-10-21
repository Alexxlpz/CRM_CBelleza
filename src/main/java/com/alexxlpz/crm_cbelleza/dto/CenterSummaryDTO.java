package com.alexxlpz.crm_cbelleza.dto;

import com.alexxlpz.crm_cbelleza.entities.Center;

import java.util.List;

/**
 * Centro tal y como lo consume el buscador/mapa público. {@code openNow} y {@code hoursLabel} («Abierto · cierra
 * a las 20:00», «Cerrado · abre mañana a las 9:00») salen de su horario y sus cierres, en su zona horaria.
 */
public record CenterSummaryDTO(Long id, String name, String address, String phone, String email,
                               Double distanceKm, Double latitude, Double longitude, String photoUrl,
                               List<Offer> offers, boolean openNow, String hoursLabel) {

    /** Tratamiento en oferta, para la tarjeta del buscador. */
    public record Offer(String treatment, Double price, Double offerPrice, int discountPercent) {
    }

    public static CenterSummaryDTO of(Center c, Double distanceKm, List<Offer> offers, boolean openNow, String hoursLabel) {
        return new CenterSummaryDTO(c.getId(), c.getName(), c.getAddress(), c.getPhone(), c.getEmail(),
                distanceKm, c.getLatitude(), c.getLongitude(), c.getPhotoUrl(), offers, openNow, hoursLabel);
    }
}
