package com.estapar.garage.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueResponseDto {
    private String sector;
    private String date; // yyyy-MM-dd
    private BigDecimal total;
}