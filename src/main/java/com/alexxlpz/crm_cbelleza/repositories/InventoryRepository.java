package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findByCenterId(Long centerId);
    Optional<Inventory> findByCenterIdAndProductId(Long centerId, Long productId);
}
