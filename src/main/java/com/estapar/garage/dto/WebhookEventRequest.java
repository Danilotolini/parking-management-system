package com.estapar.garage.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class WebhookEventRequest {
    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("license_plate")
    private String licensePlate;

    @JsonProperty("entry_time")
    private String entryTime;

    @JsonProperty("exit_time")
    private String exitTime;

    private BigDecimal lat;
    private BigDecimal lng;
}