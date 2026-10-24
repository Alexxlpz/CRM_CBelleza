package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.WorkerHoursPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkerHoursRepository extends JpaRepository<WorkerHoursPeriod, Long> {

    List<WorkerHoursPeriod> findByWorkerIdOrderByDayOfWeekAscStartsAtAsc(Long workerId);

    /** Tramos de todo el equipo de un centro, en una consulta (agenda y selector de hora). */
    @Query("SELECT h FROM WorkerHoursPeriod h JOIN FETCH h.worker w WHERE w.center.id = :centerId")
    List<WorkerHoursPeriod> findByCenterId(@Param("centerId") Long centerId);

    @Modifying
    @Query("DELETE FROM WorkerHoursPeriod h WHERE h.worker.id = :workerId")
    void deleteByWorkerId(@Param("workerId") Long workerId);
}
