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

    @Scheduled(fixedRate = 300000) // ogni 5 minuti
    public void updateSatellites() {
        celestrakService.fetchAndSaveStations();
    }
}