package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.dto.CenterRating;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Datos estructurados (JSON-LD de schema.org) de la ficha pública del centro ({@code /c/nombre}): con ellos Google
 * entiende que la página es un salón de belleza con dirección, horario, precios y nota media, y puede enseñarla con
 * estrellas en los resultados. Solo cuenta lo que se ve en la página (Google lo exige).
 *
 * @see <a href="https://developers.google.com/search/docs/appearance/structured-data/local-business">LocalBusiness</a>
 */
@Service
public class CenterStructuredData {

    /** Moneda de los precios de la carta (la app los enseña en euros). */
    static final String CURRENCY = "EUR";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ObjectMapper json;

    public CenterStructuredData(ObjectMapper json) {
        this.json = json;
    }

    /**
     * El bloque JSON listo para ir dentro de {@code <script type="application/ld+json">}.
     *
     * @param pageUrl dirección absoluta de la ficha ({@code https://cuquora.com/c/centro-historico})
     * @param siteUrl dirección del sitio, para hacer absoluta la foto
     */
    public String jsonLd(Center center, String pageUrl, String siteUrl, List<Treatment> treatments, LocalDate today,
                         OpeningHours hours, CenterRating rating, List<Review> latestReviews) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("@context", "https://schema.org");
        data.put("@type", "BeautySalon");
        data.put("@id", pageUrl + "#centro");
        data.put("name", center.getName());
        data.put("url", pageUrl);
        putIfPresent(data, "telephone", center.getPhone());
        putIfPresent(data, "email", center.getEmail());
        if (center.getPhotoUrl() != null) {
            data.put("image", siteUrl + center.getPhotoUrl());
        }
        if (center.getAddress() != null && !center.getAddress().isBlank()) {
            data.put("address", Map.of("@type", "PostalAddress", "streetAddress", center.getAddress()));
        }
        if (center.getLatitude() != null && center.getLongitude() != null) {
            data.put("geo", Map.of("@type", "GeoCoordinates",
                    "latitude", center.getLatitude(), "longitude", center.getLongitude()));
        }
        List<Map<String, Object>> opening = hours.periods().stream()
                .map(p -> Map.<String, Object>of("@type", "OpeningHoursSpecification",
                        "dayOfWeek", "https://schema.org/" + dayName(p.day()),
                        "opens", p.opens().format(TIME), "closes", p.closes().format(TIME)))
                .toList();
        if (!opening.isEmpty()) {
            data.put("openingHoursSpecification", opening);
        }
        if (rating.hasReviews()) {
            data.put("aggregateRating", Map.of("@type", "AggregateRating",
                    "ratingValue", BigDecimal.valueOf(rating.average()).setScale(1, RoundingMode.HALF_UP),
                    "reviewCount", rating.count(), "bestRating", 5, "worstRating", 1));
            data.put("review", latestReviews.stream().map(CenterStructuredData::review).toList());
        }
        List<Map<String, Object>> offers = new ArrayList<>();
        for (Treatment t : treatments) {
            Map<String, Object> service = new LinkedHashMap<>();
            service.put("@type", "Service");
            service.put("name", t.getName());
            putIfPresent(service, "description", t.getDescription());
            Map<String, Object> offer = new LinkedHashMap<>();
            offer.put("@type", "Offer");
            offer.put("price", BigDecimal.valueOf(t.priceOn(today)).setScale(2, RoundingMode.HALF_UP));
            offer.put("priceCurrency", CURRENCY);
            offer.put("itemOffered", service);
            offers.add(offer);
        }
        if (!offers.isEmpty()) {
            data.put("hasOfferCatalog", Map.of("@type", "OfferCatalog", "name", "Tratamientos", "itemListElement", offers));
        }
        try {
            // «<» escapado: un «</script>» dentro de un texto no puede cerrar el bloque antes de tiempo
            return json.writeValueAsString(data).replace("<", "\\u003c");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se han podido generar los datos estructurados", e);
        }
    }

    private static Map<String, Object> review(Review review) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("@type", "Review");
        data.put("author", Map.of("@type", "Person", "name", review.getAuthorName()));
        data.put("reviewRating", Map.of("@type", "Rating", "ratingValue", review.getRating(), "bestRating", 5));
        data.put("datePublished", review.getCreatedAt().toLocalDate().toString());
        putIfPresent(data, "reviewBody", review.getComment());
        return data;
    }

    /** MONDAY → «Monday», como los nombra schema.org. */
    private static String dayName(DayOfWeek day) {
        String name = day.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }
}
