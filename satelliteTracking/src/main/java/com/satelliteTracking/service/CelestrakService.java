package com.satelliteTracking.service;

import com.satelliteTracking.dto.CelestrakSatelliteDTO;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CelestrakService {

    private static final Logger log = LoggerFactory.getLogger(CelestrakService.class);

    private final WebClient webClient;
    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;
    private final SatelliteAuditFileService satelliteAuditFileService;

    private final AtomicBoolean isDownloading = new AtomicBoolean(false);

    private static final String[] SATELLITE_GROUPS = {
            "stations", "starlink", "oneweb", "gps-ops",
            "galileo", "beidou", "weather", "geo"
    };

    public CelestrakService(SatelliteRepository satelliteRepository,
                            OrbitalParametersRepository orbitalParametersRepository,
                            SatelliteAuditFileService satelliteAuditFileService) {

        log.info("🔧 Inizializzazione CelestrakService");
        
        this.webClient = WebClient.builder()
                .baseUrl("https://celestrak.org")
                .defaultHeader("User-Agent", "SatelliteTracker")
                .build();

        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
        this.satelliteAuditFileService = satelliteAuditFileService;
        
        log.info("   Gruppi satelliti configurati: {}", String.join(", ", SATELLITE_GROUPS));
    }

    @Transactional
    public void fetchAndSaveStations() {

        if (!isDownloading.compareAndSet(false, true)) {
            log.info("⏭️  CelesTrak già in esecuzione, salto questa chiamata");
            return;
        }

        try {

            log.info("═══════════════════════════════════════════════════════════");
            log.info("🌍 INIZIO DOWNLOAD DA CELESTRAK");
            log.info("   Gruppi da scaricare: {}", SATELLITE_GROUPS.length);
            log.info("═══════════════════════════════════════════════════════════");
                satelliteAuditFileService.appendFetchHeader(
                    "celestrak",
                    java.time.LocalDateTime.now(java.time.ZoneOffset.UTC),
                    "download completo"
                );

            int totalSatellites = 0;
            int totalGroups = 0;

            for (String group : SATELLITE_GROUPS) {

                totalGroups++;
                log.info("📦 Gruppo {}/{}: '{}' - Avvio download...", 
                        totalGroups, SATELLITE_GROUPS.length, group);

                List<CelestrakSatelliteDTO> satellites = downloadGroupWithRetry(group);

                if (satellites == null || satellites.isEmpty()) {
                    continue;
                }

                log.info("   Inizio salvataggio nel database...");

                int savedInGroup = 0;
                
                for (CelestrakSatelliteDTO dto : satellites) {

                    Satellite sat = satelliteRepository
                            .findByNoradCatId(dto.noradCatId())
                            .orElseGet(() -> {
                                Satellite s = new Satellite();
                                s.setNoradCatId(dto.noradCatId());
                                log.debug("      Nuovo satellite: NORAD {} - {}", 
                                        dto.noradCatId(), dto.objectName());
                            satelliteAuditFileService.appendNewSatellite(
                                "celestrak",
                                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC),
                                String.format(
                                    "norad=%s | objectName=%s | objectId=%s | group=%s | epoch=%s | meanMotion=%s | inclination=%s",
                                    dto.noradCatId(),
                                    dto.objectName(),
                                    dto.objectId(),
                                    group,
                                    dto.epoch(),
                                    dto.meanMotion(),
                                    dto.inclination()
                                )
                            );
                                return s;
                            });

                    sat.setObjectName(dto.objectName());
                    sat.setObjectId(dto.objectId());
                    sat.setSatelliteType(group);

                    OrbitalParameters op = new OrbitalParameters(
                            sat,
                            dto.epoch(),
                            dto.inclination(),
                            dto.raOfAscNode(),
                            dto.eccentricity(),
                            dto.argOfPericenter(),
                            dto.meanAnomaly(),
                            dto.meanMotion()
                    );

                    sat.addOrbitalParameters(op);

                    satelliteRepository.save(sat);
                    savedInGroup++;
                }

                totalSatellites += savedInGroup;
                log.info("✅ Gruppo '{}': {} satelliti salvati nel database", group, savedInGroup);
            }

            log.info("═══════════════════════════════════════════════════════════");
            log.info("✅ CELESTRAK DOWNLOAD COMPLETATO");
            log.info("   Gruppi processati: {}", totalGroups);
            log.info("   Satelliti totali salvati: {}", totalSatellites);
            log.info("═══════════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("═══════════════════════════════════════════════════════════");
            log.error("❌ ERRORE CELESTRAK");
            log.error("   Tipo: {}", e.getClass().getSimpleName());
            log.error("   Messaggio: {}", e.getMessage());
            log.error("═══════════════════════════════════════════════════════════", e);
        } finally {
            isDownloading.set(false);
            log.debug("🔓 CelesTrak lock rilasciato");
        }
    }

    private List<CelestrakSatelliteDTO> downloadGroupWithRetry(String group) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            long startTime = System.currentTimeMillis();
            try {
                List<CelestrakSatelliteDTO> satellites = webClient.get()
                        .uri("/NORAD/elements/gp.php?GROUP=" + group + "&FORMAT=json")
                        .retrieve()
                        .bodyToFlux(CelestrakSatelliteDTO.class)
                        .timeout(Duration.ofMinutes(3))
                        .collectList()
                        .block();

                long duration = System.currentTimeMillis() - startTime;

                if (satellites == null || satellites.isEmpty()) {
                    log.warn("⚠️  Gruppo '{}': Nessun satellite ricevuto (tempo: {}ms)", group, duration);
                    return null;
                }

                log.info("✅ Gruppo '{}': {} satelliti scaricati in {}ms", 
                        group, satellites.size(), duration);
                return satellites;
            } catch (WebClientResponseException e) {
                long duration = System.currentTimeMillis() - startTime;
                if (e.getStatusCode().value() == 500 && attempt == 1) {
                    log.warn("⚠️  Gruppo '{}': 500 da CelesTrak ({}ms), attendo 30s prima di riprovare", group, duration);
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                }

                log.error("❌ Gruppo '{}': errore HTTP {} dopo {}ms", group, e.getStatusCode(), duration);
                return null;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("❌ Gruppo '{}': errore {} dopo {}ms", group, e.getClass().getSimpleName(), duration, e);
                return null;
            }
        }

        return null;
    }
}