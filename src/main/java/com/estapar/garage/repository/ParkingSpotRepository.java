package com.estapar.garage.repository;

import com.estapar.garage.entity.ParkingSpot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {
    long countBySector(String sector);
    long countBySectorAndOccupiedTrue(String sector);

    Optional<ParkingSpot> findByLatAndLng(Double lat, Double lng);
}