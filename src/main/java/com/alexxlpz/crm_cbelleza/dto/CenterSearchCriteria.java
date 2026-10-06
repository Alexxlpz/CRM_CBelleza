package com.alexxlpz.crm_cbelleza.dto;

/** Filtros del buscador público de centros (/api/centers). Todos son opcionales. */
public record CenterSearchCriteria(Double lat, Double lon,
                                   Double north, Double south, Double east, Double west,
                                   String searchName, String searchLocation, String searchTreatment,
                                   Integer page, Integer size) {

    public boolean hasBoundingBox() {
        return north != null && south != null && east != null && west != null;
    }

    public boolean hasOrigin() {
        return lat != null && lon != null;
    }

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null || size <= 0 ? 10 : Math.min(size, 50);
    }
}
