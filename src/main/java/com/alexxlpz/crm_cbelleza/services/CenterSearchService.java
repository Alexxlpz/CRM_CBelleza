package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.dto.CenterRating;
import com.alexxlpz.crm_cbelleza.dto.CenterSearchCriteria;
import com.alexxlpz.crm_cbelleza.dto.CenterSummaryDTO;
import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours.Closure;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours.OpenState;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Búsqueda pública de centros: filtros, distancia al usuario, abierto o cerrado ahora, orden y paginación. */
@Service
@Transactional(readOnly = true)
public class CenterSearchService {

    private static final Comparator<CenterSummaryDTO> BY_DISTANCE_THEN_NAME = Comparator
            .comparing(CenterSummaryDTO::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CenterSummaryDTO::name, String.CASE_INSENSITIVE_ORDER);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private final CenterRepository centerRepository;
    private final TreatmentRepository treatmentRepository;
    private final AgendaBlockRepository agendaBlocks;
    private final OpeningHoursService openingHours;
    private final ReviewService reviewService;
    private final AgendaAvailabilityService availability;
    private final CenterService centerService;
    private final Clock clock;

    public CenterSearchService(CenterRepository centerRepository, TreatmentRepository treatmentRepository,
                               AgendaBlockRepository agendaBlocks, OpeningHoursService openingHours,
                               ReviewService reviewService, AgendaAvailabilityService availability,
                               CenterService centerService, Clock clock) {
        this.centerService = centerService;
        this.reviewService = reviewService;
        this.availability = availability;
        this.centerRepository = centerRepository;
        this.treatmentRepository = treatmentRepository;
        this.agendaBlocks = agendaBlocks;
        this.openingHours = openingHours;
        this.clock = clock;
    }

    public List<CenterSummaryDTO> search(CenterSearchCriteria criteria) {
        // Una sola consulta para las ofertas de todos los centros; cada una se filtra con el «hoy» de su centro
        Map<Long, List<Treatment>> offeredByCenter = treatmentRepository.findByOfferPriceIsNotNull().stream()
                .collect(Collectors.groupingBy(t -> t.getCenter().getId()));
        boolean onlyWithOffers = Boolean.TRUE.equals(criteria.withOffers());
        boolean onlyOpen = Boolean.TRUE.equals(criteria.openNow());
        boolean onlyTopRated = Boolean.TRUE.equals(criteria.topRated());

        List<Center> centers = centerRepository.findAll().stream()
                .filter(Center::isPublished)
                .filter(insideBoundingBox(criteria))
                .filter(matchesText(criteria.searchName(), true))
                .filter(matchesText(criteria.searchLocation(), false))
                .filter(offersTreatment(criteria.searchTreatment()))
                .toList();

        // Horarios y cierres de todos los centros encontrados, también en una consulta cada uno
        Map<Long, OpeningHours> hoursByCenter = openingHours.forCenters(centers.stream().map(Center::getId).toList());
        Map<Long, List<Closure>> closuresByCenter = centerClosures();
        Map<Long, CenterRating> ratings = reviewService.ratings();

        List<CenterSummaryDTO> sorted = centers.stream()
                .map(center -> {
                    LocalDateTime now = CenterClock.now(clock, center);
                    OpenState state = hoursByCenter.get(center.getId())
                            .stateAt(now, closuresByCenter.getOrDefault(center.getId(), List.of()));
                    return CenterSummaryDTO.of(center, distanceFrom(criteria, center),
                            activeOffers(center, offeredByCenter.getOrDefault(center.getId(), List.of())),
                            state.open(), hoursLabel(state, now), ratings.getOrDefault(center.getId(), CenterRating.NONE));
                })
                .filter(dto -> !onlyWithOffers || !dto.offers().isEmpty())
                .filter(dto -> !onlyOpen || dto.openNow())
                .filter(dto -> !onlyTopRated || (dto.rating() != null && dto.rating() >= CenterSearchCriteria.TOP_RATED))
                .sorted(BY_DISTANCE_THEN_NAME)
                .toList();

        int size = criteria.sizeOrDefault();
        int from = criteria.pageOrDefault() * size;
        if (from >= sorted.size()) {
            return List.of();
        }
        // El primer hueco necesita la agenda de cada centro: solo para los de esta página
        Map<Long, Center> byId = centers.stream().collect(Collectors.toMap(Center::getId, center -> center));
        Map<Long, String> slugs = centerService.shortSlugs();
        return sorted.subList(from, Math.min(from + size, sorted.size())).stream()
                .map(dto -> {
                    Center center = byId.get(dto.id());
                    return dto.withFirstSlotAndSlug(availability.firstFreeSlot(center, hoursByCenter.get(center.getId()))
                            .map(slot -> firstSlotLabel(slot, CenterClock.now(clock, center)))
                            .orElse(null), slugs.get(center.getId()));
                })
                .toList();
    }

    /** «hoy a las 17:30», «mañana a las 9:00», «el jueves a las 10:00», «el 5 de noviembre a las 10:00». */
    public static String firstSlotLabel(LocalDateTime slot, LocalDateTime now) {
        long days = slot.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay();
        String when;
        if (days == 0) {
            when = "hoy";
        } else if (days == 1) {
            when = "mañana";
        } else if (days < 7) {
            when = "el " + slot.getDayOfWeek().getDisplayName(TextStyle.FULL, SPANISH);
        } else {
            when = "el " + slot.getDayOfMonth() + " de " + slot.getMonth().getDisplayName(TextStyle.FULL, SPANISH);
        }
        return when + " a las " + TIME.format(slot);
    }

    /**
     * Cierres de todo el centro (festivos, vacaciones) de aquí a dos semanas, por centro. Las horas de los bloqueos
     * son locales de cada centro: la ventana se amplía un día por cada lado para cubrir cualquier zona horaria.
     */
    private Map<Long, List<Closure>> centerClosures() {
        LocalDateTime now = LocalDateTime.now(clock);
        return agendaBlocks.findCenterClosuresBetween(now.minusDays(1), now.plusDays(16)).stream()
                .collect(Collectors.groupingBy(b -> b.getCenter().getId(),
                        Collectors.mapping(b -> new Closure(b.getStartsAt(), b.getEndsAt()), Collectors.toList())));
    }

    /** «Abierto · cierra a las 20:00», «Cerrado · abre mañana a las 9:00», «Cerrado · abre el lunes a las 10:00»... */
    public static String hoursLabel(OpenState state, LocalDateTime now) {
        LocalDateTime at = state.changesAt();
        if (state.open()) {
            return "Abierto · cierra a las " + TIME.format(at);
        }
        if (at == null) {
            return "Cerrado";
        }
        long days = at.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay();
        String when;
        if (days == 0) {
            when = "";
        } else if (days == 1) {
            when = "mañana ";
        } else if (days < 7) {
            when = "el " + at.getDayOfWeek().getDisplayName(TextStyle.FULL, SPANISH) + " ";
        } else {
            when = "el " + at.getDayOfMonth() + " de " + at.getMonth().getDisplayName(TextStyle.FULL, SPANISH) + " ";
        }
        return "Cerrado · abre " + when + "a las " + TIME.format(at);
    }

    /** Ofertas en vigor hoy en el centro, de mayor a menor descuento. */
    private List<CenterSummaryDTO.Offer> activeOffers(Center center, List<Treatment> offered) {
        if (offered.isEmpty()) {
            return List.of();
        }
        LocalDate today = CenterClock.now(clock, center).toLocalDate();
        return offered.stream()
                .filter(t -> t.hasActiveOffer(today))
                .map(t -> new CenterSummaryDTO.Offer(t.getName(), t.getPrice(), t.getOfferPrice(), t.offerDiscountPercent()))
                .sorted(Comparator.comparingInt(CenterSummaryDTO.Offer::discountPercent).reversed())
                .toList();
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
