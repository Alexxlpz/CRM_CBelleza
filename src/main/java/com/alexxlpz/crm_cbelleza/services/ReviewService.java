package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.dto.CenterRating;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
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

    private final ReviewRepository reviews;
    private final AppointmentRepository appointments;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, AppointmentRepository appointments, Clock clock) {
        this.reviews = reviews;
        this.appointments = appointments;
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
