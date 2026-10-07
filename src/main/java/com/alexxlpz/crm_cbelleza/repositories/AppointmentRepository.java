package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Carga también tratamiento, cliente y trabajador para evitar una consulta por cita al pintar listas. */
    @EntityGraph(attributePaths = {"treatment", "client", "worker"})
    List<Appointment> findByCenterIdOrderByDateTimeDesc(Long centerId);

    @EntityGraph(attributePaths = {"treatment", "center", "worker"})
    List<Appointment> findByClientIdOrderByDateTimeDesc(Long clientId);

    @EntityGraph(attributePaths = {"treatment", "client", "worker"})
    List<Appointment> findByCenterIdAndClientIdOrderByDateTimeDesc(Long centerId, Long clientId);

    @EntityGraph(attributePaths = {"treatment"})
    List<Appointment> findByCenterIdAndStatusIn(Long centerId, Collection<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"treatment"})
    List<Appointment> findByCenterIdAndStatusInAndDateTimeAfterOrderByDateTimeAsc(
            Long centerId, Collection<AppointmentStatus> statuses, LocalDateTime dateTime);

    @EntityGraph(attributePaths = {"treatment", "client"})
    Optional<Appointment> findByIdAndCenterId(Long id, Long centerId);

    long countByClientId(Long clientId);

    boolean existsByCenterIdAndClientIdAndStatusIn(Long centerId, Long clientId, Collection<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"treatment"})
    List<Appointment> findByStatusAndDateTimeBefore(AppointmentStatus status, LocalDateTime dateTime);
}
