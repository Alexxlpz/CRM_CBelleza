package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.dto.AppointmentCalendarDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Convierte citas en el DTO que consume el calendario del trabajador (JSON embebido en la página). */
@Component
public class AppointmentCalendarMapper {

    /**
     * @param now         «ahora» en la hora del centro (decide si ya se puede escribir el resumen)
     * @param clientAlerts avisos del cliente en el centro (se excluye el de esta misma cita)
     * @param clientNoShows citas del centro a las que el cliente no se presentó
     */
    public AppointmentCalendarDTO toDto(Appointment app, LocalDateTime now, List<Appointment> clientAlerts, int clientNoShows) {
        return AppointmentCalendarDTO.builder()
                .id(app.getId())
                .dateTime(app.getDateTime() != null ? app.getDateTime().toString() : null)
                .status(app.getStatus() != null ? app.getStatus().name() : null)
                .workerMessage(app.getWorkerMessage())
                .isNewClient(Boolean.TRUE.equals(app.getIsNewClient()))
                .guestName(app.getGuestName())
                .guestPhone(app.getGuestPhone())
                .client(toClient(app.getClient()))
                .worker(toWorker(app.getWorker()))
                .treatment(toTreatment(app.getTreatment()))
                .price(app.getPrice())
                .previousDateTime(app.getPreviousDateTime() != null ? app.getPreviousDateTime().toString() : null)
                // Las horas propuestas solo se cargan en las citas PROPOSED (pocas): así no hay una consulta por cita
                .proposals(app.getStatus() == AppointmentStatus.PROPOSED
                        ? app.getProposals().stream()
                            .map(p -> new AppointmentCalendarDTO.ProposalDTO(p.getId(), p.getDateTime().toString()))
                            .toList()
                        : List.of())
                .proposalExpiresAt(app.getProposalExpiresAt() != null ? app.getProposalExpiresAt().toString() : null)
                .treatmentSummary(app.getTreatmentSummary())
                .treatmentAlert(app.isTreatmentAlert())
                .summaryEditable(app.isSummaryEditableAt(now))
                .clientAlerts(clientAlerts.stream()
                        .filter(alert -> !alert.getId().equals(app.getId()))
                        .map(alert -> new AppointmentCalendarDTO.ClientAlertDTO(
                                alert.getDateTime().toString(), alert.getTreatment().getName(), alert.getTreatmentSummary()))
                        .toList())
                .clientNoShows(clientNoShows)
                .workerChosenByClient(app.getStatus() == AppointmentStatus.PENDING && app.getWorker() != null
                        && (app.getLastChange() == AppointmentChange.REQUESTED || app.getLastChange() == AppointmentChange.RESCHEDULED_BY_CLIENT))
                .build();
    }

    private AppointmentCalendarDTO.ClientDTO toClient(User client) {
        if (client == null) return null;
        return AppointmentCalendarDTO.ClientDTO.builder()
                .id(client.getId())
                .name(client.getName())
                .phone(client.getPhone())
                .build();
    }

    private AppointmentCalendarDTO.WorkerDTO toWorker(User worker) {
        if (worker == null) return null;
        return AppointmentCalendarDTO.WorkerDTO.builder()
                .id(worker.getId())
                .name(worker.getName())
                .build();
    }

    private AppointmentCalendarDTO.TreatmentDTO toTreatment(Treatment treatment) {
        if (treatment == null) return null;
        return AppointmentCalendarDTO.TreatmentDTO.builder()
                .id(treatment.getId())
                .name(treatment.getName())
                .price(treatment.getPrice())
                .duration(treatment.getDuration())
                .build();
    }
}
