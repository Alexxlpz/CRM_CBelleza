package com.alexxlpz.crm_cbelleza.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
