package com.estapar.garage.controller;

import com.estapar.garage.dto.WebhookEventRequest;
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
    public ResponseEntity<Void> webhook(@Valid @RequestBody WebhookEventRequest request) {
        log.info("WEBHOOK received: eventType={}, plate={}, entryTime={}, exitTime={}",
                request.getEventType(), request.getLicensePlate(),
                request.getEntryTime(), request.getExitTime());

        webhookService.handle(request); // se estourar exceção, o Spring devolve 4xx/5xx
        return ResponseEntity.ok().build();
    }
}