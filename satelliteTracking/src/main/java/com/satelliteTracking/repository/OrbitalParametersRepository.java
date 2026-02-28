package com.satelliteTracking.repository;

import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrbitalParametersRepository extends JpaRepository<OrbitalParameters, Long> {
    
    // Trova tutti i parametri orbitali per un satellite specifico
    List<OrbitalParameters> findBySatelliteOrderByFetchedAtDesc(Satellite satellite);
    
    // Trova i parametri orbitali più recenti per un satellite
    OrbitalParameters findTopBySatelliteOrderByFetchedAtDesc(Satellite satellite);
    
    // Trova l'ultimo parametro orbitale scaricato (di qualsiasi satellite)
    OrbitalParameters findTopByOrderByFetchedAtDesc();
}
