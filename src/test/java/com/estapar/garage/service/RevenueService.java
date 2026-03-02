package com.estapar.garage.service;

import com.estapar.garage.dto.RevenueResponseDto;
import com.estapar.garage.repository.ParkingSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueServiceTest {

    @Mock ParkingSessionRepository sessionRepository;
    @InjectMocks RevenueService service;

    @Test
    @DisplayName("Retorna receita correta para setor e data")
    void revenue_returns_correct_amount() {
        when(sessionRepository.sumRevenueBySectorBetween(
                eq("A"), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("150.00"));

        RevenueResponseDto result = service.revenue("A", LocalDate.of(2026, 1, 1));

        assertThat(result.getAmount()).isEqualByComparingTo("150.00");
        assertThat(result.getCurrency()).isEqualTo("BRL");
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Retorna zero quando não há receita no período")
    void revenue_returns_zero_when_no_sessions() {
        when(sessionRepository.sumRevenueBySectorBetween(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        RevenueResponseDto result = service.revenue("B", LocalDate.of(2026, 1, 1));

        assertThat(result.getAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Retorna zero quando repositório retorna null (coalesce falhou)")
    void revenue_handles_null_from_repo() {
        when(sessionRepository.sumRevenueBySectorBetween(any(), any(), any()))
                .thenReturn(null);

        RevenueResponseDto result = service.revenue("C", LocalDate.of(2026, 1, 1));

        assertThat(result.getAmount()).isEqualByComparingTo("0.00");
    }
}