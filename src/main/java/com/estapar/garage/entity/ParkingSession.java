package com.estapar.garage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "parking_sessions")
public class ParkingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String plate;

    @Column(nullable = false)
    private Instant entryTime;

    private Instant exitTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalPaid;

    // vaga usada na sessão
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = true)
    private ParkingSpot spot;

    // multiplica o basePrice no EXIT
    @Column(nullable = true, precision = 10, scale = 4)
    private BigDecimal priceMultiplierApplied;

    public enum Status { ENTRY, PARKED, EXIT }
}