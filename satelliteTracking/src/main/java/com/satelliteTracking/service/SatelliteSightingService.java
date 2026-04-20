package com.satelliteTracking.service;
import com.satelliteTracking.dto.SatellitePositionDTO;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.dto.SatelliteSightingCreateRequestDTO;
import com.satelliteTracking.dto.SatelliteSightingDTO;
import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.model.SatelliteSighting;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.repository.SatelliteSightingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;



@Service
public class SatelliteSightingService {

    private static final int VALIDATION_WINDOW_HOURS = 6;
    private static final double NAKED_EYE_MAGNITUDE_LIMIT = 3.0;

    private final SatelliteRepository satelliteRepository;
    private final SatelliteSightingRepository satelliteSightingRepository;
    private final SatellitePassService satellitePassService;
    private final com.satelliteTracking.repository.OrbitalParametersRepository orbitalParametersRepository;
    private final PassTimeService passTimeService;
    private final AuthService authService;
    private final CityGeocodingService cityGeocodingService;
    private final SatellitePositionService satellitePositionService;

    public SatelliteSightingService(SatelliteRepository satelliteRepository,
                                    SatelliteSightingRepository satelliteSightingRepository,
                                    SatellitePassService satellitePassService,
                                    PassTimeService passTimeService,
                                    AuthService authService,
                                    CityGeocodingService cityGeocodingService,
                                    SatellitePositionService satellitePositionService,
                                    com.satelliteTracking.repository.OrbitalParametersRepository orbitalParametersRepository) {
        this.satelliteRepository = satelliteRepository;
        this.satelliteSightingRepository = satelliteSightingRepository;
        this.satellitePassService = satellitePassService;
        this.passTimeService = passTimeService;
        this.authService = authService;
        this.cityGeocodingService = cityGeocodingService;
        this.satellitePositionService = satellitePositionService;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    @Transactional
    public SatelliteSightingDTO registerSighting(SatelliteSightingCreateRequestDTO request) {


        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, richiesta nulla");
        }

        Long satelliteId = request.satelliteId();
        if (satelliteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, id satellite non trovato");
        }

        AppUser user = authService.requireAuthenticatedUser();
        Satellite satellite = satelliteRepository.findById(satelliteId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Satellite non trovato"));

        ObserverLocation observerLocation = resolveObserverLocation(request);

        // Recupera i parametri orbitali PRIMA del filtro temporale
        var params = orbitalParametersRepository.findTopBySatelliteOrderByFetchedAtDesc(satellite);
        if (params == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parametri orbitali non disponibili");
        }

        // Finestra temporale: periodo orbitale del satellite (in minuti)
        final double TOLLERANZA_KM = 10.0;
        double periodoMin = 90.0; // fallback se non disponibile
        if (params.getMeanMotion() != null && params.getMeanMotion() > 0) {
            periodoMin = 1440.0 / params.getMeanMotion();
        }
        LocalDateTime now = passTimeService.nowForObserver(observerLocation);
        LocalDateTime start = now.minusMinutes((long) (periodoMin / 2));
        LocalDateTime end = now.plusMinutes((long) (periodoMin / 2));
        List<SatelliteSighting> precedenti = satelliteSightingRepository.findByUserIdAndSatelliteIdAndSightedAtBetween(
            user.getId(), satelliteId, start, end);
        for (SatelliteSighting s : precedenti) {
            double distanza = haversineDistanceKm(
                s.getObserverLatitude(),
                s.getObserverLongitude(),
                observerLocation.getLatitude(),
                observerLocation.getLongitude()
            );
            if (distanza < TOLLERANZA_KM) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avvistamento troppo vicino a uno già registrato durante l'ultimo periodo orbitale (" + String.format("%.2f", distanza) + " km)");
            }
        }

        LocalDateTime sightedAt = passTimeService.nowForObserver(observerLocation);

        // Calcolo posizione/orientamento satellite rispetto all'osservatore
        var absDate = passTimeService.nowUtc();
        SatellitePositionDTO obs = satellitePositionService.computeObservation(
            satellite,
            params,
            absDate,
            observerLocation.getLatitude(),
            observerLocation.getLongitude(),
            observerLocation.getAltitude()
        );
        if (obs.elevationDeg() != null && obs.elevationDeg() < 0.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, satellite non sopra l'orizzonte");
        }
        if (obs.estimatedMagnitude() == null || obs.estimatedMagnitude() > NAKED_EYE_MAGNITUDE_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, magnitudine troppo debole per la visione ad occhio nudo");
        }
        if (obs.isVisible() != null && !obs.isVisible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, satellite non visibile in questo momento");
        }
        if (obs.visibility() != null && obs.visibility().equalsIgnoreCase("poor")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, condizioni di visibilità scarse");
        }
        if (obs.observingCondition() != null && obs.observingCondition().equalsIgnoreCase("unknown")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, condizioni di osservazione non determinabili");
        }

        SatelliteSighting sighting = new SatelliteSighting();
        sighting.setUser(user);
        sighting.setSatellite(satellite);
        sighting.setSightedAt(sightedAt);
        sighting.setValid(true);
        sighting.setValidationMessage("Avvistamento valido.");
        sighting.setEstimatedMagnitude(obs.estimatedMagnitude());
        sighting.setMaxElevationDeg(obs.elevationDeg() != null ? obs.elevationDeg() : 0.0);
        sighting.setObserverLocationName(observerLocation.getLocationName() == null
            ? "Posizione utente"
            : observerLocation.getLocationName());
        sighting.setObserverLatitude(observerLocation.getLatitude());
        sighting.setObserverLongitude(observerLocation.getLongitude());

        SatelliteSighting saved = satelliteSightingRepository.save(sighting);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SatelliteSightingDTO> getMySightings() {
        AppUser user = authService.requireAuthenticatedUser();

        return satelliteSightingRepository.findByUserIdOrderBySightedAtDesc(user.getId()).stream()
            .sorted(Comparator.comparing(SatelliteSighting::getSightedAt).reversed())
            .map(this::toDto)
            .toList();
    }

     /**
     * Calcola la distanza geodetica (haversine) tra due punti in km.
     */
    private static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Raggio medio della Terra in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }


    private SatelliteSightingDTO toDto(SatelliteSighting sighting) {
        return new SatelliteSightingDTO(
            sighting.getId(),
            sighting.getSatellite().getId(),
            sighting.getSatellite().getObjectName(),
            sighting.getSatellite().getNoradCatId(),
            sighting.getSightedAt(),
            sighting.isValid(),
            sighting.getValidationMessage(),
            sighting.getEstimatedMagnitude(),
            sighting.getMaxElevationDeg(),
            sighting.getObserverLocationName(),
            sighting.getObserverLatitude(),
            sighting.getObserverLongitude()
        );
    }

    private ObserverLocation resolveObserverLocation(SatelliteSightingCreateRequestDTO request) {
        Double latitude = request.latitude();
        Double longitude = request.longitude();

        if (latitude != null || longitude != null) {
            if (latitude == null || longitude == null ||
                !Double.isFinite(latitude) || !Double.isFinite(longitude) ||
                latitude < -90.0 || latitude > 90.0 ||
                longitude < -180.0 || longitude > 180.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
            }

            double altitude = request.altitudeMeters() != null && Double.isFinite(request.altitudeMeters())
                ? request.altitudeMeters()
                : 30.0;
            return new ObserverLocation(latitude, longitude, altitude, "Posizione browser");
        }

        String city = request.city();
        if (city != null && !city.trim().isEmpty()) {
            return cityGeocodingService.resolveCity(city);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
    }
}
