package com.alexxlpz.crm_cbelleza.dto;

import com.alexxlpz.crm_cbelleza.entities.Center;

import java.util.List;

/** Centro tal y como lo consume el buscador/mapa público. */
public record CenterSummaryDTO(Long id, String name, String address, String phone, String email,
                               Double distanceKm, Double latitude, Double longitude, String photoUrl,
                               List<Offer> offers) {

    /** Tratamiento en oferta, para la tarjeta del buscador. */
    public record Offer(String treatment, Double price, Double offerPrice, int discountPercent) {
    }

    public static CenterSummaryDTO of(Center c, Double distanceKm, List<Offer> offers) {
        return new CenterSummaryDTO(c.getId(), c.getName(), c.getAddress(), c.getPhone(), c.getEmail(),
                distanceKm, c.getLatitude(), c.getLongitude(), c.getPhotoUrl(), offers);
    }
}
