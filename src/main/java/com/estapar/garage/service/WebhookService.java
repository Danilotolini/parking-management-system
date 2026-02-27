package com.estapar.garage.service;

import com.estapar.garage.dto.WebhookEventRequest;
import com.estapar.garage.dto.WebhookEventResponse;
import com.estapar.garage.entity.ParkingSession;
import com.estapar.garage.entity.ParkingSessionEvent;
import com.estapar.garage.entity.ParkingSpot;
import com.estapar.garage.repository.ParkingSessionEventRepository;
import com.estapar.garage.repository.ParkingSessionRepository;
import com.estapar.garage.repository.ParkingSpotRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final ParkingSpotRepository spotRepository;
    private final ParkingSessionRepository sessionRepository;
    private final ParkingSessionEventRepository eventRepository;

    @Transactional
    public WebhookEventResponse handle(WebhookEventRequest req) {
        String eventType = normalizeEventType(req.getEventType());
        String plate = normalizePlate(req.getLicensePlate());

        Instant eventTime = extractEventTime(eventType, req);

        // idempotência mínima (depende de unique constraint na tabela, ex: (plate,event_type,event_time))
        try {
            eventRepository.save(ParkingSessionEvent.builder()
                    .plate(plate)
                    .eventType(eventType)
                    .eventTime(eventTime)
                    .build());
        } catch (DataIntegrityViolationException dup) {
            return WebhookEventResponse.builder()
                    .message("Duplicate event ignored")
                    .status("OK")
                    .build();
        }

        return switch (eventType) {
            case "ENTRY" -> onEntry(plate, eventTime);
            case "PARKED" -> onParked(plate, req);
            case "EXIT" -> onExit(plate, eventTime);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event_type");
        };
    }

    private WebhookEventResponse onEntry(String plate, Instant entryTime) {
        sessionRepository.findActiveByPlate(plate).ifPresent(s -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plate already has an active session");
        });

        ParkingSession session = ParkingSession.builder()
                .plate(plate)
                .entryTime(entryTime)
                .status(ParkingSession.Status.ENTRY)
                .build();

        session = sessionRepository.save(session);

        return WebhookEventResponse.builder()
                .message("ENTRY registered")
                .sessionId(session.getId())
                .status(session.getStatus().name())
                .build();
    }

    private WebhookEventResponse onParked(String plate, WebhookEventRequest req) {
        ParkingSession session = sessionRepository.findActiveByPlate(plate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active session not found"));

        if (req.getLat() == null || req.getLng() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat/lng are required for PARKED");
        }

        ParkingSpot spot = spotRepository.findByLatAndLng(req.getLat(), req.getLng())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spot not found for lat/lng"));

        if (spot.isOccupied()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spot already occupied");
        }

        // regra de lotação por setor: se 100%, não entra mais (na prática, spot livre já garante, mas mantém coerência)
        BigDecimal multiplier = multiplierForSectorOccupancy(spot.getSector()); // usa occupied atual (antes de ocupar)

        spot.setOccupied(true);
        spotRepository.save(spot);

        session.setSpot(spot);
        session.setPriceMultiplierApplied(multiplier);
        session.setStatus(ParkingSession.Status.PARKED);
        sessionRepository.save(session);

        return WebhookEventResponse.builder()
                .message("PARKED registered")
                .sessionId(session.getId())
                .status(session.getStatus().name())
                .build();
    }

    private WebhookEventResponse onExit(String plate, Instant exitTime) {
        ParkingSession session = sessionRepository.findActiveByPlate(plate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active session not found"));

        if (session.getSpot() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session has no spot (PARKED not received?)");
        }

        if (session.getExitTime() != null || session.getStatus() == ParkingSession.Status.EXIT) {
            return WebhookEventResponse.builder()
                    .message("Already EXIT")
                    .sessionId(session.getId())
                    .status("EXIT")
                    .build();
        }

        session.setExitTime(exitTime);
        session.setStatus(ParkingSession.Status.EXIT);

        BigDecimal totalPaid = calculateTotalPaid(
                session.getEntryTime(),
                exitTime,
                session.getSpot().getBasePrice(),
                session.getPriceMultiplierApplied() == null ? BigDecimal.ONE : session.getPriceMultiplierApplied()
        );
        session.setTotalPaid(totalPaid);

        ParkingSpot spot = session.getSpot();
        spot.setOccupied(false);
        spotRepository.save(spot);

        sessionRepository.save(session);

        return WebhookEventResponse.builder()
                .message("EXIT registered")
                .sessionId(session.getId())
                .status("EXIT")
                .build();
    }

    private BigDecimal calculateTotalPaid(Instant entry, Instant exit, BigDecimal basePrice, BigDecimal multiplier) {
        Duration d = Duration.between(entry, exit);
        if (d.isNegative()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exit_time before entry_time");
        }

        long minutes = d.toMinutes();
        if (minutes <= 30) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        long billableMinutes = minutes - 30;
        long hours = (billableMinutes + 59) / 60; // ceil

        BigDecimal total = basePrice
                .multiply(BigDecimal.valueOf(hours))
                .multiply(multiplier);

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal multiplierForSectorOccupancy(String sector) {
        long total = spotRepository.countBySector(sector);
        if (total == 0) return BigDecimal.ONE;

        long occupied = spotRepository.countBySectorAndOccupiedTrue(sector);

        BigDecimal rate = BigDecimal.valueOf(occupied)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);

        if (rate.compareTo(new BigDecimal("0.25")) < 0) return new BigDecimal("0.90");
        if (rate.compareTo(new BigDecimal("0.50")) < 0) return new BigDecimal("1.00");
        if (rate.compareTo(new BigDecimal("0.75")) < 0) return new BigDecimal("1.10");
        return new BigDecimal("1.25");
    }

    private Instant extractEventTime(String eventType, WebhookEventRequest req) {
        return switch (eventType) {
            case "ENTRY" -> parseToInstant(req.getEntryTime(), "entry_time");
            case "EXIT" -> parseToInstant(req.getExitTime(), "exit_time");
            case "PARKED" -> {
                // se vier algum tempo, usa; se não, usa o entryTime da sessão ativa (fixo => ajuda idempotência)
                if (req.getEntryTime() != null && !req.getEntryTime().isBlank()) {
                    yield parseToInstant(req.getEntryTime(), "entry_time");
                }
                ParkingSession s = sessionRepository.findActiveByPlate(normalizePlate(req.getLicensePlate()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active session not found"));
                yield s.getEntryTime();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event_type");
        };
    }

    private Instant parseToInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }

        // 1) "2026-02-26T02:55:00Z"
        try { return Instant.parse(value); } catch (DateTimeParseException ignored) {}

        // 2) "2026-02-26T02:55:00-03:00"
        try { return OffsetDateTime.parse(value).toInstant(); } catch (DateTimeParseException ignored) {}

        // 3) "2026-02-26T02:54:46" (sem tz) -> assume UTC
        try { return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC); } catch (DateTimeParseException ignored) {}

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO-8601 time: " + value);
    }

    private String normalizeEventType(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase();
    }

    private String normalizePlate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "license_plate is required");
        }
        return raw.trim().toUpperCase();
    }
}