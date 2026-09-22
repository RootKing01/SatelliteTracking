package com.satelliteTracking.controller;

import com.satelliteTracking.dto.OrbitalParametersDTO;
import com.satelliteTracking.dto.SatelliteDTO;
import com.satelliteTracking.dto.SatelliteWithHistoryDTO;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.util.SatelliteTypeNormalizer;
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
            .filter(satellite -> !pendingOnly || SatelliteTypeNormalizer.isPendingClassification(satellite))
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

        String requested = SatelliteTypeNormalizer.canonicalizeType(type);

        List<SatelliteDTO> results = satelliteRepository.findAll().stream()
            .filter(satellite -> !pendingOnly || SatelliteTypeNormalizer.isPendingClassification(satellite))
            .filter(satellite -> {
                String norm = SatelliteTypeNormalizer.canonicalizeSatelliteType(satellite);
                return norm != null && norm.equalsIgnoreCase(requested);
            })
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
            .filter(satellite -> !pendingOnly || SatelliteTypeNormalizer.isPendingClassification(satellite))
            .collect(Collectors.toList());

        Map<String, Long> stats = allSatellites.stream()
            .map(SatelliteTypeNormalizer::canonicalizeSatelliteType)
            .filter(type -> type != null && !type.isBlank())
            .collect(Collectors.groupingBy(
                Function.identity(),
                LinkedHashMap::new,
                Collectors.counting()
            ));

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
}
