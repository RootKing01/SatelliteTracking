package com.satelliteTracking.dto;

import java.time.LocalDateTime;

public record SatelliteSightingDTO(
    Long id,
    Long satelliteId,
    String satelliteName,
    Long noradCatId,
    LocalDateTime sightedAt,
    boolean valid,
    String validationMessage,
    Double estimatedMagnitude,
    Double maxElevationDeg,
    String observerLocationName,
    Double observerLatitude,
    Double observerLongitude
) {
}
