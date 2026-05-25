package com.satelliteTracking.repository;
import com.satelliteTracking.model.Satellite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SatelliteRepository extends JpaRepository<Satellite, Long> {
    // Trova un satellite per NORAD Catalog ID
    Optional<Satellite> findByNoradCatId(Long noradCatId);

    // Trova il primo satellite che contiene il nome richiesto (case-insensitive)
    Optional<Satellite> findFirstByObjectNameContainingIgnoreCaseOrderByIdAsc(String objectName);

    // Trova un satellite per nome esatto
    Optional<Satellite> findByObjectName(String objectName);

    @Query("select s from Satellite s where s.objectTypeRaw is null or upper(s.objectTypeRaw)=upper(:unknown)")
    Page<Satellite> findUnknown(@Param("unknown") String unknown, Pageable pageable);
}