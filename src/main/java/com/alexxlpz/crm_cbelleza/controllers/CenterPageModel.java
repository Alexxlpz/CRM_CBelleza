package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.config.SiteModelAdvice;
import com.alexxlpz.crm_cbelleza.dto.CenterRating;
import com.alexxlpz.crm_cbelleza.dto.ReviewFilter;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.CenterStructuredData;
import com.alexxlpz.crm_cbelleza.services.OpeningHoursService;
import com.alexxlpz.crm_cbelleza.services.ReviewService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que comparten la ficha pública del centro ({@code /c/nombre}) y la del cliente ({@code /client/centers/nombre}):
 * datos, carta, horario y opiniones. {@code publicPage} decide a dónde llevan los enlaces de las opiniones (la pública
 * no pide sesión; reservar, sí).
 */
@Component
class CenterPageModel {

    /** Cuántas reseñas salen en el carrusel de la ficha; el resto, en «Ver las N opiniones». */
    private static final int LATEST_REVIEWS = 6;

    private final CenterService centerService;
    private final TreatmentService treatmentService;
    private final OpeningHoursService openingHours;
    private final ReviewService reviewService;
    private final CenterStructuredData structuredData;
    private final SiteModelAdvice site;

    CenterPageModel(CenterService centerService, TreatmentService treatmentService,
                    OpeningHoursService openingHours, ReviewService reviewService,
                    CenterStructuredData structuredData, SiteModelAdvice site) {
        this.centerService = centerService;
        this.treatmentService = treatmentService;
        this.openingHours = openingHours;
        this.reviewService = reviewService;
        this.structuredData = structuredData;
        this.site = site;
    }

    /**
     * Datos, carta, horario y las últimas opiniones. Devuelve la carta para que la ficha del cliente la reutilice.
     * La pública lleva además su dirección canónica y los datos estructurados para Google ({@link CenterStructuredData}).
     */
    List<Treatment> addCenter(Model model, Center center, boolean publicPage) {
        Long id = center.getId();
        List<Treatment> treatments = treatmentService.getTreatmentsByCenter(id);
        LocalDate today = treatmentService.todayAt(id);
        addLinks(model, center, publicPage);
        model.addAttribute("treatments", treatments);
        model.addAttribute("today", today);
        model.addAttribute("offerCount", treatments.stream().filter(t -> t.hasActiveOffer(today)).count());
        OpeningHours hours = openingHours.forCenter(id);
        CenterRating rating = reviewService.rating(id);
        model.addAttribute("openingHoursLines", hours.describe());
        model.addAttribute("rating", rating);
        model.addAttribute("ratingDistribution", reviewService.distribution(id));
        List<Review> reviews = reviewService.forCenter(id);
        List<Review> latest = reviews.stream().limit(LATEST_REVIEWS).toList();
        model.addAttribute("reviewCount", reviews.size());
        model.addAttribute("latestReviews", latest);
        model.addAttribute("reviewAges", reviewService.ages(latest));
        if (publicPage) {
            String siteUrl = site.siteUrl();
            String pageUrl = siteUrl + "/c/" + centerService.shortSlug(center);
            model.addAttribute("canonicalUrl", pageUrl);
            if (center.getPhotoUrl() != null) {
                model.addAttribute("pageImage", siteUrl + center.getPhotoUrl());
            }
            model.addAttribute("structuredData",
                    structuredData.jsonLd(center, pageUrl, siteUrl, treatments, today, hours, rating, latest));
        }
        return treatments;
    }

    /** «Ver las N opiniones»: todas las reseñas del centro, con orden y filtros por estrellas, comentario y tratamiento. */
    void addReviews(Model model, Center center, boolean publicPage,
                    String order, Integer stars, boolean withComment, Long treatmentId) {
        Long id = center.getId();
        ReviewFilter filter = ReviewFilter.of(order, stars, withComment, treatmentId);
        List<Review> shown = reviewService.forCenter(id, filter);
        // Solo los tratamientos que tienen alguna reseña, por orden alfabético
        Map<Long, String> reviewedTreatments = new LinkedHashMap<>();
        reviewService.forCenter(id).stream()
                .map(review -> review.getAppointment().getTreatment())
                .sorted(Comparator.comparing(Treatment::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(treatment -> reviewedTreatments.putIfAbsent(treatment.getId(), treatment.getName()));
        addLinks(model, center, publicPage);
        model.addAttribute("rating", reviewService.rating(id));
        model.addAttribute("filter", filter);
        model.addAttribute("orders", ReviewFilter.Order.values());
        model.addAttribute("reviewedTreatments", reviewedTreatments);
        model.addAttribute("reviews", shown);
        model.addAttribute("reviewAges", reviewService.ages(shown));
    }

    /** {@code centerUrl}: la ficha; {@code reviewsUrl}: todas sus opiniones; {@code bookUrl}: la reserva (pide sesión). */
    private void addLinks(Model model, Center center, boolean publicPage) {
        String slug = centerService.shortSlug(center);
        String centerUrl = (publicPage ? "/c/" : "/client/centers/") + slug;
        model.addAttribute("center", center);
        model.addAttribute("centerSlug", slug);
        model.addAttribute("centerUrl", centerUrl);
        model.addAttribute("reviewsUrl", centerUrl + "/opiniones");
        model.addAttribute("bookUrl", "/client/centers/" + slug);
        model.addAttribute("activePage", "centers");
    }
}
