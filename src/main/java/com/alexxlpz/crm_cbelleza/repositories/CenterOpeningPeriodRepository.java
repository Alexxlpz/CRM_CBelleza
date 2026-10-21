package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.CenterOpeningPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CenterOpeningPeriodRepository extends JpaRepository<CenterOpeningPeriod, Long> {

    List<CenterOpeningPeriod> findByCenterIdOrderByDayOfWeekAscOpensAtAsc(Long centerId);

    /** Horarios de varios centros en una sola consulta (buscador). */
    @Query("SELECT p FROM CenterOpeningPeriod p JOIN FETCH p.center WHERE p.center.id IN :centerIds")
    List<CenterOpeningPeriod> findByCenterIdIn(@Param("centerIds") Collection<Long> centerIds);

    @Modifying
    @Query("DELETE FROM CenterOpeningPeriod p WHERE p.center.id = :centerId")
    void deleteByCenterId(@Param("centerId") Long centerId);
}
