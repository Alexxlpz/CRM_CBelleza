package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Inventory;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @EntityGraph(attributePaths = {"product"})
    List<Inventory> findByCenterId(Long centerId);

    Optional<Inventory> findByCenterIdAndProductId(Long centerId, Long productId);

    Optional<Inventory> findByIdAndCenterId(Long id, Long centerId);

    /** Bloquea la fila mientras se vende: dos ventas a la vez no pueden dejar el stock en negativo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i join fetch i.product where i.id = :id and i.center.id = :centerId")
    Optional<Inventory> findForUpdate(@Param("id") Long id, @Param("centerId") Long centerId);
}
