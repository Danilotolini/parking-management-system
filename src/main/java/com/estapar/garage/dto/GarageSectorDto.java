package com.estapar.garage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GarageSectorDto {

    private String sector;

    @JsonProperty("base_price")
    private BigDecimal basePrice;

    @JsonProperty("max_capacity")
    private Integer maxCapacity;

    @JsonProperty("open_hour")
    private String openHour; // "00:00"

    @JsonProperty("close_hour")
    private String closeHour; // "23:59"

    @JsonProperty("duration_limit_minutes")
    private Integer durationLimitMinutes;
}