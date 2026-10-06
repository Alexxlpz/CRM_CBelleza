package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.dto.CenterSearchCriteria;
import com.alexxlpz.crm_cbelleza.dto.CenterSummaryDTO;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Búsqueda pública de centros: filtros, distancia al usuario, orden y paginación. */
@Service
@Transactional(readOnly = true)
public class CenterSearchService {

    private static final Comparator<CenterSummaryDTO> BY_DISTANCE_THEN_NAME = Comparator
            .comparing(CenterSummaryDTO::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CenterSummaryDTO::name, String.CASE_INSENSITIVE_ORDER);

    private final CenterRepository centerRepository;
    private final TreatmentRepository treatmentRepository;

    public CenterSearchService(CenterRepository centerRepository, TreatmentRepository treatmentRepository) {
        this.centerRepository = centerRepository;
        this.treatmentRepository = treatmentRepository;
    }

    public List<CenterSummaryDTO> search(CenterSearchCriteria criteria) {
        Stream<Center> centers = centerRepository.findAll().stream()
                .filter(insideBoundingBox(criteria))
                .filter(matchesText(criteria.searchName(), true))
                .filter(matchesText(criteria.searchLocation(), false))
                .filter(offersTreatment(criteria.searchTreatment()));

        List<CenterSummaryDTO> sorted = centers
                .map(center -> CenterSummaryDTO.of(center, distanceFrom(criteria, center)))
                .sorted(BY_DISTANCE_THEN_NAME)
                .toList();

        int size = criteria.sizeOrDefault();
        int from = criteria.pageOrDefault() * size;
        if (from >= sorted.size()) {
            return List.of();
        }
        return sorted.subList(from, Math.min(from + size, sorted.size()));
    }

    private static Predicate<Center> insideBoundingBox(CenterSearchCriteria c) {
        if (!c.hasBoundingBox()) {
            return center -> true;
        }
        return center -> {
            if (center.getLatitude() == null || center.getLongitude() == null) return false;
            boolean latOk = center.getLatitude() <= c.north() && center.getLatitude() >= c.south();
            boolean lonOk = c.west() <= c.east()
                    ? center.getLongitude() >= c.west() && center.getLongitude() <= c.east()
                    : center.getLongitude() >= c.west() || center.getLongitude() <= c.east();
            return latOk && lonOk;
        };
    }

    /** Busca el texto en la dirección y, si {@code includeName}, también en el nombre. */
    private static Predicate<Center> matchesText(String query, boolean includeName) {
        if (query == null || query.isBlank()) {
            return center -> true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return center -> contains(center.getAddress(), q) || (includeName && contains(center.getName(), q));
    }

    private Predicate<Center> offersTreatment(String treatmentType) {
        if (treatmentType == null || treatmentType.isBlank()) {
            return center -> true;
        }
        try {
            TreatmentType type = TreatmentType.valueOf(treatmentType.trim().toUpperCase(Locale.ROOT));
            Set<Long> centerIds = treatmentRepository.findCenterIdsOfferingType(type);
            return center -> centerIds.contains(center.getId());
        } catch (IllegalArgumentException e) {
            return center -> true; // tipo desconocido: se ignora el filtro
        }
    }

    private static Double distanceFrom(CenterSearchCriteria c, Center center) {
        if (!c.hasOrigin() || center.getLatitude() == null || center.getLongitude() == null) {
            return null;
        }
        return GeoDistance.km(c.lat(), c.lon(), center.getLatitude(), center.getLongitude());
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
