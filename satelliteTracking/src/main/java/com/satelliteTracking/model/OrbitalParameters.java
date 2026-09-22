package com.satelliteTracking.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "orbital_parameters",
    indexes = {
        @Index(
            name = "idx_orbital_parameters_satellite_fetched_id",
            columnList = "satellite_id, fetched_at, id"
        )
    }
)
public class OrbitalParameters {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Satellite satellite;

    private String epoch;

    private Double inclination;
    private Double raOfAscNode;
    private Double eccentricity;
    private Double argOfPericenter;
    private Double meanAnomaly;
    private Double meanMotion;

    @Column(columnDefinition = "TEXT")
    private String tleLine1;

    @Column(columnDefinition = "TEXT")
    private String tleLine2;

    // FIX: naming coerente con TleDataService
    private Double raan;
    private Double argumentOfPerigee;

    private LocalDateTime fetchedAt;

    public OrbitalParameters() {
        this.fetchedAt = LocalDateTime.now();
    }

    public OrbitalParameters(
            Satellite satellite,
            String epoch,
            Double inclination,
            Double raOfAscNode,
            Double eccentricity,
            Double argOfPericenter,
            Double meanAnomaly,
            Double meanMotion
    ) {
        this();
        this.satellite = satellite;
        this.epoch = epoch;
        this.inclination = inclination;
        this.raOfAscNode = raOfAscNode;
        this.eccentricity = eccentricity;
        this.argOfPericenter = argOfPericenter;
        this.meanAnomaly = meanAnomaly;
        this.meanMotion = meanMotion;
    }
}