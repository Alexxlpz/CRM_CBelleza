package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "centers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Center {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;
    private String phone;
    private String email;

    private Double latitude;
    private Double longitude;
}
