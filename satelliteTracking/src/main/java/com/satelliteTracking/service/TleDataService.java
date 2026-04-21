package com.satelliteTracking.service;

import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Servizio unificato per il download dei TLE.
 * Utilizza Space-Track come primario e CelesTrak come fallback.
 */
@Service
public class TleDataService {
    private static final Logger log = LoggerFactory.getLogger(TleDataService.class);

    private final SpaceTrackService spaceTrackService;
    private final CelestrakService celestrakService;
    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;

    @Value("${tle.source.primary:spacetrack}")
    private String primarySource;

    @Value("${tle.batch.size:100}")
    private int batchSize;

    // Soglia di aggiornamento TLE (es. 12 ore)
    @Value("${tle.cache.ttl.hours:12}")
    private int tleCacheTtlHours = 12;

    public TleDataService(
            SpaceTrackService spaceTrackService,
            CelestrakService celestrakService,
            SatelliteRepository satelliteRepository,
            OrbitalParametersRepository orbitalParametersRepository
    ) {
        this.spaceTrackService = spaceTrackService;
        this.celestrakService = celestrakService;
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    public boolean isTleCacheExpired() {
        OrbitalParameters latest = orbitalParametersRepository.findTopByOrderByFetchedAtDesc();
        if (latest == null) return true;
        return latest.getFetchedAt().isBefore(java.time.LocalDateTime.now().minusHours(tleCacheTtlHours));
    }

    /**
     * Scarica e salva i TLE per le stazioni spaziali (ISS, Tiangong, ecc.)
     * Usa Space-Track come primario, CelesTrak come fallback
     */
    public void fetchAndSaveStations() {
        log.info("🛰️  Inizio download TLE stazioni spaziali...");

        if (!isTleCacheExpired()) {
            log.info("⏳ TLE già aggiornati di recente, nessun download eseguito.");
            return;
        }

        try {
            // Prova prima con Space-Track
            if ("spacetrack".equalsIgnoreCase(primarySource)) {
                log.info("📡 Tentativo download da Space-Track (primario)...");
                boolean success = fetchFromSpaceTrack("stations");
                
                if (success) {
                    log.info("✅ Download completato da Space-Track");
                    return;
                }
                
                log.warn("⚠️  Space-Track fallito, fallback su CelesTrak...");
            }
            
            // Fallback su CelesTrak
            log.info("📡 Download da CelesTrak...");
            celestrakService.fetchAndSaveStations();
            log.info("✅ Download completato da CelesTrak");
            
        } catch (Exception e) {
            log.error("❌ Errore critico nel download TLE: {}", e.getMessage(), e);
            throw new RuntimeException("Impossibile scaricare TLE da nessuna fonte", e);
        }
    }


    /**
     * Download da Space-Track per un gruppo specifico
     */
    private boolean fetchFromSpaceTrack(String group) {
        try {
            // Trova satelliti del gruppo nel database
            List<Satellite> satellites = satelliteRepository.findAll().stream()
                    .filter(s -> group.equalsIgnoreCase(s.getSatelliteType()))
                    .toList();

            if (satellites.isEmpty()) {
                log.warn("Nessun satellite trovato per gruppo: {}", group);
                return false;
            }

            return fetchSatellitesFromSpaceTrack(satellites);
            
        } catch (Exception e) {
            log.error("Errore download Space-Track gruppo {}: {}", group, e.getMessage());
            return false;
        }
    }

    /**
     * Download da Space-Track per tutti i satelliti
     */
    private boolean fetchAllFromSpaceTrack(List<Satellite> satellites) {
        try {
            return fetchSatellitesFromSpaceTrack(satellites);
        } catch (Exception e) {
            log.error("Errore download Space-Track: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Scarica TLE da Space-Track in batch
     */
    private boolean fetchSatellitesFromSpaceTrack(List<Satellite> satellites) {

    List<List<Long>> batches = createBatches(satellites, batchSize);

    int totalUpdated = 0;
    int totalErrors = 0;

    for (int i = 0; i < batches.size(); i++) {
        List<Long> batch = batches.get(i);

        log.debug("Processing batch {}/{}: {} satelliti",
                i + 1, batches.size(), batch.size());

        for (Long noradId : batch) {
            try {
                String tleData = spaceTrackService
                        .downloadTleByNoradId(noradId)
                        .block();

                if (tleData != null && !tleData.isEmpty()) {
                    int updated = parseTleAndSave(tleData);
                    totalUpdated += updated;

                    log.debug("Satellite {} aggiornato ({} record)", noradId, updated);
                } else {
                    log.warn("Nessun TLE ricevuto per satellite {}", noradId);
                    totalErrors++;
                }

                // rate limit leggero Space-Track
                Thread.sleep(300);

            } catch (Exception e) {
                log.error("Errore satellite {}: {}", noradId, e.getMessage());
                totalErrors++;
            }
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Batch interrotto");
        }
    }

    log.info("Space-Track completato: {} aggiornati, {} errori",
            totalUpdated, totalErrors);

    return totalUpdated > 0;
}

    /**
     * Fallback su CelesTrak per tutti i gruppi
     */
    private void fetchAllFromCelestrak() {
        // Usa i metodi esistenti di CelestrakService
        celestrakService.fetchAndSaveStations();
        
    }

    /**
     * Parsa TLE formato 3-line e salva nel database
     */
    private int parseTleAndSave(String tleData) {
        if (tleData == null || tleData.trim().isEmpty()) {
            return 0;
        }

        String[] lines = tleData.split("\n");
        int count = 0;

        for (int i = 0; i < lines.length; i += 3) {
            if (i + 2 >= lines.length) break;

            try {
                String name = lines[i].trim();
                String line1 = lines[i + 1].trim();
                String line2 = lines[i + 2].trim();

                // Estrai NORAD ID dalla line1 (colonne 3-7)
                String noradIdStr = line1.substring(2, 7).trim();
                Long noradCatId = Long.parseLong(noradIdStr);

                // Trova satellite nel DB
                Optional<Satellite> satOpt = satelliteRepository.findByNoradCatId(noradCatId);
                if (satOpt.isEmpty()) {
                    // Satellite non nel nostro tracking, skip
                    continue;
                }

                Satellite satellite = satOpt.get();

                // Crea nuovo record parametri orbitali
                OrbitalParameters params = new OrbitalParameters();
                params.setSatellite(satellite);

                params.setTleLine1(line1);
                params.setTleLine2(line2);
                params.setFetchedAt(LocalDateTime.now());


                // Estrai epoch
                String epochStr = line1.substring(18, 32).trim();
                params.setEpoch(epochStr); // Salva come stringa coerente con OrbitalParameters


                // Estrai parametri orbitali da line2
                params.setInclination(parseDouble(line2, 8, 16));
                params.setRaan(parseDouble(line2, 17, 25));
                params.setEccentricity(parseEccentricity(line2, 26, 33));
                params.setArgumentOfPerigee(parseDouble(line2, 34, 42));
                params.setMeanAnomaly(parseDouble(line2, 43, 51));
                params.setMeanMotion(parseDouble(line2, 52, 63));

                orbitalParametersRepository.save(params);
                count++;

            } catch (Exception e) {
                log.error("Errore parsing TLE linea {}: {}", i, e.getMessage());
            }
        }

        return count;
    }

    /**
     * Divide lista in batch
     */
    private List<List<Long>> createBatches(List<Satellite> satellites, int batchSize) {
        List<List<Long>> batches = new ArrayList<>();
        List<Long> currentBatch = new ArrayList<>();

        for (Satellite sat : satellites) {
            currentBatch.add(sat.getNoradCatId());
            if (currentBatch.size() >= batchSize) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    /**
     * Parsa epoch dal formato TLE
     */
    private LocalDateTime parseEpoch(String epochStr) {
        try {
            int year = Integer.parseInt(epochStr.substring(0, 2));
            year += (year < 57) ? 2000 : 1900;

            double dayOfYear = Double.parseDouble(epochStr.substring(2));
            
            return LocalDateTime.of(year, 1, 1, 0, 0)
                    .plusDays((long) dayOfYear - 1);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private Double parseDouble(String line, int start, int end) {
        try {
            return Double.parseDouble(line.substring(start, end).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseEccentricity(String line, int start, int end) {
        try {
            String eccStr = "0." + line.substring(start, end).trim();
            return Double.parseDouble(eccStr);
        } catch (Exception e) {
            return null;
        }
    }
}