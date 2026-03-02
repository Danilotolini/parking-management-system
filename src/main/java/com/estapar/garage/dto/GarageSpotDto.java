package com.estapar.garage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class GarageSpotDto {
    private Long id;
    private String sector;
    private BigDecimal lat;
    private BigDecimal lng;
    private Boolean occupied;
}