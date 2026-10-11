package com.alexxlpz.crm_cbelleza.dto;

import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;

/** Bloqueo de agenda tal y como lo pinta la agenda del trabajador. */
public record AgendaBlockDTO(Long id, Long workerId, String workerName, String startsAt, String endsAt, String reason) {

    public static AgendaBlockDTO of(AgendaBlock block) {
        return new AgendaBlockDTO(
                block.getId(),
                block.getWorker() != null ? block.getWorker().getId() : null,
                block.getWorker() != null ? block.getWorker().getName() : null,
                block.getStartsAt().toString(),
                block.getEndsAt().toString(),
                block.getReason());
    }
}
