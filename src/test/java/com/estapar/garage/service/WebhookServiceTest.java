package com.estapar.garage.service;

import com.estapar.garage.dto.WebhookEventRequest;
import com.estapar.garage.dto.WebhookEventResponse;
import com.estapar.garage.entity.ParkingSession;
import com.estapar.garage.entity.ParkingSessionEvent;
import com.estapar.garage.entity.ParkingSpot;
import com.estapar.garage.repository.ParkingSessionEventRepository;
import com.estapar.garage.repository.ParkingSessionRepository;
import com.estapar.garage.repository.ParkingSpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock ParkingSpotRepository spotRepository;
    @Mock ParkingSessionRepository sessionRepository;
    @Mock ParkingSessionEventRepository eventRepository;

    @InjectMocks WebhookService service;

    // ------------------------------------------------------------------ helpers
    private WebhookEventRequest entryRequest(String plate, String time) {
        var r = new WebhookEventRequest();
        r.setEventType("ENTRY");
        r.setLicensePlate(plate);
        r.setEntryTime(time);
        return r;
    }

    private WebhookEventRequest parkedRequest(String plate, BigDecimal lat, BigDecimal lng) {
        var r = new WebhookEventRequest();
        r.setEventType("PARKED");
        r.setLicensePlate(plate);
        r.setLat(lat);
        r.setLng(lng);
        return r;
    }

    private WebhookEventRequest exitRequest(String plate, String time) {
        var r = new WebhookEventRequest();
        r.setEventType("EXIT");
        r.setLicensePlate(plate);
        r.setExitTime(time);
        return r;
    }

    private ParkingSpot spot(String sector, BigDecimal basePrice, boolean occupied) {
        return ParkingSpot.builder()
                .id(1L)
                .sector(sector)
                .basePrice(basePrice)
                .occupied(occupied)
                .lat(new BigDecimal("-23.561684"))
                .lng(new BigDecimal("-46.655981"))
                .build();
    }

    private ParkingSession activeSession(String plate, Instant entryTime) {
        return ParkingSession.builder()
                .id(10L)
                .plate(plate)
                .entryTime(entryTime)
                .status(ParkingSession.Status.ENTRY)
                .build();
    }

    // ================================================================== ENTRY
    @Nested
    @DisplayName("ENTRY events")
    class EntryEvents {

        @Test
        @DisplayName("Registra entrada com sucesso")
        void entry_success() {
            var req = entryRequest("ZUL0001", "2026-01-01T12:00:00Z");

            when(sessionRepository.findActiveByPlate("ZUL0001")).thenReturn(Optional.empty());
            when(sessionRepository.save(any())).thenAnswer(inv -> {
                ParkingSession s = inv.getArgument(0);
                s = ParkingSession.builder()
                        .id(1L).plate(s.getPlate())
                        .entryTime(s.getEntryTime())
                        .status(s.getStatus())
                        .build();
                return s;
            });

            WebhookEventResponse resp = service.handle(req);

            assertThat(resp.getStatus()).isEqualTo("ENTRY");
            assertThat(resp.getSessionId()).isEqualTo(1L);
            verify(sessionRepository).save(any(ParkingSession.class));
        }

        @Test
        @DisplayName("Retorna CONFLICT quando placa já tem sessão ativa")
        void entry_conflict_active_session() {
            var req = entryRequest("ZUL0001", "2026-01-01T12:00:00Z");
            when(sessionRepository.findActiveByPlate("ZUL0001"))
                    .thenReturn(Optional.of(activeSession("ZUL0001", Instant.now())));

            assertThatThrownBy(() -> service.handle(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("active session");
        }

        @Test
        @DisplayName("Retorna BAD_REQUEST quando entry_time está ausente")
        void entry_missing_time() {
            var req = entryRequest("ZUL0001", null);
            assertThatThrownBy(() -> service.handle(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("entry_time");
        }

        @Test
        @DisplayName("Evento duplicado é ignorado silenciosamente")
        void entry_duplicate_event() {
            var req = entryRequest("ZUL0001", "2026-01-01T12:00:00Z");
            doThrow(DataIntegrityViolationException.class).when(eventRepository).save(any());

            WebhookEventResponse resp = service.handle(req);

            assertThat(resp.getStatus()).isEqualTo("OK");
            assertThat(resp.getMessage()).contains("Duplicate");
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Aceita timestamp com offset de timezone")
        void entry_offset_datetime() {
            var req = entryRequest("ZUL0001", "2026-01-01T09:00:00-03:00");
            when(sessionRepository.findActiveByPlate(any())).thenReturn(Optional.empty());
            when(sessionRepository.save(any())).thenAnswer(inv -> {
                ParkingSession s = inv.getArgument(0);
                s.setId(1L);
                return s;
            });

            assertThatCode(() -> service.handle(req)).doesNotThrowAnyException();
        }
    }

    // ================================================================== PARKED
    @Nested
    @DisplayName("PARKED events")
    class ParkedEvents {

        @Test
        @DisplayName("Registra estacionamento com sucesso")
        void parked_success() {
            var lat = new BigDecimal("-23.561684");
            var lng = new BigDecimal("-46.655981");
            var req = parkedRequest("ZUL0001", lat, lng);

            var session = activeSession("ZUL0001", Instant.parse("2026-01-01T12:00:00Z"));
            var sp = spot("A", new BigDecimal("10.00"), false);

            when(sessionRepository.findActiveByPlate("ZUL0001")).thenReturn(Optional.of(session));
            when(spotRepository.findByLatAndLng(lat, lng)).thenReturn(Optional.of(sp));
            when(spotRepository.countBySector("A")).thenReturn(10L);
            when(spotRepository.countBySectorAndOccupiedTrue("A")).thenReturn(3L); // 30% -> multiplier 1.00
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WebhookEventResponse resp = service.handle(req);

            assertThat(resp.getStatus()).isEqualTo("PARKED");
            assertThat(sp.isOccupied()).isTrue();
            assertThat(session.getSpot()).isEqualTo(sp);
        }

        @Test
        @DisplayName("Retorna NOT_FOUND quando não há sessão ativa")
        void parked_no_active_session() {
            var req = parkedRequest("ZUL0001", new BigDecimal("-23.5"), new BigDecimal("-46.6"));
            when(sessionRepository.findActiveByPlate(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handle(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Active session not found");
        }

        @Test
        @DisplayName("Retorna BAD_REQUEST quando lat/lng ausentes")
        void parked_missing_lat_lng() {
            var req = parkedRequest("ZUL0001", null, null);
            when(sessionRepository.findActiveByPlate(any()))
                    .thenReturn(Optional.of(activeSession("ZUL0001", Instant.now())));

            assertThatThrownBy(() -> service.handle(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("lat/lng");
        }

        @Test
        @DisplayName("Retorna CONFLICT quando vaga já está ocupada")
        void parked_spot_already_occupied() {
            var lat = new BigDecimal("-23.561684");
            var lng = new BigDecimal("-46.655981");
            var req = parkedRequest("ZUL0001", lat, lng);

            when(sessionRepository.findActiveByPlate(any()))
                    .thenReturn(Optional.of(activeSession("ZUL0001", Instant.now())));
            when(spotRepository.findByLatAndLng(lat, lng))
                    .thenReturn(Optional.of(spot("A", new BigDecimal("10.00"), true))); // occupied=true

            assertThatThrownBy(() -> service.handle(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Spot already occupied");
        }
    }

    // ================================================================== EXIT
    @Nested
    @DisplayName("EXIT events")
    class ExitEvents {

        @Test
        @DisplayName("Registra saída e calcula valor — mais de 30 minutos")
        void exit_calculates_price_after_30min() {
            var sp = spot("A", new BigDecimal("10.00"), true);
            var session = ParkingSession.builder()
                    .id(10L).plate("ZUL0001")
                    .entryTime(Instant.parse("2026-01-01T10:00:00Z"))
                    .status(ParkingSession.Status.PARKED)
                    .spot(sp)
                    .priceMultiplierApplied(BigDecimal.ONE)
                    .build();

            var req = exitRequest("ZUL0001", "2026-01-01T11:30:00Z"); // 90 min -> ceil(60min) = 1h -> R$10

            when(sessionRepository.findActiveByPlate("ZUL0001")).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WebhookEventResponse resp = service.handle(req);

            assertThat(resp.getStatus()).isEqualTo("EXIT");
            assertThat(session.getTotalPaid()).isEqualByComparingTo("10.00");
            assertThat(sp.isOccupied()).isFalse();
        }

        @Test
        @DisplayName("Primeiros 30 minutos são gratuitos")
        void exit_free_within_30_minutes() {
            var sp = spot("A", new BigDecimal("10.00"), true);
            var session = ParkingSession.builder()
                    .id(10L).plate("ZUL0001")
                    .entryTime(Instant.parse("2026-01-01T10:00:00Z"))
                    .status(ParkingSession.Status.PARKED)
                    .spot(sp)
                    .priceMultiplierApplied(BigDecimal.ONE)
                    .build();

            var req = exitRequest("ZUL0001", "2026-01-01T10:29:00Z"); // 29 min -> grátis

            when(sessionRepository.findActiveByPlate("ZUL0001")).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handle(req);

            assertThat(session.getTotalPaid()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Aplica multiplicador de preço dinâmico corretamente")
        void exit_applies_price_multiplier() {
            var sp = spot("A", new BigDecimal("10.00"), true);
            var session = ParkingSession.builder()
                    .id(10L).plate("ZUL0001")
                    .entryTime(Instant.parse("2026-01-01T10:00:00Z"))
                    .status(ParkingSession.Status.PARKED)
                    .spot(sp)
                    .priceMultiplierApplied(new BigDecimal("1.25")) // 75-100% lotação
                    .build();

            var req = exitRequest("ZUL0001", "2026-01-01T11:30:00Z"); // 90 min = 1h -> 10 * 1.25 = 12.50

            when(sessionRepository.findActiveByPlate("ZUL0001")).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handle(req);

            assertThat(session.getTotalPaid()).isEqualByComparingTo("12.50");
        }

        @Test
        @DisplayName("Retorna BAD_REQUEST quando exit_time é anterior à entry_time")
        void exit_time_before_entry() {
            var sp = spot("A", new BigDecimal("10.00"), true);
            var session = ParkingSession.builder()
                    .id(10L).plate("ZUL0001")
                    .entryTime(Instant.parse("2026-01-01T12:00:00Z"))
                    .status(ParkingSession.Status.PARKED)
                    .spot(sp)
                    .priceMultiplierApplied(BigDecimal.ONE)
                    .build();

            var req = exitRequest("ZUL0001", "2026-01-01T11:00:00Z"); // antes da entrada

            when(sessionRepository.findActiveByPlate("ZUL0001")).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> service.handle(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("exit_time before entry_time");
        }

        @Test
        @DisplayName("EXIT duplicado retorna resposta sem reprocessar")
        void exit_already_processed() {
            var sp = spot("A", new BigDecimal("10.00"), false);
            var session = ParkingSession.builder()
                    .id(10L).plate("ZUL0001")
                    .entryTime(Instant.parse("2026-01-01T10:00:00Z"))
                    .exitTime(Instant.parse("2026-01-01T11:00:00Z")) // já tem exitTime
                    .status(ParkingSession.Status.EXIT)
                    .spot(sp)
                    .priceMultiplierApplied(BigDecimal.ONE)
                    .build();

            var req = exitRequest("ZUL0001", "2026-01-01T11:00:00Z");
            when(sessionRepository.findActiveByPlate("ZUL0001")).thenReturn(Optional.of(session));

            WebhookEventResponse resp = service.handle(req);
            assertThat(resp.getStatus()).isEqualTo("EXIT");
            verify(sessionRepository, never()).save(any());
        }
    }

    // ================================================================== MULTIPLICADORES
    @Nested
    @DisplayName("Regras de preço dinâmico (multiplier)")
    class DynamicPricing {

        private void setupParked(String plate, ParkingSpot sp, long total, long occupied) {
            var session = activeSession(plate, Instant.parse("2026-01-01T10:00:00Z"));
            var lat = sp.getLat();
            var lng = sp.getLng();

            var req = parkedRequest(plate, lat, lng);
            when(sessionRepository.findActiveByPlate(plate)).thenReturn(Optional.of(session));
            when(spotRepository.findByLatAndLng(lat, lng)).thenReturn(Optional.of(sp));
            when(spotRepository.countBySector(sp.getSector())).thenReturn(total);
            when(spotRepository.countBySectorAndOccupiedTrue(sp.getSector())).thenReturn(occupied);
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handle(req);
        }

        @Test @DisplayName("< 25% ocupação -> desconto 10% (multiplier 0.90)")
        void multiplier_below_25() {
            var sp = spot("A", new BigDecimal("10.00"), false);
            setupParked("ZUL0001", sp, 100L, 10L); // 10%
            // multiplier gravado na sessão
            ArgumentCaptor<ParkingSession> cap = ArgumentCaptor.forClass(ParkingSession.class);
            verify(sessionRepository, atLeastOnce()).save(cap.capture());
            assertThat(cap.getValue().getPriceMultiplierApplied()).isEqualByComparingTo("0.90");
        }

        @Test @DisplayName("25% a 50% -> sem alteração (multiplier 1.00)")
        void multiplier_25_to_50() {
            var sp = spot("A", new BigDecimal("10.00"), false);
            setupParked("ZUL0001", sp, 100L, 30L); // 30%
            ArgumentCaptor<ParkingSession> cap = ArgumentCaptor.forClass(ParkingSession.class);
            verify(sessionRepository, atLeastOnce()).save(cap.capture());
            assertThat(cap.getValue().getPriceMultiplierApplied()).isEqualByComparingTo("1.00");
        }

        @Test @DisplayName("50% a 75% -> aumento 10% (multiplier 1.10)")
        void multiplier_50_to_75() {
            var sp = spot("A", new BigDecimal("10.00"), false);
            setupParked("ZUL0001", sp, 100L, 60L); // 60%
            ArgumentCaptor<ParkingSession> cap = ArgumentCaptor.forClass(ParkingSession.class);
            verify(sessionRepository, atLeastOnce()).save(cap.capture());
            assertThat(cap.getValue().getPriceMultiplierApplied()).isEqualByComparingTo("1.10");
        }

        @Test @DisplayName("75% a 100% -> aumento 25% (multiplier 1.25)")
        void multiplier_75_to_100() {
            var sp = spot("A", new BigDecimal("10.00"), false);
            setupParked("ZUL0001", sp, 100L, 80L); // 80%
            ArgumentCaptor<ParkingSession> cap = ArgumentCaptor.forClass(ParkingSession.class);
            verify(sessionRepository, atLeastOnce()).save(cap.capture());
            assertThat(cap.getValue().getPriceMultiplierApplied()).isEqualByComparingTo("1.25");
        }
    }

    // ================================================================== EDGE CASES
    @Nested
    @DisplayName("Casos de borda gerais")
    class EdgeCases {

        @Test @DisplayName("event_type inválido lança BAD_REQUEST")
        void invalid_event_type() {
            var req = new WebhookEventRequest();
            req.setEventType("INVALID");
            req.setLicensePlate("ZUL0001");

            assertThatThrownBy(() -> service.handle(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Invalid event_type");
        }

        @Test @DisplayName("license_plate ausente lança BAD_REQUEST")
        void missing_plate() {
            var req = new WebhookEventRequest();
            req.setEventType("ENTRY");
            req.setLicensePlate(null);

            assertThatThrownBy(() -> service.handle(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("license_plate");
        }

        @Test @DisplayName("event_type é case-insensitive")
        void event_type_case_insensitive() {
            var req = entryRequest("ZUL0001", "2026-01-01T12:00:00Z");
            req.setEventType("entry"); // minúsculo

            when(sessionRepository.findActiveByPlate(any())).thenReturn(Optional.empty());
            when(sessionRepository.save(any())).thenAnswer(inv -> {
                ParkingSession s = inv.getArgument(0);
                s.setId(1L);
                return s;
            });

            assertThatCode(() -> service.handle(req)).doesNotThrowAnyException();
        }

        @Test @DisplayName("Timestamp sem timezone é aceito como UTC")
        void timestamp_without_timezone() {
            var req = entryRequest("ZUL0001", "2026-01-01T12:00:00");
            when(sessionRepository.findActiveByPlate(any())).thenReturn(Optional.empty());
            when(sessionRepository.save(any())).thenAnswer(inv -> {
                ParkingSession s = inv.getArgument(0);
                s.setId(1L);
                return s;
            });

            assertThatCode(() -> service.handle(req)).doesNotThrowAnyException();
        }
    }
}