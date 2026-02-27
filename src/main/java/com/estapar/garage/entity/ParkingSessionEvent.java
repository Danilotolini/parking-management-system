package com.estapar.garage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "parking_session_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_idempotency",
                columnNames = {"plate", "event_type", "event_time"})
)
public class ParkingSessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String plate;

    @Column(name = "event_type", nullable = false)
    private String eventType; // ENTRY, PARKED, EXIT

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;
}