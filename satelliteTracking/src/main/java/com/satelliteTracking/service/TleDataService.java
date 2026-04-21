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

        log.info("🚀 Aggiornamento TLE iniziato...");

        try {
            if (!"spacetrack".equalsIgnoreCase(primarySource)) {
                celestrakService.fetchAndSaveStations();
                return;
            }

            boolean ok = fetchFromSpaceTrack();

            if (!ok) {
                log.warn("⚠️ fallback CelesTrak");
                celestrakService.fetchAndSaveStations();
            }

        } catch (Exception e) {
            log.error("❌ errore globale TLE", e);
            celestrakService.fetchAndSaveStations();
        }
    }

    private boolean fetchFromSpaceTrack() {

        log.info("📡 SpaceTrack delta fetch");

        try {
            OrbitalParameters last = orbitalParametersRepository
                    .findTopByOrderByFetchedAtDesc();

            String lastEpoch = (last != null) ? last.getEpoch() : null;

            if (lastEpoch == null) {
                log.warn("⚠️ nessun epoch → skip delta");
                return false;
            }

            String tleData = spaceTrackService.downloadDeltaTle(lastEpoch);

            if (tleData == null || tleData.isBlank()) {
                log.warn("⚠️ SpaceTrack vuoto");
                return false;
            }

            int saved = parseAndSave(tleData);

            return saved > 0;

        } catch (Exception e) {
            log.warn("❌ SpaceTrack error", e);
            return false;
        }
    }

    private int parseAndSave(String tleData) {

        String[] lines = tleData.split("\n");
        int count = 0;

        for (int i = 0; i + 2 < lines.length; i += 3) {

            try {
                String line1 = lines[i + 1].trim();
                String line2 = lines[i + 2].trim();

                Long norad = Long.parseLong(line1.substring(2, 7).trim());

                Optional<Satellite> satOpt =
                        satelliteRepository.findByNoradCatId(norad);

                if (satOpt.isEmpty()) continue;

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

            } catch (Exception ignored) {}
        }

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