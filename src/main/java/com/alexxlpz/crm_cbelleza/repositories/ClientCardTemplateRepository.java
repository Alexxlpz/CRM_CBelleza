package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.ClientCardTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ClientCardTemplateRepository extends JpaRepository<ClientCardTemplate, Long> {
    Optional<ClientCardTemplate> findByCenterId(Long centerId);
}
