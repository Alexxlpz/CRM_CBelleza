package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.dto.CenterRating;
import com.alexxlpz.crm_cbelleza.dto.ReviewFilter;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ReviewRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reseñas verificadas: el cliente valora una cita completada (una vez, hasta {@value #DAYS_TO_REVIEW} días después)
 * y el dueño del centro puede responder en público. Nadie puede borrarlas desde la web.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    /** Días después de la cita en los que todavía se puede valorar. */
    public static final int DAYS_TO_REVIEW = 90;

    /** Días después de la cita en los que, al entrar, se le propone valorarla (como el recordatorio de la campana). */
    public static final int DAYS_TO_PROMPT = 7;

    private final ReviewRepository reviews;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, AppointmentRepository appointments, UserRepository users, Clock clock) {
        this.reviews = reviews;
        this.appointments = appointments;
        this.users = users;
        this.clock = clock;
    }

    /** Por qué no se puede valorar esta cita (aún no está hecha, ya tiene reseña, pasó el plazo); vacío si se puede. */
    public Optional<String> reviewProblem(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            return Optional.of("Solo se pueden valorar las citas completadas.");
        }
        if (reviews.existsByAppointmentId(appointment.getId())) {
            return Optional.of("Ya has valorado esta cita.");
        }
        LocalDateTime now = CenterClock.now(clock, appointment.getCenter());
        if (appointment.getDateTime().isBefore(now.minusDays(DAYS_TO_REVIEW))) {
            return Optional.of("Ha pasado el plazo para valorar esta cita (" + DAYS_TO_REVIEW + " días).");
        }
        return Optional.empty();
    }

    /**
     * De las citas del historial, las que el cliente todavía puede valorar ({@code reviewed}: las que ya valoró,
     * de {@link #ratingsOfClient}). Sin una consulta por cita.
     */
    public Set<Long> reviewableAmong(List<Appointment> history, Set<Long> reviewed) {
        Set<Long> reviewable = new HashSet<>();
        for (Appointment appointment : history) {
            if (appointment.getStatus() == AppointmentStatus.COMPLETED
                    && !reviewed.contains(appointment.getId())
                    && !appointment.getDateTime().isBefore(CenterClock.now(clock, appointment.getCenter()).minusDays(DAYS_TO_REVIEW))) {
                reviewable.add(appointment.getId());
            }
        }
        return reviewable;
    }

    /**
     * La cita que se propone valorar al cliente con el aviso que sale al entrar: la última completada de los
     * últimos {@value #DAYS_TO_PROMPT} días que aún puede valorar y de la que todavía no se le ha avisado.
     */
    public Optional<Appointment> toPrompt(Long clientId) {
        if (users.findById(clientId).map(User::isReviewPromptsOff).orElse(true)) {
            return Optional.empty();
        }
        Set<Long> reviewed = ratingsOfClient(clientId).keySet();
        return appointments.findByClientIdOrderByDateTimeDesc(clientId).stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.COMPLETED
                        && appointment.getReviewPromptedAt() == null
                        && !reviewed.contains(appointment.getId()))
                .filter(appointment -> {
                    LocalDateTime now = CenterClock.now(clock, appointment.getCenter());
                    return appointment.getDateTime().isBefore(now)
                            && appointment.getDateTime().isAfter(now.minusDays(DAYS_TO_PROMPT));
                })
                .findFirst();
    }

    /** El aviso para valorar ya se ha enseñado: no vuelve a salir para esta cita (la campana lo sigue recordando). */
    @Transactional
    public void markPrompted(Long clientId, Long appointmentId) {
        appointments.findByIdAndClientId(appointmentId, clientId)
                .filter(appointment -> appointment.getReviewPromptedAt() == null)
                .ifPresent(appointment -> appointment.setReviewPromptedAt(LocalDateTime.now(clock)));
    }

    /** «No mostrar más»: el cliente no quiere que se le vuelva a proponer valorar ninguna cita. */
    @Transactional
    public void stopPrompts(Long clientId) {
        users.findById(clientId).ifPresent(user -> user.setReviewPromptsOff(true));
    }

    /** El cliente valora su cita completada. */
    @Transactional
    public Review create(Long clientId, Long appointmentId, Integer rating, String comment) {
        Appointment appointment = appointments.findByIdAndClientId(appointmentId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("La cita no existe."));
        reviewProblem(appointment).ifPresent(problem -> {
            throw new BusinessRuleException(problem);
        });
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessRuleException("Elige de 1 a 5 estrellas.");
        }
        return reviews.save(Review.builder()
                .appointment(appointment)
                .center(appointment.getCenter())
                .client(appointment.getClient())
                .rating(rating)
                .comment(text(comment, "El comentario"))
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    /** El centro responde a una reseña (o cambia su respuesta). Una respuesta vacía la quita. */
    @Transactional
    public Review reply(Long centerId, Long reviewId, String reply) {
        Review review = reviews.findByIdAndCenterId(reviewId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("La reseña no existe en tu centro."));
        String text = text(reply, "La respuesta");
        review.setReply(text);
        review.setRepliedAt(text == null ? null : LocalDateTime.now(clock));
        return review;
    }

    public List<Review> forCenter(Long centerId) {
        return reviews.findByCenterIdOrderByCreatedAtDesc(centerId);
    }

    /** Las reseñas del centro ordenadas y filtradas como pide la pantalla «Todas las opiniones». */
    public List<Review> forCenter(Long centerId, ReviewFilter filter) {
        Comparator<Review> newest = Comparator.comparing(Review::getCreatedAt).reversed();
        Comparator<Review> order = switch (filter.order()) {
            case RECENT -> newest;
            case BEST -> Comparator.comparing(Review::getRating).reversed().thenComparing(newest);
            case WORST -> Comparator.comparing(Review::getRating).thenComparing(newest);
        };
        return forCenter(centerId).stream()
                .filter(review -> filter.stars() == null || filter.stars().equals(review.getRating()))
                .filter(review -> !filter.withComment() || review.getComment() != null)
                .filter(review -> filter.treatmentId() == null
                        || filter.treatmentId().equals(review.getAppointment().getTreatment().getId()))
                .sorted(order)
                .toList();
    }

    /** Hace cuánto se escribió cada reseña («hace 3 días»), por id de reseña. */
    public Map<Long, String> ages(List<Review> list) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, String> byReview = new HashMap<>();
        for (Review review : list) {
            byReview.put(review.getId(), age(review.getCreatedAt().toLocalDate(), today));
        }
        return byReview;
    }

    /** «hoy», «ayer», «hace 3 días», «hace 2 semanas», «hace 1 mes», «hace 2 años». */
    public static String age(LocalDate day, LocalDate today) {
        long days = ChronoUnit.DAYS.between(day, today);
        if (days <= 0) {
            return "hoy";
        }
        if (days == 1) {
            return "ayer";
        }
        if (days < 7) {
            return "hace " + days + " días";
        }
        if (days < 30) {
            long weeks = days / 7;
            return weeks == 1 ? "hace 1 semana" : "hace " + weeks + " semanas";
        }
        Period period = Period.between(day, today);
        if (period.getYears() == 0) {
            long months = Math.max(1, period.getMonths());
            return months == 1 ? "hace 1 mes" : "hace " + months + " meses";
        }
        return period.getYears() == 1 ? "hace 1 año" : "hace " + period.getYears() + " años";
    }

    public CenterRating rating(Long centerId) {
        return ratings().getOrDefault(centerId, CenterRating.NONE);
    }

    /** Nota de todos los centros con reseñas, en una consulta (buscador). */
    public Map<Long, CenterRating> ratings() {
        Map<Long, CenterRating> byCenter = new HashMap<>();
        for (Object[] row : reviews.ratingByCenter()) {
            byCenter.put((Long) row[0], new CenterRating(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return byCenter;
    }

    /** Cuántas reseñas hay de cada nota, de 5 a 1 estrellas (para las barras de la ficha del centro). */
    public Map<Integer, Long> distribution(Long centerId) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            counts.put(stars, 0L);
        }
        for (Object[] row : reviews.distribution(centerId)) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    /** Nota que puso el cliente a cada una de sus citas valoradas: id de la cita → estrellas. */
    public Map<Long, Integer> ratingsOfClient(Long clientId) {
        Map<Long, Integer> byAppointment = new HashMap<>();
        for (Object[] row : reviews.findRatingsByClient(clientId)) {
            byAppointment.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return byAppointment;
    }

    public long countUnanswered(Long centerId) {
        return reviews.countByCenterIdAndReplyIsNull(centerId);
    }

    /** Reseñas que ha recibido el centro desde {@code since} (campana del trabajador). */
    public List<Review> receivedSince(Long centerId, LocalDateTime since) {
        return reviews.findByCenterIdAndCreatedAtAfter(centerId, since);
    }

    private static String text(String value, String what) {
        String text = FormText.trimToNull(value);
        if (text != null && text.length() > Review.MAX_TEXT) {
            throw new BusinessRuleException(what + " no puede pasar de " + Review.MAX_TEXT + " caracteres.");
        }
        return text;
    }
}
