package com.alexxlpz.productapi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductContributionRepository extends JpaRepository<ProductContribution, Long> {

    List<ProductContribution> findByGtinOrderById(String gtin);
}
