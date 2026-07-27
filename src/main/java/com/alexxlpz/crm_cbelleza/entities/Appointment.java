package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_id", nullable = false)
    private Treatment treatment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;

    // For registered clients (nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;

    // For unregistered guest clients (nullable)
    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "guest_phone")
    private String guestPhone;

    // Assigned worker (nullable for new appointments)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private User worker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "worker_message", length = 500)
    private String workerMessage;
}
