package com.satelliteTracking.controller;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.service.GeocodingService;
import com.satelliteTracking.service.SatellitePassService;
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
public class SatelliteCityController {

    private final GeocodingService geocodingService;
    private final SatellitePassService satellitePassService;

    public SatelliteCityController(GeocodingService geocodingService,
                                   SatellitePassService satellitePassService) {
        this.geocodingService = geocodingService;
        this.satellitePassService = satellitePassService;
    }

    @GetMapping("/{id}/passes/by-city")
    public ResponseEntity<?> getSatellitePassesByCity(
        @PathVariable Long id,
        @RequestParam String city,
        @RequestParam(defaultValue = "24") int hours,
        @RequestParam(defaultValue = "30") double minElevation) {

        try {
            Map<String, Object> geoResult = geocodingService.geocodeCity(city);
            if (geoResult.containsKey("error")) {
                return ResponseEntity.badRequest().body(
                    Map.of(
                        "error", geoResult.get("error"),
                        "city", city,
                        "timestamp", LocalDateTime.now().toString()
                    )
                );
            }

            double latitude = (double) geoResult.get("latitude");
            double longitude = (double) geoResult.get("longitude");
            int altitude = ((Number) geoResult.get("altitude")).intValue();
            String displayName = (String) geoResult.get("displayName");

            ObserverLocation observer = new ObserverLocation(latitude, longitude, altitude);
            List<SatellitePassDTO> passes = satellitePassService.calculatePasses(id, hours, observer);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("city", Map.of(
                "name", city,
                "displayName", displayName,
                "latitude", latitude,
                "longitude", longitude,
                "altitude", altitude
            ));
            response.put("query", Map.of(
                "hours", hours,
                "minElevation", minElevation + "°"
            ));
            response.put("totalPasses", passes.size());
            response.put("passes", passes);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "error", "Errore durante il calcolo dei passaggi",
                    "city", city,
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                )
            );
        }
    }

    @GetMapping("/upcoming-passes/by-city")
    public ResponseEntity<?> getUpcomingPassesByCity(
        @RequestParam String city,
        @RequestParam(defaultValue = "6") int hours,
        @RequestParam(defaultValue = "30") double minElevation,
        @RequestParam(defaultValue = "any") String observingCondition,
        @RequestParam(defaultValue = "6.0") double maxMagnitude) {

        try {
            Map<String, Object> geoResult = geocodingService.geocodeCity(city);
            if (geoResult.containsKey("error")) {
                return ResponseEntity.badRequest().body(
                    Map.of(
                        "error", geoResult.get("error"),
                        "city", city,
                        "timestamp", LocalDateTime.now().toString()
                    )
                );
            }

            double latitude = (double) geoResult.get("latitude");
            double longitude = (double) geoResult.get("longitude");
            int altitude = ((Number) geoResult.get("altitude")).intValue();
            String displayName = (String) geoResult.get("displayName");

            ObserverLocation observer = new ObserverLocation(latitude, longitude, altitude);
            List<SatellitePassDTO> passes = satellitePassService.findVisibleUpcomingPasses(
                hours,
                minElevation,
                observer,
                observingCondition,
                maxMagnitude
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("city", Map.of(
                "name", city,
                "displayName", displayName,
                "latitude", latitude,
                "longitude", longitude,
                "altitude", altitude
            ));
            response.put("query", Map.of(
                "hours", hours,
                "minElevation", minElevation + "°",
                "observingCondition", observingCondition,
                "maxMagnitude", maxMagnitude
            ));
            response.put("totalPasses", passes.size());
            response.put("passes", passes);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "error", "Errore durante il calcolo dei passaggi",
                    "city", city,
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                )
            );
        }
    }
}
