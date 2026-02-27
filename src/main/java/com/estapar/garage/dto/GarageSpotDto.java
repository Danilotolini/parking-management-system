package com.estapar.garage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class GarageSpotDto {

    private Long id;
    private String sector;

    private Double lat;
    private Double lng;

    @JsonProperty("occupied")
    private Boolean occupied;
}