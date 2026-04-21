package com.satelliteTracking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "orbital_parameters")
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