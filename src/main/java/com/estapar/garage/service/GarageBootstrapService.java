package com.estapar.garage.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.estapar.garage.entity.ParkingSpot;
import com.estapar.garage.repository.ParkingSpotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GarageBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GarageBootstrapService.class);

    private final RestClient garageSimulatorClient;
    private final ParkingSpotRepository spotRepository;

    @Value("${garage.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${garage.simulator.base-url:http://localhost:3000}")
    private String simulatorBaseUrl;

    // ── DTOs internos (só usados aqui) ──────────────────────────────────────

    private record GarageResponse(
            List<SectorDto> garage,
            List<SpotDto> spots
    ) {}

    private record SectorDto(
            String sector,
            @JsonProperty("base_price") BigDecimal basePrice
    ) {}

    private record SpotDto(
            Long id,
            String sector,
            BigDecimal lat,
            BigDecimal lng
    ) {}

    // ── Bootstrap ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            log.info("Garage bootstrap disabled.");
            return;
        }

        try {
            GarageResponse resp = garageSimulatorClient.get()
                    .uri("/garage")
                    .retrieve()
                    .body(GarageResponse.class);

            if (resp == null || resp.spots() == null || resp.spots().isEmpty()) {
                log.warn("Simulator returned empty /garage. Skipping bootstrap.");
                return;
            }

            if (spotRepository.count() > 0) {
                log.info("Bootstrap skipped: parking_spots already populated.");
                return;
            }

            var baseBySector = new HashMap<String, BigDecimal>();
            if (resp.garage() != null) {
                resp.garage().forEach(s -> baseBySector.put(s.sector(), s.basePrice()));
            }

            List<ParkingSpot> spots = new ArrayList<>();
            for (SpotDto dto : resp.spots()) {
                BigDecimal base = baseBySector.get(dto.sector());
                if (base == null) {
                    log.warn("Spot {} sector {} missing base_price. Skipping.", dto.id(), dto.sector());
                    continue;
                }
                spots.add(ParkingSpot.builder()
                        .id(dto.id())
                        .sector(dto.sector())
                        .basePrice(base)
                        .lat(dto.lat())
                        .lng(dto.lng())
                        .occupied(false)
                        .build());
            }

            spotRepository.saveAll(spots);
            log.info("Bootstrap OK: inserted {} spots from simulator.", spots.size());

        } catch (ResourceAccessException e) {
            log.warn("Simulator not reachable at {}. Skipping bootstrap.", simulatorBaseUrl);
        } catch (RestClientResponseException e) {
            log.warn("Simulator returned HTTP {}. Skipping bootstrap.", e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Bootstrap error: {}", e.toString());
        }
    }
}