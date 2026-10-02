package com.alexxlpz.crm_cbelleza.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "center"})
    private Treatment treatment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Center center;

    // For registered clients (nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User client;

    // For unregistered guest clients (nullable)
    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "guest_phone")
    private String guestPhone;

    // Assigned worker (nullable for new appointments)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User worker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "worker_message", length = 500)
    private String workerMessage;

    @Transient
    private Boolean isNewClient;

    public LocalDateTime getEndDateTime() {
        if (this.dateTime == null) {
            return null;
        }
        int durationMinutes = 60;
        try {
            if (this.treatment != null && this.treatment.getDuration() != null && this.treatment.getDuration() > 0) {
                durationMinutes = this.treatment.getDuration();
            }
        } catch (Exception ignored) {
            // Safe fallback if proxy initialization is unavailable
        }
        return this.dateTime.plusMinutes(durationMinutes);
    }

    @PostLoad
    public void updateStatusIfCompleted() {
        if (this.status == AppointmentStatus.CONFIRMED && this.dateTime != null) {
            LocalDateTime end = getEndDateTime();
            if (end != null && !LocalDateTime.now().isBefore(end)) {
                this.status = AppointmentStatus.COMPLETED;
            }
        }
    }

    public AppointmentStatus getStatus() {
        if (this.status == AppointmentStatus.CONFIRMED && this.dateTime != null) {
            LocalDateTime end = getEndDateTime();
            if (end != null && !LocalDateTime.now().isBefore(end)) {
                return AppointmentStatus.COMPLETED;
            }
        }
        return this.status;
    }

    public void setStatus(AppointmentStatus status) {
        if (status == AppointmentStatus.CONFIRMED && this.dateTime != null) {
            LocalDateTime end = getEndDateTime();
            if (end != null && !LocalDateTime.now().isBefore(end)) {
                this.status = AppointmentStatus.COMPLETED;
                return;
            }
        }
        this.status = status;
    }
}
