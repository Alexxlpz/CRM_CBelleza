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

    @EntityGraph(attributePaths = {"treatment", "client", "worker", "summaryUpdatedBy"})
    List<Appointment> findByCenterIdAndClientIdOrderByDateTimeDesc(Long centerId, Long clientId);

    @EntityGraph(attributePaths = {"treatment"})
    List<Appointment> findByCenterIdAndStatusIn(Long centerId, Collection<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"treatment"})
    List<Appointment> findByCenterIdAndStatusInAndDateTimeAfterOrderByDateTimeAsc(
            Long centerId, Collection<AppointmentStatus> statuses, LocalDateTime dateTime);

    @EntityGraph(attributePaths = {"treatment", "client", "worker", "center"})
    Optional<Appointment> findByIdAndCenterId(Long id, Long centerId);

    @EntityGraph(attributePaths = {"treatment", "center", "worker"})
    Optional<Appointment> findByIdAndClientId(Long id, Long clientId);

    /** Citas confirmadas del centro en un rango (las que ocupan cabina y profesional). */
    @EntityGraph(attributePaths = {"treatment", "worker"})
    List<Appointment> findByCenterIdAndStatusAndDateTimeBetween(Long centerId, AppointmentStatus status,
                                                                 LocalDateTime from, LocalDateTime to);

    /** Propuestas abiertas del centro, con sus horas (reservan hueco hasta que el cliente responde). */
    @EntityGraph(attributePaths = {"treatment", "worker", "proposals"})
    List<Appointment> findByCenterIdAndStatus(Long centerId, AppointmentStatus status);

    @EntityGraph(attributePaths = {"treatment", "client", "worker", "center"})
    List<Appointment> findByStatusAndProposalExpiresAtBefore(AppointmentStatus status, LocalDateTime moment);

    /** Citas confirmadas sin recordatorio enviado en un rango de fechas (recordatorio de 24 h). */
    @EntityGraph(attributePaths = {"treatment", "client", "worker", "center"})
    List<Appointment> findByStatusAndReminderSentAtIsNullAndDateTimeBetween(AppointmentStatus status,
                                                                             LocalDateTime from, LocalDateTime to);

    /** Carga todo lo que se escribe en el evento de Google Calendar. */
    @EntityGraph(attributePaths = {"treatment", "client", "worker", "center"})
    Optional<Appointment> findWithDetailsById(Long id);

    /** Citas confirmadas de un trabajador desde una fecha (las que se copian al activar Google Calendar). */
    @EntityGraph(attributePaths = {"treatment", "client", "worker", "center"})
    List<Appointment> findByWorkerIdAndStatusAndDateTimeAfter(Long workerId, AppointmentStatus status, LocalDateTime dateTime);

    List<Appointment> findByWorkerIdAndGoogleEventIdIsNotNull(Long workerId);

    List<Appointment> findByGoogleEventOwnerId(Long workerId);

    /** Resúmenes marcados como aviso (se muestran en las demás citas del cliente). */
    @EntityGraph(attributePaths = {"treatment"})
    List<Appointment> findByCenterIdAndTreatmentAlertTrueOrderByDateTimeDesc(Long centerId);

    long countByClientId(Long clientId);

    boolean existsByCenterIdAndClientIdAndStatusIn(Long centerId, Long clientId, Collection<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"treatment"})
    List<Appointment> findByStatusAndDateTimeBefore(AppointmentStatus status, LocalDateTime dateTime);
}
