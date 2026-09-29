package com.alexxlpz.crm_cbelleza.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;

    // For registered clients (nullable for guest clients)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;

    // For guest clients (nullable for registered clients)
    @Column(name = "guest_phone")
    private String guestPhone;

    @Column(name = "guest_name")
    private String guestName;

    // Dynamic JSON payload with key-value pairs of center-defined variables
    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;
}
