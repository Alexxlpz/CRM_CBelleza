package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    List<Treatment> findByCenterId(Long centerId);

    long countByCenterId(Long centerId);

    /** Tratamientos con oferta y en la carta (también vencidas: quien llama decide con el «hoy» de cada centro). */
    @Query("SELECT t FROM Treatment t WHERE t.offerPrice IS NOT NULL AND t.hidden = false")
    List<Treatment> findByOfferPriceIsNotNull();

    Optional<Treatment> findByIdAndCenterId(Long id, Long centerId);

    @Query("SELECT DISTINCT t.center.id FROM Treatment t WHERE t.type = :type AND t.hidden = false")
    Set<Long> findCenterIdsOfferingType(@Param("type") TreatmentType type);
}
