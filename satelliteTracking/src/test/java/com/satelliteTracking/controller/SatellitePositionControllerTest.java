package com.satelliteTracking.controller;

import com.satelliteTracking.dto.SatellitePositionDTO;
import com.satelliteTracking.service.SatellitePassService;
import com.satelliteTracking.service.SpaceMissionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class SatellitePositionControllerTest {

    private final SatellitePassService satellitePassService = Mockito.mock(SatellitePassService.class);
    private final SpaceMissionService spaceMissionService = Mockito.mock(SpaceMissionService.class);

    private final SatellitePositionController controller = new SatellitePositionController(
        satellitePassService,
        spaceMissionService
    );

    @Test
    void shouldReturnCurrentSatellitePosition() {
        SatellitePositionDTO position = new SatellitePositionDTO(
            25544L,
            "ISS (ZARYA)",
            "stations",
            "1998-067A",
            25544L,
            LocalDateTime.of(2026, 4, 8, 19, 0),
            41.9,
            12.5,
            420.0,
            6798.0,
            15.5,
            92.9,
            1.55,
            27580.0,
            124.3,
            null, // OrbitalParametersDTO
            null, // observerLatitudeDeg
            null, // observerLongitudeDeg
            null, // observerAltitudeM
            null, // elevationDeg
            null, // azimuthDeg
            null, // rangeKm
            null, // estimatedMagnitude
            null, // isVisible
            null, // visibility
            null  // observingCondition
        );

        when(satellitePassService.getCurrentSatellitePosition(25544L))
            .thenReturn(Optional.of(position));

        ResponseEntity<SatellitePositionDTO> response = controller.getCurrentSatellitePosition(25544L);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(41.9, response.getBody().latitudeDeg());
    }

    @Test
    void shouldReturnCurrentSatellitePositionsBulk() {
        SatellitePositionDTO position = new SatellitePositionDTO(
            25544L,
            "ISS (ZARYA)",
            "stations",
            "1998-067A",
            25544L,
            LocalDateTime.of(2026, 4, 8, 19, 0),
            41.9,
            12.5,
            420.0,
            6798.0,
            15.5,
            92.9,
            1.55,
            27580.0,
            124.3,
            null, // OrbitalParametersDTO
            null, // observerLatitudeDeg
            null, // observerLongitudeDeg
            null, // observerAltitudeM
            null, // elevationDeg
            null, // azimuthDeg
            null, // rangeKm
            null, // estimatedMagnitude
            null, // isVisible
            null, // visibility
            null  // observingCondition
        );

        when(satellitePassService.getCurrentSatellitePositions(null))
            .thenReturn(List.of(position));

        ResponseEntity<List<SatellitePositionDTO>> response = controller.getCurrentSatellitePositions(null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(25544L, response.getBody().get(0).satelliteId());
    }
}