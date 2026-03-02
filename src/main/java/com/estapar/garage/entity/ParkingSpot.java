package com.estapar.garage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "parking_spots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ParkingSpot {

    @Id
    private Long id; // vem do simulador

    @Column(nullable = false)
    private String sector;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal basePrice;

    @Column(nullable = false)
    private boolean occupied;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal lat;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal lng;
}