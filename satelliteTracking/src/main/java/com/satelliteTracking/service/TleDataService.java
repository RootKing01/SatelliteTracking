package com.satelliteTracking.service;

import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TleDataService {

    private static final Logger log = LoggerFactory.getLogger(TleDataService.class);

    private final SpaceTrackService spaceTrackService;
    private final CelestrakService celestrakService;
    private final SatelliteAuditFileService satelliteAuditFileService;
    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;

    @Value("${tle.source.primary:spacetrack}")
    private String primarySource;

    public TleDataService(
            SpaceTrackService spaceTrackService,
            CelestrakService celestrakService,
            SatelliteAuditFileService satelliteAuditFileService,
            SatelliteRepository satelliteRepository,
            OrbitalParametersRepository orbitalParametersRepository) {
        this.spaceTrackService = spaceTrackService;
        this.celestrakService = celestrakService;
        this.satelliteAuditFileService = satelliteAuditFileService;
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

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
            boolean ok = fetchFromSpaceTrack();

            // Se fetchFromSpaceTrack non ha già gestito il fallback, esegui qui solo se necessario
            if (!ok) {
                OrbitalParameters last = orbitalParametersRepository
                        .findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L);
                long hoursSinceLast = 0;
                if (last != null) {
                    hoursSinceLast = java.time.Duration.between(last.getFetchedAt(), java.time.LocalDateTime.now()).toHours();
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

    public void updateTleLeoOnly() {
        log.info("╔═══════════════════════════════════════════════════════════╗");
        log.info("║  🛰️  AGGIORNAMENTO TLE LEO INIZIATO                      ║");
        log.info("╚═══════════════════════════════════════════════════════════╝");

        try {
            if (!"spacetrack".equalsIgnoreCase(primarySource)) {
                log.info("⚠️ Aggiornamento LEO disponibile solo con Space-Track, skip");
                return;
            }
            boolean ok = fetchLeoFromSpaceTrack();
            if (!ok) {
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

    private boolean fetchFromSpaceTrack() {
        log.info("───────────────────────────────────────────────────────────");
        log.info("📡 Fetch DELTA da Space-Track (tutti i satelliti)");
        log.info("   💡 Filtro su CREATION_DATE via REST path per delta corretti");
        log.info("───────────────────────────────────────────────────────────");

        try {
            OrbitalParameters last = orbitalParametersRepository
                    .findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L);

            if (last == null) {
                log.warn("⚠️ Database vuoto, impossibile fare delta - usa CelesTrak prima");
                return false;
            }

            LocalDateTime lastFetchedAt = last.getFetchedAt();
            log.info("✅ Ultimo fetch: {}", lastFetchedAt);
            log.info("🔄 Richiesta delta TLE con CREATION_DATE > {}...", lastFetchedAt);

            // Se sono passate più di 48 ore dall'ultimo aggiornamento, forza fallback
            long hoursSinceLast = java.time.Duration.between(lastFetchedAt, LocalDateTime.now()).toHours();
            if (hoursSinceLast > 48) {
                log.warn("⏳ Sono passate {} ore dall'ultimo aggiornamento: fallback obbligatorio a CelesTrak", hoursSinceLast);
                celestrakService.fetchAndSaveStations();
                log.info("✅ Fallback CelesTrak forzato dopo {} ore", hoursSinceLast);
                return true; // Interrompi qui, non chiamare fallback due volte
            }

            // ⚡ FIX: passa il timestamp diretto, non una stringa di query
            String tleData = spaceTrackService.downloadDeltaTle(lastFetchedAt);
            log.debug("[DEBUG] Risposta grezza Space-Track: {}", tleData);
            if (tleData == null) {
                log.warn("❌ Space-Track ha restituito null");
                return false;
            }

            if (tleData.isBlank()) {
                log.info("ℹ️ Nessun aggiornamento TLE disponibile → database già aggiornato");
                log.info("✅ SPACE-TRACK: NESSUN AGGIORNAMENTO NECESSARIO");
                return true;
            }

            log.info("✅ Dati delta ricevuti: {} bytes", tleData.length());

            long startParse = System.currentTimeMillis();
            int saved = parseAndSaveJson(tleData, true);
            long parseDuration = System.currentTimeMillis() - startParse;

            if (saved == 0) {
                log.warn("⚠️ Parsing completato ma nessun satellite salvato");
                log.warn("   Possibili cause: satelliti non in DB, formato TLE errato");
                // Debug: mostra le prime righe ricevute
                String preview = tleData.substring(0, Math.min(500, tleData.length()));
                log.warn("   Preview dati ricevuti:\n{}", preview);
                return false;
            }

            log.info("✅ SPACE-TRACK DELTA IMPORT COMPLETATO");
            log.info("   Satelliti aggiornati: {}", saved);
            log.info("   Tempo parsing: {} ms", parseDuration);
            return true;

        } catch (Exception e) {
            log.error("❌ Errore fetch delta: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return false;
        }
    }

    private boolean fetchLeoFromSpaceTrack() {
        log.info("───────────────────────────────────────────────────────────");
        log.info("📡 Fetch DELTA LEO da Space-Track");
        log.info("───────────────────────────────────────────────────────────");

        try {
            OrbitalParameters last = orbitalParametersRepository
                    .findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L);

            if (last == null) {
                log.warn("⚠️ Database vuoto, skip LEO update");
                return false;
            }

            LocalDateTime lastFetchedAt = last.getFetchedAt();
            log.info("✅ Ultimo fetch: {}", lastFetchedAt);

            String tleData = spaceTrackService.downloadDeltaTleLeoOnly(lastFetchedAt);

            if (tleData == null) {
                log.warn("❌ Space-Track LEO ha restituito null");
                return false;
            }

            if (tleData.isBlank()) {
                log.info("ℹ️ Nessun aggiornamento TLE LEO disponibile");
                log.info("✅ SPACE-TRACK LEO: NESSUN AGGIORNAMENTO NECESSARIO");
                return true;
            }

            log.info("✅ Dati delta LEO ricevuti: {} bytes", tleData.length());
            
            // 🔍 DEBUG: mostra le prime 2000 char del JSON per vedere se OBJECT_NAME è presente
            if (tleData.length() < 2000) {
                log.info("🔍 [DEBUG] JSON completo:\n{}", tleData);
            } else {
                log.info("🔍 [DEBUG] JSON preview (primi 2000 char):\n{}", tleData.substring(0, 2000));
            }

            long startParse = System.currentTimeMillis();
            int saved = parseAndSaveJson(tleData, true);
            long parseDuration = System.currentTimeMillis() - startParse;

            if (saved == 0) {
                log.warn("⚠️ Nessun satellite LEO salvato dal delta Space-Track");
                log.warn("   Potrebbe essere un problema di dati incompleti, mapping o database non allineato");
                return false;
            }

            log.info("✅ SPACE-TRACK DELTA LEO COMPLETATO");
            log.info("   Satelliti LEO aggiornati: {}", saved);
            log.info("   Tempo parsing: {} ms", parseDuration);
            return true;

        } catch (Exception e) {
            log.error("❌ Errore fetch delta LEO: {}", e.getMessage(), e);
            return false;
        }
    }

   // In TleDataService.java

int parseAndSaveJson(String jsonData) {
    return parseAndSaveJson(jsonData, false);
}

int parseAndSaveJson(String jsonData, boolean leoOnly) {
    log.info("🔍 Inizio parsing TLE (JSON)...");
    int count = 0;
    int skipped = 0;
    int created = 0; // ✅ NUOVO: conta satelliti creati
    int errors = 0;
    
    try {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonData);
        
        if (root.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                try {
                    if (node.has("error")) {
                        skipped++;
                        if (skipped <= 10) {
                            log.warn("⚠️ Space-Track error response: {}", node.path("error").asText());
                        }
                        continue;
                    }

                    Long norad = node.path("NORAD_CAT_ID").asLong();
                    String objectName = node.path("OBJECT_NAME").asText("");

                    if (norad <= 0) {
                        skipped++;
                        if (skipped <= 10) {
                            log.warn("⚠️ Record saltato: NORAD_CAT_ID non valido");
                        }
                        continue;
                    }

                    Double meanMotion = node.path("MEAN_MOTION").asDouble();

                    if (leoOnly && meanMotion <= 11.25) {
                        skipped++;
                        continue;
                    }
                    
                    // 🔍 DEBUG: stampa i valori estratti
                    log.debug("🔍 [DEBUG] Parsing satellite: NORAD={}, OBJECT_NAME='{}'", 
                        norad, objectName);
                    
                    // ✅ MODIFICA: Cerca o crea il satellite
                    java.util.Optional<Satellite> satOpt = satelliteRepository.findByNoradCatId(norad);
                    Satellite sat;
                    
                    if (satOpt.isEmpty()) {
                        // ✅ NUOVO: Crea il satellite se non esiste
                        sat = new Satellite();
                        sat.setNoradCatId(norad);
                        sat.setObjectName(objectName.isBlank() ? "UNKNOWN" : objectName);
                        sat.setObjectId(node.path("OBJECT_ID").asText(""));
                        
                        // Determina il tipo in base al mean motion
                        if (meanMotion > 11.25) {
                            sat.setSatelliteType("LEO");
                        } else if (meanMotion > 1.0) {
                            sat.setSatelliteType("MEO");
                        } else {
                            sat.setSatelliteType("GEO");
                        }
                        
                        sat = satelliteRepository.save(sat);
                        created++;
                        log.info("✨ Nuovo satellite creato: {} (NORAD {})", sat.getObjectName(), norad);
                                    satelliteAuditFileService.appendNewSatellite(
                                        "spacetrack",
                                        java.time.LocalDateTime.now(java.time.ZoneOffset.UTC),
                                        String.format(
                                            "norad=%s | objectName=%s | objectId=%s | satelliteType=%s | objectTypeRaw=%s | objectTypeInferred=%s | epoch=%s",
                                            sat.getNoradCatId(),
                                            sat.getObjectName(),
                                            sat.getObjectId(),
                                            sat.getSatelliteType(),
                                            sat.getObjectTypeRaw(),
                                            sat.getObjectTypeInferred(),
                                            node.path("EPOCH").asText("")
                                        )
                                    );
                    } else {
                        sat = satOpt.get();
                    }
                    
                    // Salva i parametri orbitali
                    String epoch = node.path("EPOCH").asText();
                    Double inclination = node.path("INCLINATION").asDouble();
                    Double raOfAscNode = node.path("RA_OF_ASC_NODE").asDouble();
                    Double eccentricity = node.path("ECCENTRICITY").asDouble();
                    Double argOfPericenter = node.path("ARG_OF_PERICENTER").asDouble();
                    Double meanAnomaly = node.path("MEAN_ANOMALY").asDouble();
                    String tleLine1 = node.path("TLE_LINE1").asText("");
                    String tleLine2 = node.path("TLE_LINE2").asText("");

                    if (orbitalParametersRepository.existsBySatelliteAndEpoch(sat, epoch)) {
                        skipped++;
                        if (skipped <= 10) {
                            log.debug("↩️ Parametri già presenti per NORAD {} epoch {}", norad, epoch);
                        }
                        continue;
                    }
                    
                        // set object type raw and inferred
                        String rawType = node.path("OBJECT_TYPE").asText(null);
                        sat.setObjectTypeRaw(rawType);
                        String inferred = (meanMotion > 11.25) ? "LEO" : (meanMotion > 1.0 ? "MEO" : "GEO");
                        sat.setObjectTypeInferred(inferred);

                        sat = satelliteRepository.save(sat);

                        OrbitalParameters p = new OrbitalParameters(
                            sat, epoch, inclination, raOfAscNode,
                            eccentricity, argOfPericenter, meanAnomaly, meanMotion);
                    p.setTleLine1(tleLine1);
                    p.setTleLine2(tleLine2);
                    p.setFetchedAt(java.time.LocalDateTime.now());
                    
                    orbitalParametersRepository.save(p);
                    count++;
                    
                } catch (Exception e) {
                    errors++;
                    if (errors <= 10) {
                        log.warn("⚠️ Errore processing satellite: {}", e.getMessage());
                    }
                }
            }
        }
    } catch (Exception e) {
        log.error("❌ Errore parsing JSON: {}", e.getMessage(), e);
    }
    
    log.info("═══════════════════════════════════════════════════════════");
    log.info("✅ PARSING JSON COMPLETATO");
    log.info("   Salvati:          {}", count);
    log.info("   Nuovi satelliti:  {}", created); // ✅ NUOVO
    log.info("   Saltati (no DB):  {}", skipped);
    log.info("   Errori:           {}", errors);
    log.info("═══════════════════════════════════════════════════════════");
    
    return count;
}


    private Double parse(String l, int a, int b) {
        try { return Double.parseDouble(l.substring(a, b).trim()); }
        catch (Exception e) { return null; }
    }

}
    
