package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.dto.AppointmentCalendarDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import org.springframework.stereotype.Component;

/** Convierte citas en el DTO que consume el calendario del trabajador (JSON embebido en la página). */
@Component
public class AppointmentCalendarMapper {

    public AppointmentCalendarDTO toDto(Appointment app) {
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
