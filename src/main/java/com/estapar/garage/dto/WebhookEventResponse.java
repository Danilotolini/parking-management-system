package com.estapar.garage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEventResponse {
    private String message;
    private Long sessionId;
    private String status;
}