package com.satelliteTracking.repository;

import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrbitalParametersRepository extends JpaRepository<OrbitalParameters, Long> {
    
    // Trova tutti i parametri orbitali per un satellite specifico
    List<OrbitalParameters> findBySatelliteOrderByFetchedAtDesc(Satellite satellite);
    
    // Trova i parametri orbitali più recenti per un satellite
    OrbitalParameters findTopBySatelliteOrderByFetchedAtDesc(Satellite satellite);

        // Trova i parametri orbitali più recenti per tutti i satelliti in un solo round-trip
    @Query("""
        select op
        from OrbitalParameters op
        join fetch op.satellite
                where op.fetchedAt = (
              select max(op2.fetchedAt)
              from OrbitalParameters op2
              where op2.satellite = op.satellite
          )
                    and op.id = (
                            select max(op3.id)
                            from OrbitalParameters op3
                            where op3.satellite = op.satellite
                                and op3.fetchedAt = op.fetchedAt
                    )
    """)
        List<OrbitalParameters> findLatestForAllSatellites();
    
    // Trova l'ultimo parametro orbitale scaricato (di qualsiasi satellite)
    OrbitalParameters findTopByOrderByFetchedAtDesc();
}
