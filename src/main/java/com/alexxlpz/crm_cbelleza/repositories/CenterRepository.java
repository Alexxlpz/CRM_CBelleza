package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Center;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CenterRepository extends JpaRepository<Center, Long> {
}
