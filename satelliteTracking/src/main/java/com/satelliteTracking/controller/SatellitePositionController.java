package com.satelliteTracking.controller;

import com.satelliteTracking.dto.SatellitePositionDTO;
import com.satelliteTracking.service.SatellitePassService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/satellites")
public class SatellitePositionController {

    private final SatellitePassService satellitePassService;

    public SatellitePositionController(SatellitePassService satellitePassService) {
        this.satellitePassService = satellitePassService;
    }

    @GetMapping("/{id}/position")
    public ResponseEntity<SatellitePositionDTO> getCurrentSatellitePosition(@PathVariable Long id) {
        return satellitePassService.getCurrentSatellitePosition(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/positions")
    public ResponseEntity<List<SatellitePositionDTO>> getCurrentSatellitePositions(
        @RequestParam(required = false) String type) {

        return ResponseEntity.ok(satellitePassService.getCurrentSatellitePositions(type));
    }

}