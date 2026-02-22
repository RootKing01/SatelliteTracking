package com.satelliteTracking.repository;
import com.satelliteTracking.model.Satellite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SatelliteRepository extends JpaRepository<Satellite, Long> {
    // Trova un satellite per NORAD Catalog ID
    Optional<Satellite> findByNoradCatId(Long noradCatId);
}