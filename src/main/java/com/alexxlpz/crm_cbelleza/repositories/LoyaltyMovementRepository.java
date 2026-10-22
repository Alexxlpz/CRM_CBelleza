package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.LoyaltyMovement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoyaltyMovementRepository extends JpaRepository<LoyaltyMovement, Long> {

    List<LoyaltyMovement> findByCenterIdAndClientIdOrderByCreatedAtDescIdDesc(Long centerId, Long clientId);

    @Query("select m.appointmentId from LoyaltyMovement m where m.center.id = :centerId and m.client.id = :clientId and m.appointmentId is not null")
    List<Long> findRewardedAppointmentIds(@Param("centerId") Long centerId, @Param("clientId") Long clientId);

    /** Centros donde el cliente tiene movimientos (para su vista de tarjetas). */
    @EntityGraph(attributePaths = {"center"})
    List<LoyaltyMovement> findByClientId(Long clientId);

    @Modifying
    void deleteByProductSaleId(Long productSaleId);

    @Modifying
    void deleteByAppointmentId(Long appointmentId);
}
