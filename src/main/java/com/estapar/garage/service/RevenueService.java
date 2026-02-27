package com.estapar.garage.service;

import com.estapar.garage.dto.RevenueResponseDto;
import com.estapar.garage.repository.ParkingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;

@Service
@RequiredArgsConstructor
public class RevenueService {

    private final ParkingSessionRepository sessionRepository;

    public RevenueResponseDto revenue(String sector, LocalDate date) {
        // janela [00:00, 00:00 do dia seguinte) em UTC
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal total = sessionRepository.sumRevenueBySectorBetween(sector, start, end);

        return RevenueResponseDto.builder()
                .sector(sector)
                .date(date.toString())
                .total(total)
                .build();
    }
}