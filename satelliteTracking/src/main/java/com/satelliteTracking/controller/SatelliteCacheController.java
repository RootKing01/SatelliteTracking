package com.satelliteTracking.controller;

import com.satelliteTracking.service.SatellitePassService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/satellites")
public class SatelliteCacheController {

    private final SatellitePassService satellitePassService;

    public SatelliteCacheController(SatellitePassService satellitePassService) {
        this.satellitePassService = satellitePassService;
    }

    @GetMapping("/cache-status")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        return ResponseEntity.ok(satellitePassService.getCacheStatus());
    }

    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, String>> clearCache() {
        satellitePassService.clearPassesCache();
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "Cache pulito con successo");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
