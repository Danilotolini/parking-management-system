package com.estapar.garage.repository;

import com.estapar.garage.entity.ParkingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long> {

    Optional<ParkingSession> findFirstByPlateAndStatusNotOrderByEntryTimeDesc(String plate, ParkingSession.Status status);

    @Query("""
           select ps from ParkingSession ps
           where ps.plate = :plate and ps.exitTime is null
           order by ps.entryTime desc
           """)
    Optional<ParkingSession> findActiveByPlate(String plate);

    @Query("""
           select coalesce(sum(ps.totalPaid), 0) from ParkingSession ps
           where ps.spot.sector = :sector
             and ps.exitTime >= :start
             and ps.exitTime < :end
             and ps.totalPaid is not null
           """)
    java.math.BigDecimal sumRevenueBySectorBetween(String sector, Instant start, Instant end);
}