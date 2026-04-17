package com.satelliteTracking.dto;

import java.time.LocalDateTime;

public record SatellitePositionDTO(
    Long satelliteId,
    String satelliteName,
    String satelliteType,
    String objectId,
    Long noradCatId,
    LocalDateTime calculatedAtUtc,
    double latitudeDeg,
    double longitudeDeg,
    double altitudeKm,
    double distanceFromEarthCenterKm,
    double meanMotion,
    double orbitalPeriodMinutes,
    double orbitalPeriodHours,
    double velocityKmh,
    double directionDeg,
    OrbitalParametersDTO latestOrbitalParameters
) {
}
