package com.satelliteTracking.dto;

public record SatelliteSightingCreateRequestDTO(
    Long satelliteId,
    String city,
    Double latitude,
    Double longitude,
    Double altitudeMeters
) {
}
