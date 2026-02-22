package com.satelliteTracking.dto;

import com.satelliteTracking.model.OrbitalParameters;
import java.time.LocalDateTime;

public record OrbitalParametersDTO(
    Long id,
    String epoch,
    Double inclination,
    Double raOfAscNode,
    Double eccentricity,
    Double argOfPericenter,
    Double meanAnomaly,
    Double meanMotion,
    LocalDateTime fetchedAt
) {
    public static OrbitalParametersDTO fromEntity(OrbitalParameters entity) {
        return new OrbitalParametersDTO(
            entity.getId(),
            entity.getEpoch(),
            entity.getInclination(),
            entity.getRaOfAscNode(),
            entity.getEccentricity(),
            entity.getArgOfPericenter(),
            entity.getMeanAnomaly(),
            entity.getMeanMotion(),
            entity.getFetchedAt()
        );
    }
}
