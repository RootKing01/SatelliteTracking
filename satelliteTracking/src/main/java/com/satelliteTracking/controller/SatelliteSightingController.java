package com.satelliteTracking.controller;

import com.satelliteTracking.dto.SatelliteSightingCreateRequestDTO;
import com.satelliteTracking.dto.SatelliteSightingDTO;
import com.satelliteTracking.service.SatelliteSightingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sightings")
public class SatelliteSightingController {

    private final SatelliteSightingService satelliteSightingService;

    public SatelliteSightingController(SatelliteSightingService satelliteSightingService) {
        this.satelliteSightingService = satelliteSightingService;
    }

    @PostMapping
    public ResponseEntity<?> createSighting(@RequestBody SatelliteSightingCreateRequestDTO request) {
        try {
            return ResponseEntity.ok(satelliteSightingService.registerSighting(request));
        } catch (ResponseStatusException ex) {
            HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
            return ResponseEntity.status(status).body(Map.of(
                "message",
                ex.getReason() == null ? "Errore, dati non compatibili" : ex.getReason()
            ));
        }
    }

    @GetMapping("/mine")
    public ResponseEntity<List<SatelliteSightingDTO>> getMySightings() {
        return ResponseEntity.ok(satelliteSightingService.getMySightings());
    }
}
