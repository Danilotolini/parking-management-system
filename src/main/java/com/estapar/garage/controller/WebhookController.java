package com.estapar.garage.controller;

import com.estapar.garage.dto.WebhookEventRequest;
import com.estapar.garage.dto.WebhookEventResponse;
import com.estapar.garage.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<WebhookEventResponse> webhook(@RequestBody WebhookEventRequest request) {
        WebhookEventResponse response = webhookService.handle(request);
        return ResponseEntity.ok(response);
    }
}