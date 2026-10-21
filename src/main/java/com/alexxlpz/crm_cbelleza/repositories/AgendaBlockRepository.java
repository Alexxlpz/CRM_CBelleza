package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgendaBlockRepository extends JpaRepository<AgendaBlock, Long> {

    /** Bloqueos que terminan después de {@code from} y empiezan antes de {@code to}. */
    @EntityGraph(attributePaths = {"worker"})
    List<AgendaBlock> findByCenterIdAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(Long centerId, LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = {"worker"})
    List<AgendaBlock> findByCenterIdAndEndsAtAfterOrderByStartsAtAsc(Long centerId, LocalDateTime from);

    Optional<AgendaBlock> findByIdAndCenterId(Long id, Long centerId);

    /** Cierres de todo el centro, de todos los centros, que pisan el periodo (estado «abierto ahora» del buscador). */
    @Query("select b from AgendaBlock b join fetch b.center where b.worker is null and b.endsAt > :from and b.startsAt < :to")
    List<AgendaBlock> findCenterClosuresBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Bloqueos de un trabajador que aún no han terminado (se quitan al sacarle del equipo). */
    @Modifying
    @Query("delete from AgendaBlock b where b.worker.id = :workerId and b.endsAt > :from")
    int deleteUpcomingOfWorker(@Param("workerId") Long workerId, @Param("from") LocalDateTime from);
}
