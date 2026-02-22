package com.satelliteTracking.service;
import com.satelliteTracking.dto.CelestrakSatelliteDTO;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Optional;

@Service
public class CelestrakService {

    private final WebClient webClient;
    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;

    public CelestrakService(SatelliteRepository satelliteRepository, 
                            OrbitalParametersRepository orbitalParametersRepository) {
        this.webClient = WebClient.builder()
                .baseUrl("https://celestrak.org")
                .defaultHeader("User-Agent", "SatelliteTracker/1.0")
                .build();
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    public void fetchAndSaveStations() {
        List<CelestrakSatelliteDTO> satellites = webClient.get()
                .uri("/NORAD/elements/gp.php?GROUP=stations&FORMAT=json")
                .retrieve()
                .bodyToFlux(CelestrakSatelliteDTO.class)
                .collectList()
                .block();

        if (satellites != null) {
            satellites.forEach(dto -> {
                // Cerca se il satellite esiste già nel database
                Optional<Satellite> existingSatellite = satelliteRepository.findByNoradCatId(dto.noradCatId());
                
                Satellite satellite;
                if (existingSatellite.isPresent()) {
                    // Satellite già esistente, usa quello
                    satellite = existingSatellite.get();
                    
                    // Aggiorna eventuali informazioni del satellite se necessario
                    satellite.setObjectName(dto.objectName());
                    satellite.setObjectId(dto.objectId());
                } else {
                    // Nuovo satellite, crealo
                    satellite = new Satellite();
                    satellite.setObjectName(dto.objectName());
                    satellite.setObjectId(dto.objectId());
                    satellite.setNoradCatId(dto.noradCatId());
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
            });
        }
    }
}