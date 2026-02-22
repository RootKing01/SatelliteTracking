package com.satelliteTracking.scheduler;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.service.CelestrakService;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class SatelliteScheduler {

    private final CelestrakService celestrakService;

    public SatelliteScheduler(CelestrakService celestrakService) {
        this.celestrakService = celestrakService;
    }

    @Scheduled(initialDelay = 60000, fixedRate = 86400000) // Primo download dopo 1 minuto, poi ogni 24 ore
    public void updateSatellites() {
        celestrakService.fetchAndSaveStations();
    }
}