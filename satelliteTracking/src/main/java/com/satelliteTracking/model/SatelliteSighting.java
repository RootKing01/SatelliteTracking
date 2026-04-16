package com.satelliteTracking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "satellite_sightings")
public class SatelliteSighting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "satellite_id", nullable = false)
    private Satellite satellite;

    @Column(nullable = false)
    private LocalDateTime sightedAt;

    @Column(nullable = false)
    private boolean valid;

    @Column(nullable = false, length = 255)
    private String validationMessage;

    @Column
    private Double estimatedMagnitude;

    @Column
    private Double maxElevationDeg;

    @Column(nullable = false, length = 255)
    private String observerLocationName;

    @Column(nullable = false)
    private Double observerLatitude;

    @Column(nullable = false)
    private Double observerLongitude;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
