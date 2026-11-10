package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.AppointmentMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentMediaRepository extends JpaRepository<AppointmentMedia, Long> {

    Optional<AppointmentMedia> findByIdAndCenterId(Long id, Long centerId);

    List<AppointmentMedia> findByCenterIdAndAppointmentIdOrderByCreatedAtAscIdAsc(Long centerId, Long appointmentId);

    /** Los de varias citas a la vez (agenda, ficha del cliente): una sola consulta. */
    List<AppointmentMedia> findByCenterIdAndAppointmentIdInOrderByCreatedAtAscIdAsc(Long centerId, Collection<Long> appointmentIds);

    /** Todos los del centro (agenda completa). */
    List<AppointmentMedia> findByCenterIdOrderByCreatedAtAscIdAsc(Long centerId);

    long countByAppointmentId(Long appointmentId);

    /** Los de las citas de un cliente, en cualquier centro (borrar su cuenta). */
    List<AppointmentMedia> findByAppointmentIdIn(Collection<Long> appointmentIds);
}
