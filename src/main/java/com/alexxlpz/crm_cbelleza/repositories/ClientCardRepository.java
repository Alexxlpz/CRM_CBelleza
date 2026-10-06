package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.ClientCard;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientCardRepository extends JpaRepository<ClientCard, Long> {

    @EntityGraph(attributePaths = {"updatedBy"})
    Optional<ClientCard> findByCenterIdAndClientId(Long centerId, Long clientId);

    boolean existsByCenterIdAndClientId(Long centerId, Long clientId);

    @EntityGraph(attributePaths = {"client", "updatedBy"})
    List<ClientCard> findByCenterId(Long centerId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ClientCard c WHERE c.client IS NULL")
    void deleteGuestCards();
}
