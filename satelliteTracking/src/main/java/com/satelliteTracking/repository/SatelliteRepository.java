package com.satelliteTracking.repository;
import com.satelliteTracking.model.Satellite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SatelliteRepository extends JpaRepository<Satellite, Long> {
    // In futuro puoi aggiungere query tipo findByObjectName
}