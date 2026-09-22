package com.satelliteTracking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "satcat_cache")
public class SatcatCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long noradCatId;

    @Column(columnDefinition = "text")
    private String jsonData;

    private LocalDateTime fetchedAt;

}
