package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.ClientCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientCardRepository extends JpaRepository<ClientCard, Long> {
    Optional<ClientCard> findByCenterIdAndClientId(Long centerId, Long clientId);
    Optional<ClientCard> findByCenterIdAndGuestPhone(Long centerId, String guestPhone);
    List<ClientCard> findByCenterId(Long centerId);
}
