package com.alexxlpz.crm_cbelleza.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientSummaryDTO {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private int totalAppointments;
    private LocalDateTime lastAppointmentDate;
    private boolean hasFilledCard;
    private LocalDateTime cardUpdatedAt;
    private String cardUpdatedByName;
}
