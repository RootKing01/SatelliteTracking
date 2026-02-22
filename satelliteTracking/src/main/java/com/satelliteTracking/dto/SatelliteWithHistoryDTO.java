package com.satelliteTracking.dto;

import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import java.util.List;
import java.util.stream.Collectors;

public record SatelliteWithHistoryDTO(
    Long id,
    String objectName,
    String objectId,
    Long noradCatId,
    List<OrbitalParametersDTO> orbitalHistory
) {
    public static SatelliteWithHistoryDTO fromEntity(Satellite satellite) {
        List<OrbitalParametersDTO> history = satellite.getOrbitalParametersList()
            .stream()
            .map(OrbitalParametersDTO::fromEntity)
            .collect(Collectors.toList());
        
        return new SatelliteWithHistoryDTO(
            satellite.getId(),
            satellite.getObjectName(),
            satellite.getObjectId(),
            satellite.getNoradCatId(),
            history
        );
    }
}
