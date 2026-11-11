package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Productos con ese código de barras que puede usar el centro: los suyos y los del catálogo general. */
    @Query("select p from Product p where p.barcode = :barcode and (p.centerId = :centerId or p.centerId is null) order by p.centerId desc nulls last, p.id")
    List<Product> findUsableByBarcode(@Param("barcode") String barcode, @Param("centerId") Long centerId);
}
