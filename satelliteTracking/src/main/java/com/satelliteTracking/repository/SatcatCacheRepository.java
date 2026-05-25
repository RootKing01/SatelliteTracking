package com.satelliteTracking.repository;

import com.satelliteTracking.model.SatcatCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SatcatCacheRepository extends JpaRepository<SatcatCache, Long> {
    Optional<SatcatCache> findByNoradCatId(Long noradCatId);
}
