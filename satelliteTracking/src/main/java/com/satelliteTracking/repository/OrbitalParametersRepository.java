package com.satelliteTracking.repository;

import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Set;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface OrbitalParametersRepository extends JpaRepository<OrbitalParameters, Long> {

    List<OrbitalParameters> findBySatelliteOrderByFetchedAtDesc(Satellite satellite);

    OrbitalParameters findTopBySatelliteOrderByFetchedAtDesc(Satellite satellite);

    OrbitalParameters findTopByOrderByFetchedAtDesc();

    OrbitalParameters findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(Long noradCatId);

    // ✅ FIX IMPORTANTE (CACHE PER NORAD ID)
    Optional<OrbitalParameters> findTopBySatellite_NoradCatIdOrderByFetchedAtDesc(Long noradCatId);

    List<OrbitalParameters> findBySatellite_NoradCatIdIn(Collection<Long> noradCatIds);

    boolean existsBySatelliteAndEpoch(Satellite satellite, String epoch);

    @Query("SELECT CONCAT(op.satellite.noradCatId, '|', op.epoch) FROM OrbitalParameters op WHERE op.satellite.noradCatId IN :noradIds")
    Set<String> findEpochKeysByNoradCatIdIn(@Param("noradIds") Set<Long> noradIds);

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
}