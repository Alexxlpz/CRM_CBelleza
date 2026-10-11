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

    /** Evento en el Google Calendar del trabajador asignado; nulo si no tiene la sincronización activada. */
    @Column(name = "google_event_id")
    private String googleEventId;

    /** Trabajador en cuyo Google Calendar está el evento (puede no ser el asignado si se cambió de profesional). */
    @Column(name = "google_event_owner_id")
    private Long googleEventOwnerId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Último cambio y cuándo: alimenta las campanas de avisos y los correos. */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_change", length = 30)
    private AppointmentChange lastChange;

    @Column(name = "last_change_at")
    private LocalDateTime lastChangeAt;

    /** Hora anterior cuando el centro mueve una cita (se muestra al cliente: «antes: …»). */
    @Column(name = "previous_date_time")
    private LocalDateTime previousDateTime;

    /** Una propuesta (PROPOSED) sin respuesta se anula sola a esta hora. */
    @Column(name = "proposal_expires_at")
    private LocalDateTime proposalExpiresAt;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dateTime ASC")
    @JsonIgnoreProperties({"appointment"})
    @Builder.Default
    private java.util.List<AppointmentProposal> proposals = new java.util.ArrayList<>();

    @Transient
    private Boolean isNewClient;

    public void recordChange(AppointmentChange change, LocalDateTime at) {
        this.lastChange = change;
        this.lastChangeAt = at;
    }

    /** Aún no ha empezado (hora del servidor): el cliente todavía puede anularla. */
    public boolean isUpcoming() {
        return dateTime != null && dateTime.isAfter(LocalDateTime.now());
    }

    /** true si [start, end) se solapa con esta cita. */
    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        return start.isBefore(getEndDateTime()) && end.isAfter(dateTime);
    }

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
