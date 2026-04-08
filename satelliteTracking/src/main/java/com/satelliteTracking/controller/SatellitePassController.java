package com.satelliteTracking.controller;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.TelegramSubscription;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.service.SatellitePassService;
import com.satelliteTracking.service.TelegramNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/satellites")
public class SatellitePassController {

    private final SatelliteRepository satelliteRepository;
    private final SatellitePassService satellitePassService;
    private final TelegramNotificationService telegramNotificationService;
    private final double defaultLatitude;
    private final double defaultLongitude;
    private final double defaultAltitude;

    public SatellitePassController(SatelliteRepository satelliteRepository,
                                   SatellitePassService satellitePassService,
                                   TelegramNotificationService telegramNotificationService,
                                   @Value("${satellite.default-location.latitude:41.01}") double defaultLatitude,
                                   @Value("${satellite.default-location.longitude:14.30}") double defaultLongitude,
                                   @Value("${satellite.default-location.altitude:30.0}") double defaultAltitude) {
        this.satelliteRepository = satelliteRepository;
        this.satellitePassService = satellitePassService;
        this.telegramNotificationService = telegramNotificationService;
        this.defaultLatitude = defaultLatitude;
        this.defaultLongitude = defaultLongitude;
        this.defaultAltitude = defaultAltitude;
    }

    @GetMapping("/passes/by-name")
    public ResponseEntity<?> getSatellitePassesByName(
        @RequestParam(defaultValue = "ISS") String name,
        @RequestParam(defaultValue = "24") int hours) {

        return satelliteRepository.findFirstByObjectNameContainingIgnoreCaseOrderByIdAsc(name)
            .<ResponseEntity<?>>map(satellite -> ResponseEntity.ok(satellitePassService.calculatePasses(satellite.getId(), hours)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/passes/iss")
    public ResponseEntity<?> getIssPasses(@RequestParam(defaultValue = "24") int hours) {
        return getSatellitePassesByName("ISS", hours);
    }

    @GetMapping("/{id}/passes")
    public ResponseEntity<List<SatellitePassDTO>> getSatellitePasses(
        @PathVariable Long id,
        @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(satellitePassService.calculatePasses(id, hours));
    }

    @GetMapping("/{id}/passes/custom")
    public ResponseEntity<List<SatellitePassDTO>> getSatellitePassesCustomLocation(
        @PathVariable Long id,
        @RequestParam double lat,
        @RequestParam double lon,
        @RequestParam(defaultValue = "0") double alt,
        @RequestParam(defaultValue = "24") int hours) {

        ObserverLocation customLocation = new ObserverLocation(lat, lon, alt);
        return ResponseEntity.ok(satellitePassService.calculatePasses(id, hours, customLocation));
    }

    @GetMapping("/observer-location")
    public ResponseEntity<ObserverLocation> getDefaultObserverLocation() {
        return ResponseEntity.ok(satellitePassService.getDefaultLocation());
    }

    @GetMapping("/upcoming-passes")
    public ResponseEntity<List<SatellitePassDTO>> getUpcomingPasses(@RequestParam(defaultValue = "6") int hours) {
        List<SatellitePassDTO> passes = satellitePassService.findVisibleUpcomingPasses(hours, 30.0);
        sendFirstPassNotificationsIfNeeded(passes);
        return ResponseEntity.ok(passes);
    }

    @GetMapping("/upcoming-passes/custom")
    public ResponseEntity<List<SatellitePassDTO>> getUpcomingPassesCustom(
        @RequestParam(defaultValue = "6") int hours,
        @RequestParam(defaultValue = "30") double minElevation,
        @RequestParam double latitude,
        @RequestParam double longitude,
        @RequestParam(defaultValue = "0") double altitude) {

        ObserverLocation customLocation = new ObserverLocation(latitude, longitude, altitude);
        return ResponseEntity.ok(satellitePassService.findVisibleUpcomingPasses(hours, minElevation, customLocation));
    }

    @GetMapping("/upcoming-passes/filtered")
    public ResponseEntity<List<SatellitePassDTO>> getUpcomingPassesFiltered(
        @RequestParam(defaultValue = "6") int hours,
        @RequestParam(defaultValue = "30") double minElevation,
        @RequestParam(defaultValue = "any") String observingCondition,
        @RequestParam(defaultValue = "6.0") double maxMagnitude) {

        List<SatellitePassDTO> passes = satellitePassService.findVisibleUpcomingPasses(
            hours,
            minElevation,
            observingCondition,
            maxMagnitude
        );
        sendFirstPassNotificationsIfNeeded(passes);
        return ResponseEntity.ok(passes);
    }

    @GetMapping("/upcoming-passes/filtered/custom")
    public ResponseEntity<List<SatellitePassDTO>> getUpcomingPassesFilteredCustom(
        @RequestParam(defaultValue = "6") int hours,
        @RequestParam(defaultValue = "30") double minElevation,
        @RequestParam(defaultValue = "any") String observingCondition,
        @RequestParam(defaultValue = "6.0") double maxMagnitude,
        @RequestParam double latitude,
        @RequestParam double longitude,
        @RequestParam(defaultValue = "0") double altitude) {

        ObserverLocation customLocation = new ObserverLocation(latitude, longitude, altitude);
        return ResponseEntity.ok(satellitePassService.findVisibleUpcomingPasses(
            hours,
            minElevation,
            customLocation,
            observingCondition,
            maxMagnitude
        ));
    }

    @GetMapping("/passes/upcoming")
    public ResponseEntity<?> getUpcomingPassesDetailed(
        @RequestParam(value = "hours", defaultValue = "3") Integer hours,
        @RequestParam(value = "latitude", required = false) Double latitude,
        @RequestParam(value = "longitude", required = false) Double longitude,
        @RequestParam(value = "altitude", required = false) Double altitude,
        @RequestParam(value = "minElevation", defaultValue = "30.0") Double minElevation) {

        latitude = latitude != null ? latitude : defaultLatitude;
        longitude = longitude != null ? longitude : defaultLongitude;
        altitude = altitude != null ? altitude : defaultAltitude;

        try {
            if (hours == null || hours <= 0 || hours > 24) {
                return ResponseEntity.badRequest().body(Map.of("error", "hours deve essere tra 1 e 24", "received", hours));
            }
            if (latitude == null || latitude < -90 || latitude > 90) {
                return ResponseEntity.badRequest().body(Map.of("error", "latitude deve essere tra -90 e 90", "received", latitude));
            }
            if (longitude == null || longitude < -180 || longitude > 180) {
                return ResponseEntity.badRequest().body(Map.of("error", "longitude deve essere tra -180 e 180", "received", longitude));
            }
            if (minElevation == null || minElevation < 0 || minElevation > 90) {
                return ResponseEntity.badRequest().body(Map.of("error", "minElevation deve essere tra 0 e 90", "received", minElevation));
            }

            ObserverLocation observer = new ObserverLocation(
                latitude,
                longitude,
                altitude,
                String.format("Custom (%.2f, %.2f, %.0fm)", latitude, longitude, altitude)
            );

            List<SatellitePassDTO> passes = satellitePassService.findVisibleUpcomingPasses(
                hours,
                minElevation,
                observer,
                "any",
                6.0
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("query", Map.of(
                "hours", hours,
                "observer", Map.of(
                    "latitude", latitude,
                    "longitude", longitude,
                    "altitude", altitude
                ),
                "minElevation", minElevation + "°"
            ));
            response.put("totalPasses", passes.size());
            response.put("passes", passes);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "error", "Errore durante il calcolo dei passaggi",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                )
            );
        }
    }

    private void sendFirstPassNotificationsIfNeeded(List<SatellitePassDTO> passes) {
        if (passes.isEmpty()) {
            return;
        }

        try {
            List<TelegramSubscription> subscriptions = telegramNotificationService.getAllSubscriptions();
            LocalDateTime now = LocalDateTime.now();
            SatellitePassDTO firstPass = passes.get(0);

            for (TelegramSubscription sub : subscriptions) {
                if (!sub.getNotificationsEnabled()) {
                    continue;
                }

                long minutesSinceLast = java.time.temporal.ChronoUnit.MINUTES
                    .between(sub.getLastNotificationSent(), now);

                if (minutesSinceLast >= 30) {
                    telegramNotificationService.sendNotificationToUser(
                        sub,
                        firstPass.satelliteName(),
                        firstPass.riseTime(),
                        firstPass.maxElevation(),
                        firstPass.estimatedMagnitude()
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Errore invio notifiche: " + e.getMessage());
        }
    }
}
