package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSaleRepository extends JpaRepository<ProductSale, Long> {

    @EntityGraph(attributePaths = {"product", "worker"})
    List<ProductSale> findByCenterIdAndClientIdOrderBySoldAtDesc(Long centerId, Long clientId);

    @EntityGraph(attributePaths = {"product"})
    Optional<ProductSale> findByIdAndCenterIdAndClientId(Long id, Long centerId, Long clientId);
}
