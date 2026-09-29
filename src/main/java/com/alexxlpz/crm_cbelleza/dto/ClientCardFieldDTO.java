package com.alexxlpz.crm_cbelleza.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientCardFieldDTO {
    private String id;
    private String label;
    private String type; // "text", "number", "textarea", "select"
    private String options; // comma-separated options for select
    private String placeholder;
    private boolean required;
}
