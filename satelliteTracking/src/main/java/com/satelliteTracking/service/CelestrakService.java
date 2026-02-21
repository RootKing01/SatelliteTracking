package com.satelliteTracking.service;
import com.satelliteTracking.dto.CelestrakSatelliteDTO;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.model.Satellite;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

@Service
public class CelestrakService {

    private final WebClient webClient;
    private final SatelliteRepository satelliteRepository;

    public CelestrakService(SatelliteRepository satelliteRepository) {
        this.webClient = WebClient.builder()
                .baseUrl("https://celestrak.org")
                .defaultHeader("User-Agent", "SatelliteTracker/1.0")
                .build();
        this.satelliteRepository = satelliteRepository;
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
                Satellite sat = new Satellite();
                sat.setObjectName(dto.objectName());
                sat.setObjectId(dto.objectId());
                sat.setNoradCatId(dto.noradCatId());
                sat.setEpoch(dto.epoch());
                sat.setInclination(dto.inclination());
                sat.setRaOfAscNode(dto.raOfAscNode());
                sat.setEccentricity(dto.eccentricity());
                sat.setArgOfPericenter(dto.argOfPericenter());
                sat.setMeanAnomaly(dto.meanAnomaly());
                sat.setMeanMotion(dto.meanMotion());

                satelliteRepository.save(sat);
            });
        }
    }
}