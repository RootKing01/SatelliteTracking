package com.satelliteTracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.service.SpaceTrackService.DeltaFetchResult;
import com.satelliteTracking.service.SpaceTrackService.DeltaFetchStatus;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TleDataService {

    private static final double MEO_THRESHOLD = 2.0;

    private enum FetchOutcome {
        DATA,
        NO_CHANGE,
        FALLBACK_USED,
        ERROR
    }

    private static final class ParseResult {
        private final int saved;
        private final int skipped;
        private final int created;
        private final int errors;

        private ParseResult(int saved, int skipped, int created, int errors) {
            this.saved = saved;
            this.skipped = skipped;
            this.created = created;
            this.errors = errors;
        }

        private boolean isNoOp() {
            return saved == 0 && created == 0 && errors == 0;
        }
    }

    private static final class ParsedSatelliteRow {
        private final long norad;
        private final String objectName;
        private final String objectId;
        private final String epoch;
        private final double inclination;
        private final double raOfAscNode;
        private final double eccentricity;
        private final double argOfPericenter;
        private final double meanAnomaly;
        private final double meanMotion;
        private final String tleLine1;
        private final String tleLine2;
        private final String rawType;

        private ParsedSatelliteRow(
                long norad,
                String objectName,
                String objectId,
                String epoch,
                double inclination,
                double raOfAscNode,
                double eccentricity,
                double argOfPericenter,
                double meanAnomaly,
                double meanMotion,
                String tleLine1,
                String tleLine2,
                String rawType) {
            this.norad = norad;
            this.objectName = objectName;
            this.objectId = objectId;
            this.epoch = epoch;
            this.inclination = inclination;
            this.raOfAscNode = raOfAscNode;
            this.eccentricity = eccentricity;
            this.argOfPericenter = argOfPericenter;
            this.meanAnomaly = meanAnomaly;
            this.meanMotion = meanMotion;
            this.tleLine1 = tleLine1;
            this.tleLine2 = tleLine2;
            this.rawType = rawType;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TleDataService.class);

    private final SpaceTrackService spaceTrackService;
    private final CelestrakService celestrakService;
    private final SatelliteAuditFileService satelliteAuditFileService;
    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;
    private final ObjectMapper objectMapper;

    @Value("${tle.leo.threshold:11.0}")
    private double leoThreshold;

    @Value("${tle.source.primary:spacetrack}")
    private String primarySource;

    public TleDataService(
            SpaceTrackService spaceTrackService,
            CelestrakService celestrakService,
            SatelliteAuditFileService satelliteAuditFileService,
            SatelliteRepository satelliteRepository,
            OrbitalParametersRepository orbitalParametersRepository,
            ObjectMapper objectMapper) {
        this.spaceTrackService = spaceTrackService;
        this.celestrakService = celestrakService;
        this.satelliteAuditFileService = satelliteAuditFileService;
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void updateTle() {
        log.info("╔═══════════════════════════════════════════════════════════╗");
        log.info("║  🚀 AGGIORNAMENTO TLE COMPLETO INIZIATO                   ║");
        log.info("╚═══════════════════════════════════════════════════════════╝");
        log.info("⚙️  Sorgente primaria: {}", primarySource);

        try {
            if (!"spacetrack".equalsIgnoreCase(primarySource)) {
                log.info("🎯 Usando CELESTRAK come sorgente primaria...");
                celestrakService.fetchAndSaveStations();
                log.info("✅ Aggiornamento CelesTrak completato");
                return;
            }

            log.info("🎯 Tentativo download da SPACE-TRACK...");
            FetchOutcome outcome = fetchFromSpaceTrack();

            if (outcome == FetchOutcome.ERROR) {
                OrbitalParameters last = orbitalParametersRepository
                        .findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L);
                long hoursSinceLast = 0;
                if (last != null) {
                    hoursSinceLast = java.time.Duration.between(last.getFetchedAt(), java.time.LocalDateTime.now(ZoneOffset.UTC)).toHours();
                }
                if (hoursSinceLast <= 48) {
                    log.warn("⚠️ SPACE-TRACK FALLITO - attivo fallback CelesTrak");
                    celestrakService.fetchAndSaveStations();
                    log.info("✅ Fallback CelesTrak completato");
                }
            }

            log.info("╔═══════════════════════════════════════════════════════════╗");
            log.info("║  ✅ AGGIORNAMENTO TLE COMPLETATO                         ║");
            log.info("╚═══════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            log.error("❌ ERRORE CRITICO: {}", e.getMessage(), e);
            try {
                celestrakService.fetchAndSaveStations();
                log.info("✅ Fallback d'emergenza CelesTrak riuscito");
            } catch (Exception fe) {
                log.error("❌ Anche il fallback CelesTrak è fallito: {}", fe.getMessage(), fe);
            }
        }
    }

    @Transactional
    public void updateTleLeoOnly() {
        log.info("╔═══════════════════════════════════════════════════════════╗");
        log.info("║  🛰️  AGGIORNAMENTO TLE LEO INIZIATO                      ║");
        log.info("╚═══════════════════════════════════════════════════════════╝");

        try {
            if (!"spacetrack".equalsIgnoreCase(primarySource)) {
                log.info("⚠️ Aggiornamento LEO disponibile solo con Space-Track, skip");
                return;
            }
            FetchOutcome outcome = fetchLeoFromSpaceTrack();
            if (outcome == FetchOutcome.ERROR) {
                log.warn("⚠️ Aggiornamento LEO fallito");
                log.warn("⚠️ Attivo fallback CelesTrak per mantenere aggiornati i dati disponibili");
                celestrakService.fetchAndSaveStations();
                log.info("✅ Fallback CelesTrak completato per aggiornamento LEO");
            } else {
                log.info("✅ AGGIORNAMENTO TLE LEO COMPLETATO");
            }
        } catch (Exception e) {
            log.error("❌ Errore durante aggiornamento LEO: {}", e.getMessage(), e);
            try {
                log.warn("⚠️ Fallback CelesTrak d'emergenza per LEO...");
                celestrakService.fetchAndSaveStations();
                log.info("✅ Fallback CelesTrak d'emergenza completato");
            } catch (Exception fe) {
                log.error("❌ Anche il fallback CelesTrak per LEO è fallito: {}", fe.getMessage(), fe);
            }
        }
    }

    private FetchOutcome fetchFromSpaceTrack() {
        log.info("───────────────────────────────────────────────────────────");
        log.info("📡 Fetch DELTA da Space-Track (tutti i satelliti)");
        log.info("   💡 Filtro su CREATION_DATE via REST path per delta corretti");
        log.info("───────────────────────────────────────────────────────────");
        satelliteAuditFileService.appendFetchHeader(
            "spacetrack",
            LocalDateTime.now(ZoneOffset.UTC),
            "delta completo"
        );

        try {
            OrbitalParameters last = orbitalParametersRepository
                    .findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L);

            if (last == null) {
                log.warn("⚠️ Database vuoto, impossibile fare delta - usa CelesTrak prima");
                celestrakService.fetchAndSaveStations();
                log.info("✅ Fallback CelesTrak completato per database vuoto");
                return FetchOutcome.FALLBACK_USED;
            }

            LocalDateTime lastFetchedAt = last.getFetchedAt();
            log.info("✅ Ultimo fetch: {}", lastFetchedAt);
            log.info("🔄 Richiesta delta TLE con CREATION_DATE > {}...", lastFetchedAt);

            // Se sono passate più di 48 ore dall'ultimo aggiornamento, forza fallback
            long hoursSinceLast = java.time.Duration.between(lastFetchedAt, LocalDateTime.now(ZoneOffset.UTC)).toHours();
            if (hoursSinceLast > 48) {
                log.warn("⏳ Sono passate {} ore dall'ultimo aggiornamento: fallback obbligatorio a CelesTrak", hoursSinceLast);
                celestrakService.fetchAndSaveStations();
                log.info("✅ Fallback CelesTrak forzato dopo {} ore", hoursSinceLast);
                return FetchOutcome.FALLBACK_USED;
            }

            // ⚡ FIX: passa il timestamp diretto, non una stringa di query
            DeltaFetchResult delta = spaceTrackService.downloadDeltaTle(lastFetchedAt);
            if (delta == null || delta.getStatus() == DeltaFetchStatus.ERROR) {
                log.warn("❌ Space-Track ha restituito un errore sul delta");
                return FetchOutcome.ERROR;
            }

            if (delta.getStatus() == DeltaFetchStatus.SUCCESS_EMPTY) {
                log.info("ℹ️ Nessun aggiornamento TLE disponibile → database già aggiornato");
                log.info("✅ SPACE-TRACK: NESSUN AGGIORNAMENTO NECESSARIO");
                return FetchOutcome.NO_CHANGE;
            }

            if (delta.getStatus() != DeltaFetchStatus.SUCCESS_WITH_DATA) {
                log.warn("❌ Space-Track ha restituito uno stato delta inatteso: {}", delta.getStatus());
                return FetchOutcome.ERROR;
            }

            String tleData = delta.getRaw();
            log.debug("[DEBUG] Risposta grezza Space-Track: {}", tleData);

            log.info("✅ Dati delta ricevuti: {} bytes", tleData.length());

            long startParse = System.currentTimeMillis();
            ParseResult parseResult = parseAndSaveJsonDetailed(tleData, true);
            long parseDuration = System.currentTimeMillis() - startParse;

            if (parseResult.isNoOp()) {
                log.info("ℹ️ Delta Space-Track senza nuove modifiche rilevanti");
                log.info("✅ SPACE-TRACK: NESSUN AGGIORNAMENTO NECESSARIO");
                return FetchOutcome.NO_CHANGE;
            }

            if (log.isDebugEnabled()) {
                String preview = tleData.substring(0, Math.min(500, tleData.length()));
                log.debug("   Preview dati ricevuti:\n{}", preview);
            }

            log.info("✅ SPACE-TRACK DELTA IMPORT COMPLETATO");
            log.info("   Satelliti aggiornati: {}", parseResult.saved);
            log.info("   Tempo parsing: {} ms", parseDuration);
                return FetchOutcome.DATA;

        } catch (Exception e) {
            log.error("❌ Errore fetch delta: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return FetchOutcome.ERROR;
        }
    }

    private FetchOutcome fetchLeoFromSpaceTrack() {
        log.info("───────────────────────────────────────────────────────────");
        log.info("📡 Fetch DELTA LEO da Space-Track");
        log.info("───────────────────────────────────────────────────────────");
        satelliteAuditFileService.appendFetchHeader(
            "spacetrack",
            LocalDateTime.now(ZoneOffset.UTC),
            "delta LEO"
        );

        try {
            OrbitalParameters last = orbitalParametersRepository
                    .findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L);

            if (last == null) {
                log.warn("⚠️ Database vuoto, skip LEO update");
                return FetchOutcome.ERROR;
            }

            LocalDateTime lastFetchedAt = last.getFetchedAt();
            log.info("✅ Ultimo fetch: {}", lastFetchedAt);

            DeltaFetchResult delta = spaceTrackService.downloadDeltaTleLeoOnly(lastFetchedAt);

            if (delta == null || delta.getStatus() == DeltaFetchStatus.ERROR) {
                log.warn("❌ Space-Track LEO ha restituito un errore sul delta");
                return FetchOutcome.ERROR;
            }

            if (delta.getStatus() == DeltaFetchStatus.SUCCESS_EMPTY) {
                log.info("ℹ️ Nessun aggiornamento TLE LEO disponibile");
                log.info("✅ SPACE-TRACK LEO: NESSUN AGGIORNAMENTO NECESSARIO");
                return FetchOutcome.NO_CHANGE;
            }

            if (delta.getStatus() != DeltaFetchStatus.SUCCESS_WITH_DATA) {
                log.warn("❌ Space-Track LEO ha restituito uno stato delta inatteso: {}", delta.getStatus());
                return FetchOutcome.ERROR;
            }

            String tleData = delta.getRaw();

            log.info("✅ Dati delta LEO ricevuti: {} bytes", tleData.length());
            
            // 🔍 DEBUG: mostra le prime 2000 char del JSON per vedere se OBJECT_NAME è presente
            if (tleData.length() < 2000) {
                log.info("🔍 [DEBUG] JSON completo:\n{}", tleData);
            } else {
                log.info("🔍 [DEBUG] JSON preview (primi 2000 char):\n{}", tleData.substring(0, 2000));
            }

            long startParse = System.currentTimeMillis();
            ParseResult parseResult = parseAndSaveJsonDetailed(tleData, true);
            long parseDuration = System.currentTimeMillis() - startParse;

            if (parseResult.isNoOp()) {
                log.info("ℹ️ Delta LEO Space-Track senza nuove modifiche rilevanti");
                log.info("✅ SPACE-TRACK LEO: NESSUN AGGIORNAMENTO NECESSARIO");
                return FetchOutcome.NO_CHANGE;
            }

            log.info("✅ SPACE-TRACK DELTA LEO COMPLETATO");
            log.info("   Satelliti LEO aggiornati: {}", parseResult.saved);
            log.info("   Tempo parsing: {} ms", parseDuration);
            return FetchOutcome.DATA;

        } catch (Exception e) {
            log.error("❌ Errore fetch delta LEO: {}", e.getMessage(), e);
            return FetchOutcome.ERROR;
        }
    }

   // In TleDataService.java

int parseAndSaveJson(String jsonData) {
    return parseAndSaveJson(jsonData, false);
}

int parseAndSaveJson(String jsonData, boolean leoOnly) {
    return parseAndSaveJsonDetailed(jsonData, leoOnly).saved;
}

    private ParseResult parseAndSaveJsonDetailed(String jsonData, boolean leoOnly) {
        log.info("🔍 Inizio parsing TLE (JSON)...");
        int count = 0;
        int skipped = 0;
        int created = 0;
        int errors = 0;

        try {
            JsonNode root = objectMapper.readTree(jsonData);
            if (root == null || !root.isArray()) {
                log.warn("⚠️ JSON Space-Track non valido o non array");
                return new ParseResult(0, 0, 0, 1);
            }

            Set<Long> noradIds = new HashSet<>();
            for (JsonNode node : root) {
                Long norad = readOptionalLong(node, "NORAD_CAT_ID");
                if (norad > 0) {
                    noradIds.add(norad);
                }
            }

            List<Satellite> foundSatellites = satelliteRepository.findByNoradCatIdIn(noradIds);
            if (foundSatellites == null) {
                foundSatellites = List.of();
            }
            Map<Long, Satellite> satellitesByNorad = foundSatellites.stream()
                    .collect(Collectors.toMap(Satellite::getNoradCatId, satellite -> satellite));

            Set<String> existingEpochKeys = new HashSet<>();
            Set<String> foundEpochKeys = orbitalParametersRepository.findEpochKeysByNoradCatIdIn(noradIds);
            if (foundEpochKeys != null) {
                existingEpochKeys.addAll(foundEpochKeys);
            }

            Map<Long, Satellite> newSatellitesByNorad = new HashMap<>();
            List<OrbitalParameters> pendingParameters = new ArrayList<>();
            LocalDateTime fetchedAt = LocalDateTime.now(ZoneOffset.UTC);

            for (JsonNode node : root) {
                try {
                    if (node.hasNonNull("error")) {
                        errors++;
                        if (errors <= 10) {
                            log.warn("⚠️ Space-Track error response: {}", node.path("error").asText());
                        }
                        continue;
                    }

                    ParsedSatelliteRow row = parseRow(node, leoOnly);
                    if (row == null) {
                        skipped++;
                        continue;
                    }

                    String epochKey = row.norad + "|" + row.epoch;
                    if (existingEpochKeys.contains(epochKey)) {
                        skipped++;
                        if (skipped <= 10) {
                            log.debug("↩️ Parametri già presenti per NORAD {} epoch {}", row.norad, row.epoch);
                        }
                        continue;
                    }

                    Satellite sat = satellitesByNorad.get(row.norad);
                    boolean newSatellite = false;
                    if (sat == null) {
                        sat = newSatellitesByNorad.computeIfAbsent(row.norad, ignored -> {
                            Satellite newSatelliteEntity = new Satellite();
                            newSatelliteEntity.setNoradCatId(row.norad);
                            newSatelliteEntity.setObjectName(row.objectName.isBlank() ? "UNKNOWN" : row.objectName);
                            newSatelliteEntity.setObjectId(row.objectId);
                            newSatelliteEntity.setSatelliteType("UNKNOWN");
                            return newSatelliteEntity;
                        });
                        newSatellite = true;
                    }

                    String inferred = row.meanMotion > leoThreshold ? "LEO" : (row.meanMotion >= MEO_THRESHOLD ? "MEO" : "GEO");
                    String effectiveSatelliteType = sat.getSatelliteType();
                    if (effectiveSatelliteType == null || effectiveSatelliteType.isBlank() || "UNKNOWN".equalsIgnoreCase(effectiveSatelliteType)) {
                        effectiveSatelliteType = inferred;
                    }
                    sat.setObjectTypeRaw(row.rawType);
                    sat.setObjectTypeInferred(inferred);
                    sat.setSatelliteType(effectiveSatelliteType);

                    OrbitalParameters p = new OrbitalParameters(
                            sat,
                            row.epoch,
                            row.inclination,
                            row.raOfAscNode,
                            row.eccentricity,
                            row.argOfPericenter,
                            row.meanAnomaly,
                            row.meanMotion);
                    p.setTleLine1(row.tleLine1);
                    p.setTleLine2(row.tleLine2);
                    p.setFetchedAt(fetchedAt);

                    pendingParameters.add(p);
                    existingEpochKeys.add(epochKey);
                    count++;

                    if (newSatellite) {
                        created++;
                    }
                } catch (Exception e) {
                    errors++;
                    if (errors <= 10) {
                        log.warn("⚠️ Errore processing satellite: {}", e.getMessage());
                    }
                }
            }

            if (!newSatellitesByNorad.isEmpty()) {
                List<Satellite> savedSatellites = satelliteRepository.saveAll(new ArrayList<>(newSatellitesByNorad.values()));
                Map<Long, Satellite> persistedByNorad = savedSatellites.stream()
                        .collect(Collectors.toMap(Satellite::getNoradCatId, satellite -> satellite));

                for (Satellite satellite : savedSatellites) {
                    satelliteAuditFileService.appendNewSatellite(
                            "spacetrack",
                            LocalDateTime.now(ZoneOffset.UTC),
                            String.format(
                                    "norad=%s | objectName=%s | objectId=%s | satelliteType=%s | objectTypeRaw=%s | objectTypeInferred=%s",
                                    satellite.getNoradCatId(),
                                    satellite.getObjectName(),
                                    satellite.getObjectId(),
                                    satellite.getSatelliteType(),
                                    satellite.getObjectTypeRaw(),
                                    satellite.getObjectTypeInferred()
                            )
                    );
                }

                for (OrbitalParameters parameters : pendingParameters) {
                    Satellite persistedSatellite = persistedByNorad.get(parameters.getSatellite().getNoradCatId());
                    if (persistedSatellite != null) {
                        parameters.setSatellite(persistedSatellite);
                    }
                }
            }

            if (!pendingParameters.isEmpty()) {
                orbitalParametersRepository.saveAll(pendingParameters);
            }
        } catch (Exception e) {
            log.error("❌ Errore parsing JSON: {}", e.getMessage(), e);
            errors++;
        }

        log.info("═══════════════════════════════════════════════════════════");
        log.info("✅ PARSING JSON COMPLETATO");
        log.info("   Salvati:          {}", count);
        log.info("   Nuovi satelliti:  {}", created);
        log.info("   Saltati (filtrati/duplicati):  {}", skipped);
        log.info("   Errori:           {}", errors);
        log.info("═══════════════════════════════════════════════════════════");

        return new ParseResult(count, skipped, created, errors);
    }

    private ParsedSatelliteRow parseRow(JsonNode node, boolean leoOnly) {
        long norad = readRequiredLong(node, "NORAD_CAT_ID");
        String epoch = readRequiredText(node, "EPOCH");
        double meanMotion = readRequiredDouble(node, "MEAN_MOTION");

        if (leoOnly && meanMotion < leoThreshold) {
            return null;
        }

        return new ParsedSatelliteRow(
                norad,
                node.path("OBJECT_NAME").asText(""),
                node.path("OBJECT_ID").asText(""),
                epoch,
                readRequiredDouble(node, "INCLINATION"),
                readRequiredDouble(node, "RA_OF_ASC_NODE"),
                readRequiredDouble(node, "ECCENTRICITY"),
                readRequiredDouble(node, "ARG_OF_PERICENTER"),
                readRequiredDouble(node, "MEAN_ANOMALY"),
                meanMotion,
                node.path("TLE_LINE1").asText(""),
                node.path("TLE_LINE2").asText(""),
                node.path("OBJECT_TYPE").asText(null)
        );
    }

    private long readRequiredLong(JsonNode node, String fieldName) {
        Long parsed = readOptionalLong(node, fieldName);
        if (parsed == null) {
            throw new IllegalArgumentException("Campo numerico mancante o non valido: " + fieldName);
        }
        return parsed;
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
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private double readRequiredDouble(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            throw new IllegalArgumentException("Campo mancante: " + fieldName);
        }
        if (field.isNumber()) {
            return field.asDouble();
        }
        if (field.isTextual()) {
            String text = field.asText("").trim();
            if (!text.isBlank()) {
                try {
                    return Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Campo non parsabile come double: " + fieldName + " = " + text);
                }
            }
        }
        throw new IllegalArgumentException("Campo numerico mancante o non valido: " + fieldName);
    }

    private String readRequiredText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            throw new IllegalArgumentException("Campo mancante: " + fieldName);
        }
        String value = field.asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Campo vuoto: " + fieldName);
        }
        return value;
    }

}
    
