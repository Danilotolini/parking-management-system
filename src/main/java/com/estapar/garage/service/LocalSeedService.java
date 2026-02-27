package com.estapar.garage.service;

import com.estapar.garage.entity.ParkingSpot;
import com.estapar.garage.repository.ParkingSpotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocalSeedService implements ApplicationRunner {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(WebhookService.class);

    private final ParkingSpotRepository spotRepository;

    @Value("${garage.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) return;

        long count = spotRepository.count();
        if (count > 0) {
            log.info("Seed skipped: parking_spots already has {} rows.", count);
            return;
        }

        List<ParkingSpot> spots = new ArrayList<>();

        // Setor A: ids 1..10, base 10.00
        for (long id = 1; id <= 10; id++) {
            spots.add(ParkingSpot.builder()
                    .id(id)
                    .sector("A")
                    .basePrice(new BigDecimal("10.00"))
                    .occupied(false)
                    .build());
        }

        // Setor B: ids 11..20, base 12.00
        for (long id = 11; id <= 20; id++) {
            spots.add(ParkingSpot.builder()
                    .id(id)
                    .sector("B")
                    .basePrice(new BigDecimal("12.00"))
                    .occupied(false)
                    .build());
        }

        spotRepository.saveAll(spots);
        log.info("Seed OK: inserted {} parking spots (A=10, B=10).", spots.size());
    }
}