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
            "stations", "starlink", "oneweb", "gps-ops",
            "galileo", "beidou", "weather", "geo"
    };

    public CelestrakService(SatelliteRepository satelliteRepository,
                            OrbitalParametersRepository orbitalParametersRepository) {

        this.webClient = WebClient.builder()
                .baseUrl("https://celestrak.org")
                .defaultHeader("User-Agent", "SatelliteTracker")
                .build();

        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    @Transactional
    public void fetchAndSaveStations() {

        if (!isDownloading.compareAndSet(false, true)) {
            log.info("Skip CelesTrak (already running)");
            return;
        }

        try {

            log.info("📡 CelesTrak bulk fetch");

            for (String group : SATELLITE_GROUPS) {

                List<CelestrakSatelliteDTO> satellites =
                        webClient.get()
                                .uri("/NORAD/elements/gp.php?GROUP=" + group + "&FORMAT=json")
                                .retrieve()
                                .bodyToFlux(CelestrakSatelliteDTO.class)
                                .timeout(Duration.ofMinutes(3))
                                .collectList()
                                .block();

                if (satellites == null) continue;

                for (CelestrakSatelliteDTO dto : satellites) {

                    Satellite sat = satelliteRepository
                            .findByNoradCatId(dto.noradCatId())
                            .orElseGet(() -> {
                                Satellite s = new Satellite();
                                s.setNoradCatId(dto.noradCatId());
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
                }
            }

        } finally {
            isDownloading.set(false);
        }
    }
}