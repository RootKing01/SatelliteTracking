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

    // TLE raw data
    @Column(columnDefinition = "TEXT")
    private String tleLine1;

    @Column(columnDefinition = "TEXT")
    private String tleLine2;

    private Double raan;
    private Double argumentOfPerigee;

    private LocalDateTime fetchedAt;

    // ✅ REQUIRED BY JPA (fix del tuo errore principale)
    public OrbitalParameters() {
        this.fetchedAt = LocalDateTime.now();
    }

    // ✅ COSTRUTTORE USATO DAL TLE SERVICE
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
        this.satellite = satellite;
        this.epoch = epoch;
        this.inclination = inclination;
        this.raOfAscNode = raOfAscNode;
        this.eccentricity = eccentricity;
        this.argOfPericenter = argOfPericenter;
        this.meanAnomaly = meanAnomaly;
        this.meanMotion = meanMotion;
        this.fetchedAt = LocalDateTime.now();
    }
}