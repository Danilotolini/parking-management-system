package com.estapar.garage.service;

import com.estapar.garage.dto.GarageResponseDto;
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

    @Value("${garage.simulator.base-url:http://garage-sim:3000}")
    private String simulatorBaseUrl;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            log.info("Garage bootstrap disabled (garage.bootstrap.enabled=false)");
            return;
        }

        try {
            GarageResponseDto resp = garageSimulatorClient.get()
                    .uri("/garage")
                    .retrieve()
                    .body(GarageResponseDto.class);

            if (resp == null || resp.getSpots() == null || resp.getSpots().isEmpty()) {
                log.warn("Simulator returned empty /garage spots. Skipping bootstrap.");
                return;
            }

            if (spotRepository.count() > 0) {
                log.info("Bootstrap skipped: parking_spots already populated.");
                return;
            }

            // sector -> basePrice (vem do resp.garage)
            var baseBySector = new HashMap<String, BigDecimal>();
            if (resp.getGarage() != null) {
                for (var s : resp.getGarage()) {
                    baseBySector.put(s.getSector(), s.getBasePrice());
                }
            }

            List<ParkingSpot> toInsert = new ArrayList<>();

            for (var spot : resp.getSpots()) {
                var base = baseBySector.get(spot.getSector());
                if (base == null) {
                    log.warn("Spot {} sector {} has no base_price in garage list. Skipping.",
                            spot.getId(), spot.getSector());
                    continue;
                }

                toInsert.add(ParkingSpot.builder()
                        .id(spot.getId())
                        .sector(spot.getSector())
                        .basePrice(base)
                        .occupied(false) // não confiar no simulador
                        .lat(spot.getLat())
                        .lng(spot.getLng())
                        .build());
            }

            spotRepository.saveAll(toInsert);
            log.info("Bootstrap OK: inserted {} spots from simulator.", toInsert.size());

        } catch (ResourceAccessException e) {
            log.warn("Garage simulator not reachable at {}. Skipping bootstrap.", simulatorBaseUrl);
        } catch (RestClientResponseException e) {
            log.warn("Garage simulator returned HTTP {}. Skipping bootstrap.", e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Unexpected error during garage bootstrap. Skipping. Cause: {}", e.toString());
        }
    }
}