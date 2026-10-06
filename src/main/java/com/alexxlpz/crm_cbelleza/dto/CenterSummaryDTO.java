package com.alexxlpz.crm_cbelleza.dto;

import com.alexxlpz.crm_cbelleza.entities.Center;

/** Centro tal y como lo consume el buscador/mapa público. */
public record CenterSummaryDTO(Long id, String name, String address, String phone, String email,
                               Double distanceKm, Double latitude, Double longitude) {

    public static CenterSummaryDTO of(Center c, Double distanceKm) {
        return new CenterSummaryDTO(c.getId(), c.getName(), c.getAddress(), c.getPhone(), c.getEmail(),
                distanceKm, c.getLatitude(), c.getLongitude());
    }
}
