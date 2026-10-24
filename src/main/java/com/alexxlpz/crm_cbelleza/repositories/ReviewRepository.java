package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Review;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Reseñas del centro, de la más nueva a la más antigua, con lo que se pinta de cada una. */
    @EntityGraph(attributePaths = {"client", "appointment", "appointment.treatment", "appointment.worker"})
    List<Review> findByCenterIdOrderByCreatedAtDesc(Long centerId);

    @EntityGraph(attributePaths = {"client", "appointment", "appointment.treatment"})
    Optional<Review> findByIdAndCenterId(Long id, Long centerId);

    boolean existsByAppointmentId(Long appointmentId);

    @Query("SELECT r.appointment.id, r.rating FROM Review r WHERE r.client.id = :clientId")
    List<Object[]> findRatingsByClient(@Param("clientId") Long clientId);

    /** Nota media y número de reseñas de cada centro: [centerId, media, total]. */
    @Query("SELECT r.center.id, AVG(r.rating), COUNT(r) FROM Review r GROUP BY r.center.id")
    List<Object[]> ratingByCenter();

    /** Cuántas reseñas hay de cada nota en un centro: [nota, total]. */
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.center.id = :centerId GROUP BY r.rating")
    List<Object[]> distribution(@Param("centerId") Long centerId);

    long countByCenterIdAndReplyIsNull(Long centerId);

    @EntityGraph(attributePaths = {"client"})
    List<Review> findByCenterIdAndCreatedAtAfter(Long centerId, LocalDateTime since);

    /** Nota media y número de reseñas de cada profesional, por las citas de un periodo: [workerId, media, total]. */
    @Query("SELECT r.appointment.worker.id, AVG(r.rating), COUNT(r) FROM Review r WHERE r.center.id = :centerId"
            + " AND r.appointment.worker IS NOT NULL AND r.appointment.dateTime BETWEEN :from AND :to GROUP BY r.appointment.worker.id")
    List<Object[]> ratingByWorker(@Param("centerId") Long centerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Respuestas que ha recibido el cliente desde {@code since} (su campana). */
    @EntityGraph(attributePaths = {"center", "appointment", "appointment.treatment"})
    List<Review> findByClientIdAndRepliedAtAfter(Long clientId, LocalDateTime since);
}
