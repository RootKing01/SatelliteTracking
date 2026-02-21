package com.satelliteTracking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CelestrakSatelliteDTO(
        @JsonProperty("OBJECT_NAME") String objectName,
        @JsonProperty("OBJECT_ID") String objectId,
        @JsonProperty("NORAD_CAT_ID") Long noradCatId,
        @JsonProperty("EPOCH") String epoch,
        @JsonProperty("INCLINATION") Double inclination,
        @JsonProperty("RA_OF_ASC_NODE") Double raOfAscNode,
        @JsonProperty("ECCENTRICITY") Double eccentricity,
        @JsonProperty("ARG_OF_PERICENTER") Double argOfPericenter,
        @JsonProperty("MEAN_ANOMALY") Double meanAnomaly,
        @JsonProperty("MEAN_MOTION") Double meanMotion
) {}