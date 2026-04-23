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
            OrbitalParametersRepository orbitalParametersRepository
    ) {
        this.spaceTrackService = spaceTrackService;
        this.celestrakService = celestrakService;
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    public void updateTle() {

        log.info("╔═══════════════════════════════════════════════════════════╗");
        log.info("║  🚀 AGGIORNAMENTO TLE INIZIATO                            ║");
        log.info("╚═══════════════════════════════════════════════════════════╝");
        log.info("⚙️  Sorgente primaria configurata: {}", primarySource);

        try {
            if (!"spacetrack".equalsIgnoreCase(primarySource)) {
                log.info("🎯 Usando CELESTRAK come sorgente primaria (da configurazione)...");
                celestrakService.fetchAndSaveStations();
                log.info("╔═══════════════════════════════════════════════════════════╗");
                log.info("║  ✅ AGGIORNAMENTO TLE COMPLETATO CON SUCCESSO            ║");
                log.info("╚═══════════════════════════════════════════════════════════╝");
                return;
            }

            log.info("🎯 Tentativo download da SPACE-TRACK (sorgente primaria)...");
            boolean ok = fetchFromSpaceTrack();

            if (!ok) {
                log.warn("╔═══════════════════════════════════════════════════════════╗");
                log.warn("║  ⚠️  SPACE-TRACK FALLITO - ATTIVO FALLBACK               ║");
                log.warn("╚═══════════════════════════════════════════════════════════╝");
                log.info("🔄 Passaggio a CELESTRAK come sorgente di backup...");
                celestrakService.fetchAndSaveStations();
                log.info("✅ Fallback CelesTrak completato");
            }

            log.info("╔═══════════════════════════════════════════════════════════╗");
            log.info("║  ✅ AGGIORNAMENTO TLE COMPLETATO CON SUCCESSO            ║");
            log.info("╚═══════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            log.error("╔═══════════════════════════════════════════════════════════╗");
            log.error("║  ❌ ERRORE CRITICO AGGIORNAMENTO TLE                     ║");
            log.error("╚═══════════════════════════════════════════════════════════╝");
            log.error("   Tipo errore: {}", e.getClass().getSimpleName());
            log.error("   Messaggio: {}", e.getMessage());
            log.error("🔄 Tentativo fallback d'emergenza a CelesTrak...", e);
            
            try {
                celestrakService.fetchAndSaveStations();
                log.info("✅ Fallback d'emergenza CelesTrak riuscito");
            } catch (Exception fallbackError) {
                log.error("❌ Anche il fallback CelesTrak è fallito: {}", fallbackError.getMessage(), fallbackError);
            }
        }
    }

    private boolean fetchFromSpaceTrack() {

        log.info("───────────────────────────────────────────────────────────");
        log.info("📡 Inizio fetch DELTA da Space-Track");
        log.info("───────────────────────────────────────────────────────────");

        try {
            log.info("🔍 Ricerca ultimo epoch nel database...");
            
            OrbitalParameters last = orbitalParametersRepository
                    .findTopByOrderByFetchedAtDesc();

            String lastEpoch = (last != null) ? last.getEpoch() : null;

            if (lastEpoch == null) {
                log.warn("⚠️  Nessun epoch trovato nel database");
                log.warn("   Impossibile effettuare fetch delta (richiede epoch di riferimento)");
                log.warn("   Usa prima CelesTrak per popolare il database");
                return false;
            }

            log.info("✅ Ultimo epoch trovato: {}", lastEpoch);
            log.info("   Timestamp fetch: {}", last.getFetchedAt());
            log.info("🔄 Richiesta delta TLE da Space-Track (epoch > {})...", lastEpoch);

            String tleData = spaceTrackService.downloadDeltaTle(lastEpoch);

            if (tleData == null) {
                log.warn("❌   Space-Track ha restituito dati NULL o vuoti");
                log.warn("   Possibili cause:");
                log.warn("   - Timeout");
                log.warn("   - Errore di connessione/autenticazione");
                log.warn("   - Rate limit raggiunto");
                return false;
            }

            if (tleData.isBlank()) {
                log.info("ℹ️ Nessun aggiornamento TLE disponibile → database già aggiornato");

                log.info("───────────────────────────────────────────────────────────");
                    log.info("✅ SPACE-TRACK: NESSUN AGGIORNAMENTO NECESSARIO");
                log.info("───────────────────────────────────────────────────────────");

                return true; // ✅ IMPORTANTE: NON fallback, dati già aggiornati 
            }

            log.info("✅ Dati delta ricevuti da Space-Track: {} bytes", tleData.length());
            log.info("🔄 Inizio parsing e salvataggio nel database...");
            
            long startParse = System.currentTimeMillis();
            int saved = parseAndSave(tleData);
            long parseDuration = System.currentTimeMillis() - startParse;

            if (saved == 0) {
                log.warn("⚠️  Parsing completato ma nessun satellite salvato");
                log.warn("   Possibili cause:");
                log.warn("   - Satelliti nel TLE non presenti nel database");
                log.warn("   - Formato TLE non valido");
                return false;
            }

            log.info("───────────────────────────────────────────────────────────");
            log.info("✅ SPACE-TRACK DELTA IMPORT COMPLETATO");
            log.info("   Satelliti aggiornati: {}", saved);
            log.info("   Tempo di parsing: {}ms", parseDuration);
            log.info("───────────────────────────────────────────────────────────");

            return true;

        } catch (Exception e) {
            log.error("───────────────────────────────────────────────────────────");
            log.error("❌ Errore durante fetch delta da Space-Track");
            log.error("   Tipo: {}", e.getClass().getSimpleName());
            log.error("   Messaggio: {}", e.getMessage());
            log.error("───────────────────────────────────────────────────────────", e);
            return false;
        }
    }

    private int parseAndSave(String tleData) {

        log.info("🔍 Inizio parsing TLE...");
        String[] lines = tleData.split("\n");
        int count = 0;
        int skipped = 0;
        int errors = 0;

        log.info("   Totale righe da processare: {}", lines.length);
        log.info("   TLE attesi: ~{}", lines.length / 2);

        for (int i = 0; i + 2 < lines.length; i += 3) {

            try {
                String line1 = lines[i + 1].trim();
                String line2 = lines[i + 2].trim();

                if (line1.isEmpty() || line2.isEmpty()) {
                    skipped++;
                    continue;
                }

                Long norad = Long.parseLong(line1.substring(2, 7).trim());

                Optional<Satellite> satOpt =
                        satelliteRepository.findByNoradCatId(norad);

                if (satOpt.isEmpty()) {
                    skipped++;
                    if (skipped % 1000 == 0) {
                        log.debug("   Saltati {} satelliti non in database", skipped);
                    }
                    continue;
                }

                Satellite sat = satOpt.get();

                String epoch = line1.substring(18, 32).trim();

                Double inclination = parse(line2, 8, 16);
                Double raOfAscNode = parse(line2, 17, 25);
                Double eccentricity = parseEcc(line2, 26, 33);
                Double argOfPericenter = parse(line2, 34, 42);
                Double meanAnomaly = parse(line2, 43, 51);
                Double meanMotion = parse(line2, 52, 63);

                OrbitalParameters p = new OrbitalParameters(
                        sat,
                        epoch,
                        inclination,
                        raOfAscNode,
                        eccentricity,
                        argOfPericenter,
                        meanAnomaly,
                        meanMotion
                );

                p.setTleLine1(line1);
                p.setTleLine2(line2);
                p.setFetchedAt(LocalDateTime.now());

                orbitalParametersRepository.save(p);

                count++;
                
                if (count % 1000 == 0) {
                    log.info("   Progresso: {} satelliti salvati...", count);
                }

            } catch (Exception e) {
                errors++;
                if (errors < 10) { // Log solo i primi 10 errori
                    log.warn("⚠️  Errore parsing TLE al blocco {} (riga {}): {}", 
                            i / 3, i, e.getMessage());
                }
            }
        }

        log.info("═══════════════════════════════════════════════════════════");
        log.info("✅ PARSING COMPLETATO");
        log.info("   Satelliti salvati: {}", count);
        log.info("   Satelliti saltati (non in DB): {}", skipped);
        log.info("   Errori di parsing: {}", errors);
        log.info("═══════════════════════════════════════════════════════════");

        return count;
    }

    private Double parse(String l, int a, int b) {
        try { return Double.parseDouble(l.substring(a, b).trim()); }
        catch (Exception e) { return null; }
    }

    private Double parseEcc(String l, int a, int b) {
        try { return Double.parseDouble("0." + l.substring(a, b).trim()); }
        catch (Exception e) { return null; }
    }
}