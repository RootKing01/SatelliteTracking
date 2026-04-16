package com.satelliteTracking.repository;

import com.satelliteTracking.model.SatelliteSighting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SatelliteSightingRepository extends JpaRepository<SatelliteSighting, Long> {

    List<SatelliteSighting> findByUserIdOrderBySightedAtDesc(Long userId);
}
