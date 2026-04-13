package com.satelliteTracking.service;
import com.satelliteTracking.dto.CelestrakSatelliteDTO;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
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
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);

    private static final String[] SATELLITE_GROUPS = {
        // Stazioni Spaziali
        "stations",           // ISS, Tiangong, Mir
        
        // Costellazioni Comunicazione (ATTENZIONE: migliaia di satelliti!)
        "starlink",           // SpaceX Starlink
        "oneweb",             // OneWeb
        "iridium-NEXT",       // Iridium Communications
        "spire",              // Spire Global
        
        // Navigazione Satellitare
        "gps-ops",            // GPS (USA)
        "galileo",            // Galileo (Europa)
        // "glonass-ops",     // ❌ Rimosso: gruppo non più disponibile su Celestrak
        "beidou",             // BeiDou (Cina)
        "sbas",               // Satellite-Based Augmentation Systems
        
        // Scientifici e Osservazione
        "science",            // Hubble, JWST, telescopi spaziali
        "weather",            // NOAA, GOES, Meteosat
        "planet",             // Planet Labs (imaging terrestre)
        "radar",              // Satelliti radar
        
        // Geostazionari
        "geo",                // Satelliti geostazionari
        
        // Altri
        "amateur",            // Satelliti radioamatoriali
        "cubesat",            // CubeSat (piccoli satelliti)
        "education",          // Satelliti educativi
        "engineering",        // Satelliti di test ingegneristici
        "military"            // Satelliti militari declassificati
    };

    public CelestrakService(SatelliteRepository satelliteRepository, 
                            OrbitalParametersRepository orbitalParametersRepository) {
        // Increase buffer size to 20MB for large satellite groups like Starlink (6000+ satellites)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(20 * 1024 * 1024))  // 20MB
                .build();
        
        this.webClient = WebClient.builder()
                .baseUrl("https://celestrak.org")
                .defaultHeader("User-Agent", "SatelliteTracker/1.0")
                .exchangeStrategies(strategies)
                .build();
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    @Transactional
    public void fetchAndSaveStations() {
        // Evita download concorrenti
        if (!isDownloading.compareAndSet(false, true)) {
            log.info("Download already in progress, skipping current cycle");
            return;
        }
        
        try {
            log.info("Starting satellites download from Celestrak");
            long startTime = System.currentTimeMillis();
            int totalSaved = 0;
            int totalUpdated = 0;
            
            for (String group : SATELLITE_GROUPS) {
                try {
                    log.info("Downloading group: {}", group);
                    long groupStartTime = System.currentTimeMillis();
                    
                    // Usa streaming per gruppi grandi come Starlink (no buffer limit)
                    List<CelestrakSatelliteDTO> satellites = webClient.get()
                            .uri("/NORAD/elements/gp.php?GROUP=" + group + "&FORMAT=json")
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .bodyToFlux(CelestrakSatelliteDTO.class)
                            .onErrorResume(error -> {
                                log.error("Errore per gruppo '{}'", group, error);
                                return reactor.core.publisher.Flux.empty();
                            })
                            .timeout(Duration.ofMinutes(5))
                            .collectList()
                            .block();

                    if (satellites != null && !satellites.isEmpty()) {
                        int saved = 0;
                        int updated = 0;
                        
                        for (CelestrakSatelliteDTO dto : satellites) {
                            // Cerca se il satellite esiste già nel database
                            Optional<Satellite> existingSatellite = satelliteRepository.findByNoradCatId(dto.noradCatId());
                            
                            Satellite satellite;
                            if (existingSatellite.isPresent()) {
                                // Satellite già esistente, usa quello
                                satellite = existingSatellite.get();
                                
                                // Aggiorna eventuali informazioni del satellite se necessario
                                satellite.setObjectName(dto.objectName());
                                satellite.setObjectId(dto.objectId());
                                satellite.setSatelliteType(group);  // 🔧 Salva il tipo
                                updated++;
                            } else {
                                // Nuovo satellite, crealo
                                satellite = new Satellite();
                                satellite.setObjectName(dto.objectName());
                                satellite.setObjectId(dto.objectId());
                                satellite.setNoradCatId(dto.noradCatId());
                                satellite.setSatelliteType(group);  // 🔧 Salva il tipo
                                saved++;
                            }
                            
                            // Crea nuovi parametri orbitali
                            OrbitalParameters orbitalParams = new OrbitalParameters(
                                satellite,
                                dto.epoch(),
                                dto.inclination(),
                                dto.raOfAscNode(),
                                dto.eccentricity(),
                                dto.argOfPericenter(),
                                dto.meanAnomaly(),
                                dto.meanMotion()
                            );
                            
                            // Aggiungi i parametri orbitali al satellite
                            satellite.addOrbitalParameters(orbitalParams);
                            
                            // Salva il satellite (cascade salverà anche i parametri orbitali)
                            satelliteRepository.save(satellite);
                        }
                        
                        long groupDuration = System.currentTimeMillis() - groupStartTime;
                        totalSaved += saved;
                        totalUpdated += updated;
                        log.info("Group '{}' done: {} new, {} updated [{}ms]", group, saved, updated, groupDuration);
                    } else {
                        log.warn("No data for group: {}", group);
                    }
                    
                } catch (Exception e) {
                    log.error("Errore scaricando gruppo '{}'", group, e);
                }
            }
            
            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("Download completed: {} new, {} updated [{}s]", totalSaved, totalUpdated, totalDuration / 1000);
            
        } finally {
            // Resetta la flag per permettere il prossimo download
            isDownloading.set(false);
        }
    }
}