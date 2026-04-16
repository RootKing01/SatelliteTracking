package com.satelliteTracking.service;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.dto.SatelliteSightingCreateRequestDTO;
import com.satelliteTracking.dto.SatelliteSightingDTO;
import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.model.SatelliteSighting;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.repository.SatelliteSightingRepository;
import jakarta.servlet.http.HttpSession;
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
    private static final double NAKED_EYE_MAGNITUDE_LIMIT = 6.0;

    private final SatelliteRepository satelliteRepository;
    private final SatelliteSightingRepository satelliteSightingRepository;
    private final SatellitePassService satellitePassService;
    private final PassTimeService passTimeService;
    private final AuthService authService;
    private final CityGeocodingService cityGeocodingService;

    public SatelliteSightingService(SatelliteRepository satelliteRepository,
                                    SatelliteSightingRepository satelliteSightingRepository,
                                    SatellitePassService satellitePassService,
                                    PassTimeService passTimeService,
                                    AuthService authService,
                                    CityGeocodingService cityGeocodingService) {
        this.satelliteRepository = satelliteRepository;
        this.satelliteSightingRepository = satelliteSightingRepository;
        this.satellitePassService = satellitePassService;
        this.passTimeService = passTimeService;
        this.authService = authService;
        this.cityGeocodingService = cityGeocodingService;
    }

    @Transactional
    public SatelliteSightingDTO registerSighting(SatelliteSightingCreateRequestDTO request, HttpSession session) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
        }

        Long satelliteId = request.satelliteId();
        if (satelliteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
        }

        AppUser user = authService.requireAuthenticatedUser(session);
        Satellite satellite = satelliteRepository.findById(satelliteId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Satellite non trovato"));

        ObserverLocation observerLocation = resolveObserverLocation(request);

        LocalDateTime sightedAt = passTimeService.nowForObserver(observerLocation);

        List<SatellitePassDTO> passes = satellitePassService.calculatePasses(
            satelliteId,
            VALIDATION_WINDOW_HOURS,
            observerLocation
        );

        SatellitePassDTO activePass = passes.stream()
            .filter(pass -> !sightedAt.isBefore(pass.riseTime()) && !sightedAt.isAfter(pass.setTime()))
            .findFirst()
            .orElse(null);

        if (activePass == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
        }

        boolean visible = activePass.isVisible();
        boolean brightEnough = activePass.estimatedMagnitude() <= NAKED_EYE_MAGNITUDE_LIMIT;
        if (!visible || !brightEnough) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
        }

        SatelliteSighting sighting = new SatelliteSighting();
        sighting.setUser(user);
        sighting.setSatellite(satellite);
        sighting.setSightedAt(sightedAt);
        sighting.setValid(true);
        sighting.setValidationMessage("Avvistamento valido.");
        sighting.setEstimatedMagnitude(activePass.estimatedMagnitude());
        sighting.setMaxElevationDeg(activePass.maxElevation());
        sighting.setObserverLocationName(observerLocation.getLocationName() == null
            ? "Posizione utente"
            : observerLocation.getLocationName());
        sighting.setObserverLatitude(observerLocation.getLatitude());
        sighting.setObserverLongitude(observerLocation.getLongitude());

        SatelliteSighting saved = satelliteSightingRepository.save(sighting);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SatelliteSightingDTO> getMySightings(HttpSession session) {
        AppUser user = authService.requireAuthenticatedUser(session);

        return satelliteSightingRepository.findByUserIdOrderBySightedAtDesc(user.getId()).stream()
            .sorted(Comparator.comparing(SatelliteSighting::getSightedAt).reversed())
            .map(this::toDto)
            .toList();
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
