package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
