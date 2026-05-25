package com.satelliteTracking.service;

import com.satelliteTracking.model.SatcatCache;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.SatcatCacheRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SatcatService {

    private static final Logger log = LoggerFactory.getLogger(SatcatService.class);

    private final SatcatCacheRepository cacheRepository;
    private final SpaceTrackService spaceTrackService;
    private final SatelliteRepository satelliteRepository;

    public SatcatService(SatcatCacheRepository cacheRepository, SpaceTrackService spaceTrackService, SatelliteRepository satelliteRepository) {
        this.cacheRepository = cacheRepository;
        this.spaceTrackService = spaceTrackService;
        this.satelliteRepository = satelliteRepository;
    }

    public String getSatcatJson(Long norad) {
        if (spaceTrackService.isSatcatRateLimited()) {
            log.warn("⏸️ SATCAT rate limited fino a {}: skip NORAD {}", spaceTrackService.getSatcatCooldownUntil(), norad);
            return null;
        }

        SatcatCache cached = cacheRepository.findByNoradCatId(norad).orElse(null);
        if (cached != null && cached.getFetchedAt() != null) {
            if (Duration.between(cached.getFetchedAt(), LocalDateTime.now()).toHours() < 24) {
                log.debug("Satcat cache hit for {}", norad);
                return cached.getJsonData();
            }
        }

        log.info("Fetching SATCAT for NORAD {} from Space-Track", norad);
        String json = spaceTrackService.downloadSatcatByNoradId(norad);
        if (json != null && !json.isBlank()) {
            if (cached == null) cached = new SatcatCache();
            cached.setNoradCatId(norad);
            cached.setJsonData(json);
            cached.setFetchedAt(LocalDateTime.now());
            cacheRepository.save(cached);
        }
        return json;
    }

    @Scheduled(cron = "0 10 18 * * *") // every day at 18:10 server time
    public void dailyEnrichment() {
        final int batchSize = 10; // keep each run small and predictable
        final long pauseBetweenRequestsMs = 2500L; // stay below Space-Track rate limits
        log.info("🔁 Avvio enrichment SATCAT giornaliero (batch size = {})", batchSize);

        if (spaceTrackService.isSatcatRateLimited()) {
            log.warn("⏸️ Enrichment SATCAT rimandato: cooldown attivo fino a {}", spaceTrackService.getSatcatCooldownUntil());
            return;
        }

        int page = 0;
        int updated = 0;
        org.springframework.data.domain.Page<Satellite> result;
        do {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, batchSize);
            result = satelliteRepository.findUnknown("UNKNOWN", pageable);
            if (result == null || result.isEmpty()) break;

            for (Satellite s : result.getContent()) {
                try {
                    if (spaceTrackService.isSatcatRateLimited()) {
                        log.warn("⏸️ Interrompo enrichment: cooldown SATCAT attivo fino a {}", spaceTrackService.getSatcatCooldownUntil());
                        return;
                    }

                    String json = getSatcatJson(s.getNoradCatId());
                    if (json != null && !json.isBlank() && json.contains("\"OBJECT_TYPE\"")) {
                        String objType = null;
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
                            if (root.isArray() && root.size() > 0) {
                                objType = root.get(0).path("OBJECT_TYPE").asText(null);
                            }
                        } catch (Exception e) {
                            log.warn("Errore parsing satcat JSON per {}: {}", s.getNoradCatId(), e.getMessage());
                        }
                        if (objType != null && !objType.isBlank() && !"UNKNOWN".equalsIgnoreCase(objType)) {
                            s.setObjectTypeRaw(objType);
                            String inferred = inferFromMeanMotion(s.getOrbitalParametersList().isEmpty() ? 0.0 : s.getOrbitalParametersList().get(0).getMeanMotion());
                            s.setObjectTypeInferred(inferred);
                            satelliteRepository.save(s);
                            updated++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Errore enrichment satcat per {}: {}", s.getNoradCatId(), e.getMessage());
                } finally {
                    try {
                        Thread.sleep(pauseBetweenRequestsMs);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            page++;
        } while (result.hasNext());

        log.info("🔁 Enrichment SATCAT completato: {} satelliti aggiornati", updated);
    }

    private String inferFromMeanMotion(double meanMotion) {
        if (meanMotion > 11.25) return "LEO_INFERRED";
        if (meanMotion > 1.0) return "MEO_INFERRED";
        return "GEO_INFERRED";
    }

}
