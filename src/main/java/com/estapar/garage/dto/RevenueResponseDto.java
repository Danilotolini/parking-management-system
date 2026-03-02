package com.estapar.garage.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueResponseDto {
    private BigDecimal amount;
    private String currency = "BRL";
    private Instant timestamp;
}