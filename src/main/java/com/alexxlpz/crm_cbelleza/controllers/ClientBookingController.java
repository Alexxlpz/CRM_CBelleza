package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.dto.ReviewFilter;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.LoyaltyService;
import com.alexxlpz.crm_cbelleza.services.OpeningHoursService;
import com.alexxlpz.crm_cbelleza.services.ReviewService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Zona del cliente: ficha del centro, solicitud de cita e historial de citas. Solo rol CLIENT. */
@Controller
public class ClientBookingController {

    private static final DateTimeFormatter CHANGED_AT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es"));

    /** Cuántas reseñas salen en el carrusel de la ficha; el resto, en «Ver las N opiniones». */
    private static final int LATEST_REVIEWS = 6;

    private final CenterService centerService;
    private final TreatmentService treatmentService;
    private final UserService userService;
    private final AppointmentService appointmentService;
    private final BookingService bookingService;
    private final OpeningHoursService openingHours;
    private final LoyaltyService loyaltyService;
    private final ReviewService reviewService;

    public ClientBookingController(CenterService centerService,
                                   TreatmentService treatmentService,
                                   UserService userService,
                                   AppointmentService appointmentService,
                                   BookingService bookingService,
                                   OpeningHoursService openingHours,
                                   LoyaltyService loyaltyService,
                                   ReviewService reviewService) {
        this.reviewService = reviewService;
        this.loyaltyService = loyaltyService;
        this.centerService = centerService;
        this.treatmentService = treatmentService;
        this.userService = userService;
        this.appointmentService = appointmentService;
        this.bookingService = bookingService;
        this.openingHours = openingHours;
    }

    /** Ruta antigua: el listado de centros es ahora público en /centers. */
    @GetMapping("/client/centers")
    public String listCenters() {
        return "redirect:/centers";
    }

    /**
     * Ficha del centro con la reserva, por su nombre en la dirección ({@code /client/centers/centro-historico}: nunca
     * el id). {@code tratamiento}: «Volver a reservar» lo deja ya elegido (si sigue en la carta).
     */
    @GetMapping("/client/centers/{slug}")
    public String centerDetails(@PathVariable String slug,
                                @RequestParam(name = "tratamiento", required = false) Long treatmentId,
                                Model model) {
        Center center = centerService.getPublishedBySlug(slug);
        Long id = center.getId();
        model.addAttribute("center", center);
        model.addAttribute("centerSlug", centerService.shortSlug(center));
        List<Treatment> treatments = treatmentService.getTreatmentsByCenter(id);
        LocalDate today = treatmentService.todayAt(id);
        model.addAttribute("treatments", treatments);
        model.addAttribute("selectedTreatmentId", treatments.stream()
                .map(Treatment::getId)
                .filter(candidate -> candidate.equals(treatmentId))
                .findFirst()
                .orElse(null));
        model.addAttribute("today", today);
        model.addAttribute("offerCount", treatments.stream().filter(t -> t.hasActiveOffer(today)).count());
        model.addAttribute("workers", userService.getWorkersByCenter(id));
        model.addAttribute("selectedWorkerId", null);
        model.addAttribute("openingHoursLines", openingHours.forCenter(id).describe());
        model.addAttribute("rating", reviewService.rating(id));
        model.addAttribute("ratingDistribution", reviewService.distribution(id));
        List<Review> reviews = reviewService.forCenter(id);
        model.addAttribute("reviewCount", reviews.size());
        model.addAttribute("latestReviews", reviews.stream().limit(LATEST_REVIEWS).toList());
        model.addAttribute("reviewAges", reviewService.ages(reviews.stream().limit(LATEST_REVIEWS).toList()));
        model.addAttribute("activePage", "centers");
        return "client/center_details";
    }

    /** «Ver las N opiniones»: todas las reseñas del centro, con orden y filtros por estrellas, comentario y tratamiento. */
    @GetMapping("/client/centers/{slug}/opiniones")
    public String centerReviews(@PathVariable String slug,
                                @RequestParam(name = "orden", required = false) String order,
                                @RequestParam(name = "estrellas", required = false) Integer stars,
                                @RequestParam(name = "comentario", defaultValue = "false") boolean withComment,
                                @RequestParam(name = "tratamiento", required = false) Long treatmentId,
                                Model model) {
        Center center = centerService.getPublishedBySlug(slug);
        Long id = center.getId();
        ReviewFilter filter = ReviewFilter.of(order, stars, withComment, treatmentId);
        List<Review> shown = reviewService.forCenter(id, filter);
        // Solo los tratamientos que tienen alguna reseña, por orden alfabético
        Map<Long, String> reviewedTreatments = new LinkedHashMap<>();
        reviewService.forCenter(id).stream()
                .map(review -> review.getAppointment().getTreatment())
                .sorted(Comparator.comparing(Treatment::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(treatment -> reviewedTreatments.putIfAbsent(treatment.getId(), treatment.getName()));
        model.addAttribute("center", center);
        model.addAttribute("centerSlug", centerService.shortSlug(center));
        model.addAttribute("rating", reviewService.rating(id));
        model.addAttribute("filter", filter);
        model.addAttribute("orders", ReviewFilter.Order.values());
        model.addAttribute("reviewedTreatments", reviewedTreatments);
        model.addAttribute("reviews", shown);
        model.addAttribute("reviewAges", reviewService.ages(shown));
        model.addAttribute("activePage", "centers");
        return "client/center_reviews";
    }

    @PostMapping("/client/booking")
    public String book(@AuthenticationPrincipal AppUserDetails client,
                       @RequestParam Long centerId,
                       @RequestParam Long treatmentId,
                       @RequestParam(required = false) Long workerId,
                       @RequestParam("dateTime") String dateTime,
                       RedirectAttributes redirect) {
        try {
            Appointment appointment = bookingService.requestBooking(client.getId(), centerId, treatmentId, workerId, parse(dateTime));
            redirect.addFlashAttribute("successMessage", appointment.getStatus() == AppointmentStatus.CONFIRMED
                    ? "¡Cita confirmada! " + appointment.getCenter().getName() + " te espera."
                    : "Solicitud enviada. Te avisaremos en cuanto " + appointment.getCenter().getName() + " la confirme.");
            return "redirect:/client/appointments";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("bookingError", e.getMessage());
            return "redirect:/client/centers/" + centerService.shortSlug(centerService.getCenterById(centerId));
        }
    }

    @GetMapping("/client/appointments")
    public String appointments(@AuthenticationPrincipal AppUserDetails client, Model model) {
        AppointmentService.ClientAppointments appointments = appointmentService.getClientAppointmentsSplit(client.getId());
        model.addAttribute("appointments", appointments);
        // Las que el cliente puede cambiar de hora él mismo (plazo de cada centro)
        Set<Long> changeable = appointments.upcoming().stream()
                .filter(app -> bookingService.clientRescheduleProblem(app).isEmpty())
                .map(Appointment::getId)
                .collect(Collectors.toSet());
        model.addAttribute("changeableIds", changeable);
        // Valoraciones: la nota de las que ya valoró y las completadas que aún puede valorar
        Map<Long, Integer> ratings = reviewService.ratingsOfClient(client.getId());
        model.addAttribute("reviewRatings", ratings);
        model.addAttribute("reviewableIds", reviewService.reviewableAmong(appointments.history(), ratings.keySet()));
        model.addAttribute("loyaltyCards", loyaltyService.cardsOfClient(client.getId()));
        model.addAttribute("centerSlugs", centerService.shortSlugs());
        model.addAttribute("activePage", "appointments");
        return "client/appointments";
    }

    /** Cambiar la hora (y el profesional) de una solicitud o cita confirmada, con el mismo selector que al reservar. */
    @GetMapping("/client/appointments/{id}/cambiar")
    public String changeForm(@AuthenticationPrincipal AppUserDetails client,
                             @PathVariable Long id,
                             Model model,
                             RedirectAttributes redirect) {
        Appointment appointment = appointmentService.getClientAppointment(client.getId(), id);
        String problem = bookingService.clientRescheduleProblem(appointment).orElse(null);
        if (problem != null) {
            redirect.addFlashAttribute("errorMessage", problem);
            return "redirect:/client/appointments";
        }
        Long centerId = appointment.getCenter().getId();
        model.addAttribute("appointment", appointment);
        model.addAttribute("center", appointment.getCenter());
        model.addAttribute("treatment", appointment.getTreatment());
        model.addAttribute("workers", appointment.getTreatment().doneBy(userService.getWorkersByCenter(centerId)));
        model.addAttribute("selectedWorkerId", appointment.getWorker() != null ? appointment.getWorker().getId() : null);
        model.addAttribute("openingHoursLines", openingHours.forCenter(centerId).describe());
        model.addAttribute("activePage", "appointments");
        return "client/change_appointment";
    }

    @PostMapping("/client/appointments/{id}/cambiar")
    public String change(@AuthenticationPrincipal AppUserDetails client,
                         @PathVariable Long id,
                         @RequestParam(required = false) Long workerId,
                         @RequestParam("dateTime") String dateTime,
                         RedirectAttributes redirect) {
        try {
            Appointment appointment = bookingService.rescheduleByClient(client.getId(), id, workerId, parse(dateTime));
            redirect.addFlashAttribute("successMessage", "Listo: tu cita es ahora el "
                    + appointment.getDateTime().format(CHANGED_AT) + ". Hemos avisado a " + appointment.getCenter().getName() + ".");
            return "redirect:/client/appointments";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("bookingError", e.getMessage());
            return "redirect:/client/appointments/" + id + "/cambiar";
        }
    }

    /** Reseña verificada de una cita completada: de 1 a 5 estrellas y un comentario opcional. */
    @PostMapping("/client/appointments/{id}/review")
    public String review(@AuthenticationPrincipal AppUserDetails client,
                         @PathVariable Long id,
                         @RequestParam(required = false) Integer rating,
                         @RequestParam(required = false) String comment,
                         RedirectAttributes redirect) {
        return clientAction(redirect, "¡Gracias por tu valoración! Ya aparece en la ficha del centro.",
                () -> reviewService.create(client.getId(), id, rating, comment));
    }

    /** El cliente elige una de las horas que le propuso el centro. */
    @PostMapping("/client/appointments/{id}/accept-proposal")
    public String acceptProposal(@AuthenticationPrincipal AppUserDetails client,
                                 @PathVariable Long id,
                                 @RequestParam Long proposalId,
                                 RedirectAttributes redirect) {
        return clientAction(redirect, "¡Cita confirmada! Te esperamos a la hora que has elegido.",
                () -> bookingService.acceptProposal(client.getId(), id, proposalId));
    }

    @PostMapping("/client/appointments/{id}/decline-proposal")
    public String declineProposal(@AuthenticationPrincipal AppUserDetails client,
                                  @PathVariable Long id,
                                  RedirectAttributes redirect) {
        return clientAction(redirect, "Hemos avisado al centro de que ninguna hora te viene bien. Puedes pedir otra cita cuando quieras.",
                () -> bookingService.declineProposal(client.getId(), id));
    }

    @PostMapping("/client/appointments/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserDetails client,
                         @PathVariable Long id,
                         RedirectAttributes redirect) {
        return clientAction(redirect, "Cita anulada. Hemos avisado al centro.",
                () -> bookingService.cancelByClient(client.getId(), id));
    }

    private static String clientAction(RedirectAttributes redirect, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("successMessage", success);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/client/appointments";
    }

    private static LocalDateTime parse(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessRuleException("Selecciona un día y una hora válidos.");
        }
    }
}
