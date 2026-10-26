package com.alexxlpz.crm_cbelleza.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentCalendarDTO {
    private Long id;
    private String dateTime;
    private String status;
    private String workerMessage;
    private Boolean isNewClient;
    private String guestName;
    private String guestPhone;
    private ClientDTO client;
    private WorkerDTO worker;
    private TreatmentDTO treatment;
    /** Lo que se cobra por la cita (puede no ser el precio de la carta). */
    private Double price;
    /** Hora anterior si el centro la movió. */
    private String previousDateTime;
    /** Horas propuestas al cliente (solo en citas PROPOSED). */
    private List<ProposalDTO> proposals;
    private String proposalExpiresAt;
    /** Resumen del tratamiento realizado y si es un aviso. */
    private String treatmentSummary;
    private boolean treatmentAlert;
    /** La cita ya ha empezado: se puede escribir su resumen. */
    private boolean summaryEditable;
    /** Avisos que el cliente tiene en otras citas del centro (reacciones, productos que no le sientan bien...). */
    private List<ClientAlertDTO> clientAlerts;
    /** Citas del centro a las que el cliente no se presentó. */
    private int clientNoShows;
    /** Solicitud en la que el cliente ha elegido profesional (sale en «La atiende»). */
    private boolean workerChosenByClient;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientAlertDTO {
        private String dateTime;
        private String treatmentName;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProposalDTO {
        private Long id;
        private String dateTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientDTO {
        private Long id;
        private String name;
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkerDTO {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TreatmentDTO {
        private Long id;
        private String name;
        private Double price;
        private Integer duration;
    }
}
