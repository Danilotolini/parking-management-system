package com.estapar.garage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class GarageResponseDto {

    @JsonProperty("garage")
    private List<GarageSectorDto> garage;

    @JsonProperty("spots")
    private List<GarageSpotDto> spots;
}