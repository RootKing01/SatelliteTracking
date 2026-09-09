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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.HashSet;

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
    log.info("🔁 Avvio enrichment SATCAT giornaliero (single Space-Track call, parsing offline)", scanBatchSize);

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

    log.info("ℹ️ SATCAT enrichment: {} satelliti da aggiornare", distinctNorads.size());

    if (spaceTrackService.isSatcatRateLimited()) {
        log.warn("⏸️ Interrompo enrichment: cooldown SATCAT attivo fino a hourly={} daily={}", spaceTrackService.getSatcatCooldownUntil(), spaceTrackService.getSatcatDailyCooldownUntil());
        return;
    }

    log.info("📥 Fetch full SATCAT");
    String groupedJson = spaceTrackService.downloadFullSatcat();

    if (groupedJson != null && !groupedJson.isBlank()) {
        try {
            JsonNode root = objectMapper.readTree(groupedJson);

            if (root.isArray()) {

                // CARICA SOLO I NORAD CHE CI INTERESSANO
                Map<Long, SatcatCache> existing =
                        cacheRepository.findByNoradCatIdIn(distinctNorads)
                                .stream()
                                .collect(Collectors.toMap(
                                        SatcatCache::getNoradCatId,
                                        Function.identity()
                                ));

                Set<Long> wantedNorads = new HashSet<>(distinctNorads);

                for (JsonNode node : root) {

                    Long id = readOptionalLong(node, "NORAD_CAT_ID");

                    if (id == null || id == 0) {
                        continue;
                    }

                    // IGNORA TUTTI GLI ALTRI SATELLITI
                    if (!wantedNorads.contains(id)) {
                        continue;
                    }

                    String jsonStr = objectMapper.writeValueAsString(node);

                    SatcatCache cache = existing.getOrDefault(id, new SatcatCache());

                    cache.setNoradCatId(id);
                    cache.setJsonData(jsonStr);
                    cache.setFetchedAt(LocalDateTime.now(ZoneOffset.UTC));

                    cacheRepository.save(cache);
                }
            }
        } catch (Exception e) {
            log.warn("Errore parsing SATCAT JSON: {}", e.getMessage());
        }
    }

    // After caching, update satellites
    int updated = 0;
    int missingCache = 0;
    int blankJson = 0;
    int missingObjectType = 0;
    int unknownObjectType = 0;
    int pendingObjectType = 0;
    int skippedOtherObjectType = 0;
    int skippedSampleLogs = 0;
    Map<String, Integer> skippedObjectTypeCounts = new HashMap<>();
    List<SatcatCache> refreshed = cacheRepository.findByNoradCatIdIn(distinctNorads);
    Map<Long, SatcatCache> cacheMap = refreshed.stream().collect(Collectors.toMap(SatcatCache::getNoradCatId, c -> c));

    for (Long norad : distinctNorads) {
        try {
            SatcatCache c = cacheMap.get(norad);
            if (c == null) {
                missingCache++;
                continue;
            }
            if (c.getJsonData() == null || c.getJsonData().isBlank()) {
                blankJson++;
                continue;
            }

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

            if (objType == null || objType.isBlank()) {
                missingObjectType++;
                skippedObjectTypeCounts.merge("<blank>", 1, Integer::sum);
                if (skippedSampleLogs < 5) {
                    log.info("🧪 SATCAT skipped NORAD {} -> OBJECT_TYPE vuoto/null", norad);
                    skippedSampleLogs++;
                }
                continue;
            }

            if ("UNKNOWN".equalsIgnoreCase(objType)) {
                unknownObjectType++;
                skippedObjectTypeCounts.merge("UNKNOWN", 1, Integer::sum);
                if (skippedSampleLogs < 5) {
                    log.info("🧪 SATCAT skipped NORAD {} -> OBJECT_TYPE={}", norad, objType);
                    skippedSampleLogs++;
                }
                continue;
            }

            String normalizedObjType = objType.trim().toLowerCase();
            if (normalizedObjType.startsWith("tba")
                    || normalizedObjType.contains("to be assigned")
                    || normalizedObjType.contains("to be determined")) {
                pendingObjectType++;
                skippedObjectTypeCounts.merge(objType.trim(), 1, Integer::sum);
                if (skippedSampleLogs < 5) {
                    log.info("🧪 SATCAT skipped NORAD {} -> OBJECT_TYPE={} (pending)", norad, objType);
                    skippedSampleLogs++;
                }
                continue;
            }

            if (updated < 5) {
                log.info("🧪 SATCAT update candidate NORAD {} -> OBJECT_TYPE={}", norad, objType);
            }

            final String finalObjType = objType;
            satelliteRepository.findByNoradCatId(norad).ifPresent(s -> {
                s.setObjectTypeRaw(finalObjType);
                String inferred = inferFromLatestOrbitalParameters(s);
                s.setObjectTypeInferred(inferred);
                satelliteRepository.save(s);
            });
            updated++;
        } catch (Exception e) {
            log.warn("Errore aggiornamento satellite per {}: {}", norad, e.getMessage());
        }
    }

        log.info("🧾 SATCAT enrichment summary: updated={}, missingCache={}, blankJson={}, missingObjectType={}, unknownObjectType={}, pendingObjectType={}, skippedOtherObjectType={}, total={}",
            updated, missingCache, blankJson, missingObjectType, unknownObjectType, pendingObjectType, skippedOtherObjectType, distinctNorads.size());

        if (!skippedObjectTypeCounts.isEmpty()) {
            String skippedSummary = skippedObjectTypeCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                    .limit(10)
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
            log.info("🧾 SATCAT skipped object types (top 10): {}", skippedSummary);
        }

    log.info("🔁 Enrichment SATCAT completato: {}/{} satelliti aggiornati", updated, distinctNorads.size());
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
