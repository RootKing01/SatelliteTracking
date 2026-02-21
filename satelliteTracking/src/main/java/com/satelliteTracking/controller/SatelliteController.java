package com.satelliteTracking.controller;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.SatelliteRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/satellites")
public class SatelliteController {

    private final SatelliteRepository repository;

    public SatelliteController(SatelliteRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Satellite> getAllSatellites() {
        return repository.findAll();
    }
}