package com.satelliteTracking.service;

import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.SatcatCache;
import com.satelliteTracking.repository.SatcatCacheRepository;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SatcatService {

    private static final Logger log = LoggerFactory.getLogger(SatcatService.class);

    private final SatcatCacheRepository cacheRepository;
    private final SpaceTrackService spaceTrackService;
    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;

    @Value("${satcat.batch.size:100}")
    private int scanBatchSize;

    @Value("${satcat.norads.per.call:500}")
    private int noradsPerCall;

    @Value("${satcat.pause.ms:3000}")
    private long pauseBetweenRequestsMs;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SatcatService(SatcatCacheRepository cacheRepository,
                         SpaceTrackService spaceTrackService,
                         SatelliteRepository satelliteRepository,
                         OrbitalParametersRepository orbitalParametersRepository) {
        this.cacheRepository = cacheRepository;
        this.spaceTrackService = spaceTrackService;
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    public String getSatcatJson(Long norad) {
        if (spaceTrackService.isSatcatRateLimited()) {
            log.warn("⏸️ SATCAT rate limited fino a hourly={} daily={}: skip NORAD {}", spaceTrackService.getSatcatCooldownUntil(), spaceTrackService.getSatcatDailyCooldownUntil(), norad);
            return null;
        }

        SatcatCache cached = cacheRepository.findByNoradCatId(norad).orElse(null);
        if (cached != null && cached.getFetchedAt() != null) {
            if (Duration.between(cached.getFetchedAt(), LocalDateTime.now(ZoneOffset.UTC)).toHours() < 24) {
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
            cached.setFetchedAt(LocalDateTime.now(ZoneOffset.UTC));
            cacheRepository.save(cached);
        }
        return json;
    }

    @Scheduled(cron = "0 10 18 * * *", zone = "UTC") // every day at 18:10 UTC
    public void dailyEnrichment() {
        log.info("🔁 Avvio enrichment SATCAT giornaliero (scan batch = {}, single Space-Track call, parsing offline)", scanBatchSize);

        if (spaceTrackService.isSatcatRateLimited()) {
            log.warn("⏸️ Enrichment SATCAT rimandato: cooldown attivo fino a hourly={} daily={}", spaceTrackService.getSatcatCooldownUntil(), spaceTrackService.getSatcatDailyCooldownUntil());
            return;
        }

        // 1) Scan all unknown satellites and collect NORAD ids
        List<Long> allNorads = new ArrayList<>();
        int page = 0;
        org.springframework.data.domain.Page<Satellite> pageResult;
        do {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, scanBatchSize);
            pageResult = satelliteRepository.findUnknown("UNKNOWN", pageable);
            if (pageResult == null || pageResult.isEmpty()) break;
            for (Satellite s : pageResult.getContent()) {
                if (s.getNoradCatId() != null && s.getNoradCatId() > 0) allNorads.add(s.getNoradCatId());
            }
            page++;
        } while (pageResult.hasNext());

        if (allNorads.isEmpty()) {
            log.info("ℹ️ Nessun satellite pending per enrichment");
            return;
        }

        // Remove duplicates
        List<Long> distinctNorads = allNorads.stream().distinct().collect(Collectors.toList());

        // Exclude those already in cache and fresh
        List<SatcatCache> cachedList = cacheRepository.findByNoradCatIdIn(distinctNorads);
        Set<Long> freshCached = cachedList.stream()
                .filter(c -> c.getFetchedAt() != null && Duration.between(c.getFetchedAt(), LocalDateTime.now(ZoneOffset.UTC)).toHours() < 24)
            .map(SatcatCache::getNoradCatId)
                .collect(Collectors.toSet());

        List<Long> toFetchAll = distinctNorads.stream().filter(n -> !freshCached.contains(n)).collect(Collectors.toList());

        int skippedCached = distinctNorads.size() - toFetchAll.size();

        log.info("ℹ️ SATCAT enrichment: distinctNorads={}, toFetch={}, cachedSkip={}", distinctNorads.size(), toFetchAll.size(), skippedCached);

        if (!toFetchAll.isEmpty()) {
            if (spaceTrackService.isSatcatRateLimited()) {
                log.warn("⏸️ Interrompo enrichment: cooldown SATCAT attivo fino a hourly={} daily={}", spaceTrackService.getSatcatCooldownUntil(), spaceTrackService.getSatcatDailyCooldownUntil());
                return;
            }
            try {
                log.info("📥 Fetch grouped SATCAT for {} ids (sample={})", toFetchAll.size(), toFetchAll.get(0));
                String groupedJson = spaceTrackService.downloadSatcatByNoradIds(toFetchAll);
                if (groupedJson != null && !groupedJson.isBlank()) {
                    try {
                        JsonNode root = objectMapper.readTree(groupedJson);
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                Long id = readOptionalLong(node, "NORAD_CAT_ID");
                                if (id == null || id == 0) continue;
                                String jsonStr = objectMapper.writeValueAsString(node);
                                SatcatCache cache = cacheRepository.findByNoradCatId(id).orElseGet(SatcatCache::new);
                                cache.setNoradCatId(id);
                                cache.setJsonData(jsonStr);
                                cache.setFetchedAt(LocalDateTime.now(ZoneOffset.UTC));
                                cacheRepository.save(cache);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Errore parsing grouped satcat JSON: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Errore fetching grouped SATCAT: {}", e.getMessage());
            }
        }

        // After caching, update satellites from cache
        int updated = 0;
        List<SatcatCache> refreshed = cacheRepository.findByNoradCatIdIn(distinctNorads);
        Map<Long, SatcatCache> cacheMap = refreshed.stream().collect(Collectors.toMap(SatcatCache::getNoradCatId, c -> c));

        for (Long norad : distinctNorads) {
            try {
                SatcatCache c = cacheMap.get(norad);
                if (c == null || c.getJsonData() == null || c.getJsonData().isBlank()) continue;

                String json = c.getJsonData();
                String objType = null;
                try {
                    JsonNode root = objectMapper.readTree(json);
                    if (root.isArray() && root.size() > 0) {
                        objType = root.get(0).path("OBJECT_TYPE").asText(null);
                    } else if (root.isObject()) {
                        objType = root.path("OBJECT_TYPE").asText(null);
                    }
                } catch (Exception e) {
                    log.warn("Errore parsing satcat JSON per {}: {}", norad, e.getMessage());
                }

                if (objType != null && !objType.isBlank() && !"UNKNOWN".equalsIgnoreCase(objType)) {
                    final String finalObjType = objType;
                    satelliteRepository.findByNoradCatId(norad).ifPresent(s -> {
                        s.setObjectTypeRaw(finalObjType);
                        String inferred = inferFromLatestOrbitalParameters(s);
                        s.setObjectTypeInferred(inferred);
                        satelliteRepository.save(s);
                    });
                    updated++;
                }
            } catch (Exception e) {
                log.warn("Errore aggiornamento satellite per {}: {}", norad, e.getMessage());
            }
        }

        log.info("🔁 Enrichment SATCAT completato: {} satelliti aggiornati (singleCall={}, skippedCached={})", updated, !toFetchAll.isEmpty(), skippedCached);
    }

    private Long readOptionalLong(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (field.isNumber()) {
            return field.asLong();
        }
        if (field.isTextual()) {
            String text = field.asText("").trim();
            if (!text.isBlank()) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String inferFromLatestOrbitalParameters(Satellite satellite) {
        if (satellite == null) {
            return "UNKNOWN_INFERRED";
        }

        OrbitalParameters latestParams = orbitalParametersRepository.findTopBySatelliteOrderByFetchedAtDesc(satellite);

        if (latestParams == null) {
            return "UNKNOWN_INFERRED";
        }

        double meanMotion = latestParams.getMeanMotion();
        double eccentricity = latestParams.getEccentricity();

        if (meanMotion > 11.25) {
            return "LEO_INFERRED";
        }
        if (meanMotion > 1.0) {
            return "MEO_INFERRED";
        }
        if (eccentricity >= 0.25) {
            return "HEO_INFERRED";
        }
        return "GEO_INFERRED";
    }

}
