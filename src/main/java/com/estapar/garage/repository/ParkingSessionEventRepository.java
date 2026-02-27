package com.estapar.garage.repository;

import com.estapar.garage.entity.ParkingSessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSessionEventRepository extends JpaRepository<ParkingSessionEvent, Long> {
}