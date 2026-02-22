package com.satelliteTracking.dto;

import com.satelliteTracking.model.Satellite;

public record SatelliteDTO(
    Long id,
    String objectName,
    String objectId,
    Long noradCatId,
    OrbitalParametersDTO latestOrbitalParameters
) {
    public static SatelliteDTO fromEntity(Satellite satellite, OrbitalParametersDTO latestParams) {
        return new SatelliteDTO(
            satellite.getId(),
            satellite.getObjectName(),
            satellite.getObjectId(),
            satellite.getNoradCatId(),
            latestParams
        );
    }
}
