package com.satelliteTracking.controller;

import com.satelliteTracking.dto.OrbitalParametersDTO;
import com.satelliteTracking.dto.SatelliteDTO;
import com.satelliteTracking.dto.SatelliteWithHistoryDTO;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/satellites")
public class SatelliteQueryController {

    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;

    public SatelliteQueryController(SatelliteRepository satelliteRepository,
                                    OrbitalParametersRepository orbitalParametersRepository) {
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    @GetMapping
    public List<SatelliteDTO> getAllSatellites(@RequestParam(name = "pendingOnly", defaultValue = "false") boolean pendingOnly) {
        Map<Long, OrbitalParameters> latestBySatelliteId = loadLatestParametersBySatelliteId();

        return satelliteRepository.findAll().stream()
            .filter(satellite -> !pendingOnly || isPendingClassification(satellite))
            .map(satellite -> {
                OrbitalParameters latestParams = latestBySatelliteId.get(satellite.getId());
                OrbitalParametersDTO paramsDTO = latestParams != null
                    ? OrbitalParametersDTO.fromEntity(latestParams)
                    : null;
                return SatelliteDTO.fromEntity(satellite, paramsDTO);
            })
            .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SatelliteDTO> getSatelliteById(@PathVariable Long id) {
        return satelliteRepository.findById(id)
            .map(satellite -> {
                OrbitalParameters latestParams = orbitalParametersRepository
                    .findTopBySatelliteOrderByFetchedAtDesc(satellite);
                OrbitalParametersDTO paramsDTO = latestParams != null
                    ? OrbitalParametersDTO.fromEntity(latestParams)
                    : null;
                return ResponseEntity.ok(SatelliteDTO.fromEntity(satellite, paramsDTO));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/orbital-history")
    public ResponseEntity<SatelliteWithHistoryDTO> getOrbitalHistory(@PathVariable Long id) {
        return satelliteRepository.findById(id)
            .map(satellite -> {
                List<OrbitalParametersDTO> history = orbitalParametersRepository
                    .findBySatelliteOrderByFetchedAtDesc(satellite)
                    .stream()
                    .map(OrbitalParametersDTO::fromEntity)
                    .collect(Collectors.toList());

                return ResponseEntity.ok(new SatelliteWithHistoryDTO(
                    satellite.getId(),
                    satellite.getObjectName(),
                    satellite.getObjectId(),
                    satellite.getNoradCatId(),
                    satellite.getEffectiveType(),
                    history
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/norad/{noradCatId}")
    public ResponseEntity<SatelliteDTO> getSatelliteByNoradId(@PathVariable Long noradCatId) {
        return satelliteRepository.findByNoradCatId(noradCatId)
            .map(satellite -> {
                OrbitalParameters latestParams = orbitalParametersRepository
                    .findTopBySatelliteOrderByFetchedAtDesc(satellite);
                OrbitalParametersDTO paramsDTO = latestParams != null
                    ? OrbitalParametersDTO.fromEntity(latestParams)
                    : null;
                return ResponseEntity.ok(SatelliteDTO.fromEntity(satellite, paramsDTO));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/latest-parameters")
    public ResponseEntity<OrbitalParametersDTO> getLatestParameters(@PathVariable Long id) {
        Optional<Satellite> satelliteOpt = satelliteRepository.findById(id);
        if (satelliteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        OrbitalParameters latestParams = orbitalParametersRepository
            .findTopBySatelliteOrderByFetchedAtDesc(satelliteOpt.get());

        if (latestParams != null) {
            return ResponseEntity.ok(OrbitalParametersDTO.fromEntity(latestParams));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/search-by-type")
    public ResponseEntity<List<SatelliteDTO>> searchByType(@RequestParam String type,
                                                           @RequestParam(name = "pendingOnly", defaultValue = "false") boolean pendingOnly) {
        Map<Long, OrbitalParameters> latestBySatelliteId = loadLatestParametersBySatelliteId();

        List<SatelliteDTO> results = satelliteRepository.findAll().stream()
            .filter(satellite -> !pendingOnly || isPendingClassification(satellite))
            .filter(satellite -> satellite.getEffectiveType() != null &&
                satellite.getEffectiveType().equalsIgnoreCase(type))
            .map(satellite -> {
                OrbitalParameters latestParams = latestBySatelliteId.get(satellite.getId());
                OrbitalParametersDTO paramsDTO = latestParams != null
                    ? OrbitalParametersDTO.fromEntity(latestParams)
                    : null;
                return SatelliteDTO.fromEntity(satellite, paramsDTO);
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }

    @GetMapping("/groups-stats")
    public ResponseEntity<Map<String, Object>> getGroupsStats(@RequestParam(name = "pendingOnly", defaultValue = "false") boolean pendingOnly) {
        List<Satellite> allSatellites = satelliteRepository.findAll().stream()
            .filter(satellite -> !pendingOnly || isPendingClassification(satellite))
            .collect(Collectors.toList());

        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("stations", allSatellites.stream().filter(s -> "stations".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("starlink", allSatellites.stream().filter(s -> "starlink".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("oneweb", allSatellites.stream().filter(s -> "oneweb".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("iridium-NEXT", allSatellites.stream().filter(s -> "iridium-NEXT".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("spire", allSatellites.stream().filter(s -> "spire".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("gps-ops", allSatellites.stream().filter(s -> "gps-ops".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("galileo", allSatellites.stream().filter(s -> "galileo".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("glonass-ops", allSatellites.stream().filter(s -> "glonass-ops".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("beidou", allSatellites.stream().filter(s -> "beidou".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("sbas", allSatellites.stream().filter(s -> "sbas".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("science", allSatellites.stream().filter(s -> "science".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("weather", allSatellites.stream().filter(s -> "weather".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("planet", allSatellites.stream().filter(s -> "planet".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("radar", allSatellites.stream().filter(s -> "radar".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("geo", allSatellites.stream().filter(s -> "geo".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("amateur", allSatellites.stream().filter(s -> "amateur".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("cubesat", allSatellites.stream().filter(s -> "cubesat".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("education", allSatellites.stream().filter(s -> "education".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("engineering", allSatellites.stream().filter(s -> "engineering".equalsIgnoreCase(s.getEffectiveType())).count());
        stats.put("military", allSatellites.stream().filter(s -> "military".equalsIgnoreCase(s.getEffectiveType())).count());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stats", stats);
        response.put("total", (long) allSatellites.size());

        return ResponseEntity.ok(response);
    }

    private Map<Long, OrbitalParameters> loadLatestParametersBySatelliteId() {
        return orbitalParametersRepository.findLatestForAllSatellites().stream()
            .filter(parameters -> parameters.getSatellite() != null && parameters.getSatellite().getId() != null)
            .collect(Collectors.toMap(
                parameters -> parameters.getSatellite().getId(),
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private boolean isPendingClassification(Satellite satellite) {
        String rawType = satellite.getObjectTypeRaw();
        return rawType == null || rawType.isBlank() || "UNKNOWN".equalsIgnoreCase(rawType);
    }
}
