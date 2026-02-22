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
    @JoinColumn(name = "satellite_id", nullable = false)
    private Satellite satellite;

    @Column(nullable = false)
    private String epoch;

    @Column(nullable = false)
    private Double inclination;

    @Column(nullable = false)
    private Double raOfAscNode;

    @Column(nullable = false)
    private Double eccentricity;

    @Column(nullable = false)
    private Double argOfPericenter;

    @Column(nullable = false)
    private Double meanAnomaly;

    @Column(nullable = false)
    private Double meanMotion;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    public OrbitalParameters() {
        this.fetchedAt = LocalDateTime.now();
    }

    public OrbitalParameters(Satellite satellite, String epoch, Double inclination, 
                             Double raOfAscNode, Double eccentricity, 
                             Double argOfPericenter, Double meanAnomaly, Double meanMotion) {
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
