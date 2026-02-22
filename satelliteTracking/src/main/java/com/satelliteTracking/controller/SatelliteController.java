package com.satelliteTracking.controller;

import com.satelliteTracking.dto.OrbitalParametersDTO;
import com.satelliteTracking.dto.SatelliteDTO;
import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.dto.SatelliteWithHistoryDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.service.SatellitePassService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/satellites")
public class SatelliteController {

    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;
    private final SatellitePassService satellitePassService;

    public SatelliteController(SatelliteRepository satelliteRepository, 
                               OrbitalParametersRepository orbitalParametersRepository,
                               SatellitePassService satellitePassService) {
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
        this.satellitePassService = satellitePassService;
    }

    /**
     * Ottiene tutti i satelliti con i loro parametri orbitali più recenti
     */
    @GetMapping
    public List<SatelliteDTO> getAllSatellites() {
        return satelliteRepository.findAll().stream()
            .map(satellite -> {
                OrbitalParameters latestParams = orbitalParametersRepository
                    .findTopBySatelliteOrderByFetchedAtDesc(satellite);
                OrbitalParametersDTO paramsDTO = latestParams != null 
                    ? OrbitalParametersDTO.fromEntity(latestParams) 
                    : null;
                return SatelliteDTO.fromEntity(satellite, paramsDTO);
            })
            .collect(Collectors.toList());
    }

    /**
     * Ottiene un satellite specifico con i parametri orbitali più recenti
     */
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

    /**
     * Ottiene lo storico completo dei parametri orbitali per un satellite
     */
    @GetMapping("/{id}/orbital-history")
    public ResponseEntity<SatelliteWithHistoryDTO> getOrbitalHistory(@PathVariable Long id) {
        return satelliteRepository.findById(id)
            .map(satellite -> {
                // Ordina lo storico per data di fetch (più recente prima)
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
                    history
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cerca un satellite tramite NORAD Catalog ID
     */
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

    /**
     * Ottiene solo i parametri orbitali più recenti per un satellite
     */
    @GetMapping("/{id}/latest-parameters")
    public ResponseEntity<OrbitalParametersDTO> getLatestParameters(@PathVariable Long id) {
        java.util.Optional<Satellite> satelliteOpt = satelliteRepository.findById(id);
        if (satelliteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Satellite satellite = satelliteOpt.get();
        OrbitalParameters latestParams = orbitalParametersRepository
            .findTopBySatelliteOrderByFetchedAtDesc(satellite);
        
        if (latestParams != null) {
            return ResponseEntity.ok(OrbitalParametersDTO.fromEntity(latestParams));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Calcola i prossimi passaggi visibili di un satellite sopra San Marcellino
     * 
     * @param id ID del satellite
     * @param hours numero di ore nel futuro da analizzare (default: 24)
     * @return lista dei passaggi visibili
     */
    @GetMapping("/{id}/passes")
    public ResponseEntity<List<SatellitePassDTO>> getSatellitePasses(
            @PathVariable Long id,
            @RequestParam(defaultValue = "24") int hours) {
        
        List<SatellitePassDTO> passes = satellitePassService.calculatePasses(id, hours);
        return ResponseEntity.ok(passes);
    }

    /**
     * Calcola i prossimi passaggi visibili di un satellite sopra una posizione personalizzata
     * 
     * @param id ID del satellite
     * @param lat latitudine dell'osservatore
     * @param lon longitudine dell'osservatore
     * @param alt altitudine dell'osservatore in metri (default: 0)
     * @param hours numero di ore nel futuro da analizzare (default: 24)
     * @return lista dei passaggi visibili
     */
    @GetMapping("/{id}/passes/custom")
    public ResponseEntity<List<SatellitePassDTO>> getSatellitePassesCustomLocation(
            @PathVariable Long id,
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "0") double alt,
            @RequestParam(defaultValue = "24") int hours) {
        
        ObserverLocation customLocation = new ObserverLocation(lat, lon, alt);
        List<SatellitePassDTO> passes = satellitePassService.calculatePasses(id, hours, customLocation);
        return ResponseEntity.ok(passes);
    }

    /**
     * Ottiene la posizione predefinita dell'osservatore (San Marcellino)
     */
    @GetMapping("/observer-location")
    public ResponseEntity<ObserverLocation> getDefaultObserverLocation() {
        return ResponseEntity.ok(satellitePassService.getDefaultLocation());
    }
}