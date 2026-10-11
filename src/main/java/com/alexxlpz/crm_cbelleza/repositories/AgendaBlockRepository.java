package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
