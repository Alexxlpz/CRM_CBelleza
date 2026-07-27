package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Treatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TreatmentRepository extends JpaRepository<Treatment, Long> {
    List<Treatment> findByCenterId(Long centerId);
}
