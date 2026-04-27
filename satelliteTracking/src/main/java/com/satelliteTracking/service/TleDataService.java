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
    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;

    @Value("${tle.source.primary:spacetrack}")
    private String primarySource;

    public TleDataService(
            SpaceTrackService spaceTrackService,
            CelestrakService celestrakService,
            SatelliteRepository satelliteRepository,
            OrbitalParametersRepository orbitalParametersRepository) {
        this.spaceTrackService = spaceTrackService;
        this.celestrakService = celestrakService;
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
                OrbitalParameters last = orbitalParametersRepository.findTopByOrderByFetchedAtDesc();
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
            } else {
                log.info("✅ AGGIORNAMENTO TLE LEO COMPLETATO");
            }
        } catch (Exception e) {
            log.error("❌ Errore durante aggiornamento LEO: {}", e.getMessage(), e);
        }
    }

    private boolean fetchFromSpaceTrack() {
        log.info("───────────────────────────────────────────────────────────");
        log.info("📡 Fetch DELTA da Space-Track (tutti i satelliti)");
        log.info("   💡 Filtro su CREATION_DATE (non EPOCH) per delta corretti");
        log.info("───────────────────────────────────────────────────────────");

        try {
            OrbitalParameters last = orbitalParametersRepository.findTopByOrderByFetchedAtDesc();

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

            // ⚡ FIX: passa LocalDateTime, non String epoch
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
            int saved = parseAndSaveJson(tleData);
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
            OrbitalParameters last = orbitalParametersRepository.findTopByOrderByFetchedAtDesc();

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

            long startParse = System.currentTimeMillis();
            int saved = parseAndSaveJson(tleData);
            long parseDuration = System.currentTimeMillis() - startParse;

            log.info("✅ SPACE-TRACK DELTA LEO COMPLETATO");
            log.info("   Satelliti LEO aggiornati: {}", saved);
            log.info("   Tempo parsing: {} ms", parseDuration);
            return true;

        } catch (Exception e) {
            log.error("❌ Errore fetch delta LEO: {}", e.getMessage(), e);
            return false;
        }
    }

    int parseAndSaveJson(String jsonData) {
        log.info("🔍 Inizio parsing TLE (JSON)...");
        int count = 0;
        int skipped = 0;
        int errors = 0;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonData);
            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    try {
                        Long norad = node.path("NORAD_CAT_ID").asLong();
                        java.util.Optional<Satellite> satOpt = satelliteRepository.findByNoradCatId(norad);
                        if (satOpt.isEmpty()) {
                            skipped++;
                            continue;
                        }
                        Satellite sat = satOpt.get();
                        String epoch = node.path("EPOCH").asText();
                        Double inclination = node.path("INCLINATION").asDouble();
                        Double raOfAscNode = node.path("RA_OF_ASC_NODE").asDouble();
                        Double eccentricity = node.path("ECCENTRICITY").asDouble();
                        Double argOfPericenter = node.path("ARG_OF_PERICENTER").asDouble();
                        Double meanAnomaly = node.path("MEAN_ANOMALY").asDouble();
                        Double meanMotion = node.path("MEAN_MOTION").asDouble();
                        String tleLine1 = node.path("TLE_LINE1").asText("");
                        String tleLine2 = node.path("TLE_LINE2").asText("");
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
                            log.warn("⚠️ Errore JSON: {}", e.getMessage());
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
        log.info("   Saltati (no DB):  {}", skipped);
        log.info("   Errori:           {}", errors);
        log.info("═══════════════════════════════════════════════════════════");
        return count;
    }


        catch (Exception e) { return null; }
    }
}