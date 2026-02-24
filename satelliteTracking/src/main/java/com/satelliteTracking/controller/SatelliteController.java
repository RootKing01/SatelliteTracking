package com.satelliteTracking.controller;

import com.satelliteTracking.dto.OrbitalParametersDTO;
import com.satelliteTracking.dto.SatelliteDTO;
import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.dto.SatelliteWithHistoryDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.model.TelegramSubscription;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.service.SatellitePassService;
import com.satelliteTracking.service.TelegramNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/satellites")
public class SatelliteController {

    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;
    private final SatellitePassService satellitePassService;
    private final TelegramNotificationService telegramNotificationService;

    public SatelliteController(SatelliteRepository satelliteRepository, 
                               OrbitalParametersRepository orbitalParametersRepository,
                               SatellitePassService satellitePassService,
                               TelegramNotificationService telegramNotificationService) {
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
        this.satellitePassService = satellitePassService;
        this.telegramNotificationService = telegramNotificationService;
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

    /**
     * Cerca satelliti per tipo/gruppo
     * Esempi: stations, starlink, science, weather, geo, gps-ops, etc.
     * 
     * @param type tipo di satellite
     * @return lista satelliti del tipo specificato
     */
    @GetMapping("/search-by-type")
    public ResponseEntity<List<SatelliteDTO>> searchByType(@RequestParam String type) {
        List<SatelliteDTO> results = satelliteRepository.findAll().stream()
            .filter(satellite -> satellite.getSatelliteType() != null && 
                               satellite.getSatelliteType().equalsIgnoreCase(type))
            .map(satellite -> {
                OrbitalParameters latestParams = orbitalParametersRepository
                    .findTopBySatelliteOrderByFetchedAtDesc(satellite);
                OrbitalParametersDTO paramsDTO = latestParams != null 
                    ? OrbitalParametersDTO.fromEntity(latestParams) 
                    : null;
                return SatelliteDTO.fromEntity(satellite, paramsDTO);
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(results);
    }

    /**
     * Lista i satelliti disponibili per ogni gruppo
     * 
     * @return mappa con count dei satelliti per gruppo
     */
    @GetMapping("/groups-stats")
    public ResponseEntity<?> getGroupsStats() {
        List<Satellite> allSatellites = satelliteRepository.findAll();
        
        // Crea mappa di statistiche per gruppo usando il campo satelliteType
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("stations", allSatellites.stream().filter(s -> "stations".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("starlink", allSatellites.stream().filter(s -> "starlink".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("oneweb", allSatellites.stream().filter(s -> "oneweb".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("iridium-NEXT", allSatellites.stream().filter(s -> "iridium-NEXT".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("spire", allSatellites.stream().filter(s -> "spire".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("gps-ops", allSatellites.stream().filter(s -> "gps-ops".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("galileo", allSatellites.stream().filter(s -> "galileo".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("glonass-ops", allSatellites.stream().filter(s -> "glonass-ops".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("beidou", allSatellites.stream().filter(s -> "beidou".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("sbas", allSatellites.stream().filter(s -> "sbas".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("science", allSatellites.stream().filter(s -> "science".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("weather", allSatellites.stream().filter(s -> "weather".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("planet", allSatellites.stream().filter(s -> "planet".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("radar", allSatellites.stream().filter(s -> "radar".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("geo", allSatellites.stream().filter(s -> "geo".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("amateur", allSatellites.stream().filter(s -> "amateur".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("cubesat", allSatellites.stream().filter(s -> "cubesat".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("education", allSatellites.stream().filter(s -> "education".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("engineering", allSatellites.stream().filter(s -> "engineering".equalsIgnoreCase(s.getSatelliteType())).count());
        stats.put("military", allSatellites.stream().filter(s -> "military".equalsIgnoreCase(s.getSatelliteType())).count());
        
        // Crea risposta
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stats", stats);
        response.put("total", (long) allSatellites.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Trova tutti i satelliti visibili nelle prossime ore che passano vicino
     * Usa posizione predefinita (San Marcellino) ed elevazione minima 30°
     * BONUS: Invia notifiche Telegram agli utenti registrati se trova passaggi
     * 
     * @param hours ore da controllare (default 6)
     * @return lista di pass ordinati per tempo
     */
    @GetMapping("/upcoming-passes")
    public ResponseEntity<List<SatellitePassDTO>> getUpcomingPasses(@RequestParam(defaultValue = "6") int hours) {
        List<SatellitePassDTO> passes = satellitePassService.findVisibleUpcomingPasses(hours, 30.0);
        
        // Se trova passaggi, invia notifiche Telegram agli utenti registrati
        if (!passes.isEmpty()) {
            try {
                List<TelegramSubscription> subscriptions = telegramNotificationService.getAllSubscriptions();
                LocalDateTime now = LocalDateTime.now();
                
                for (TelegramSubscription sub : subscriptions) {
                    if (!sub.getNotificationsEnabled()) continue;
                    
                    // Invia notifica per il primo passaggio (evita spam)
                    SatellitePassDTO firstPass = passes.get(0);
                    long minutesSinceLast = java.time.temporal.ChronoUnit.MINUTES
                        .between(sub.getLastNotificationSent(), now);
                    
                    if (minutesSinceLast >= 30) {
                        telegramNotificationService.sendNotificationToUser(
                            sub,
                            firstPass.satelliteName(),
                            firstPass.riseTime(),
                            firstPass.maxElevation(),
                            firstPass.estimatedMagnitude()
                        );
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Errore invio notifiche: " + e.getMessage());
            }
        }
        
        return ResponseEntity.ok(passes);
    }

    /**
     * Trova tutti i satelliti visibili nelle prossime ore che passano vicino
     * Con parametri custom (posizione e elevazione minima)
     * 
     * @param hours ore da controllare
     * @param minElevation elevazione minima in gradi
     * @param latitude latitudine osservatore
     * @param longitude longitudine osservatore
     * @param altitude altitudine osservatore in metri
     * @return lista di pass ordinati per tempo
     */
    @GetMapping("/upcoming-passes/custom")
    public ResponseEntity<List<SatellitePassDTO>> getUpcomingPassesCustom(
            @RequestParam(defaultValue = "6") int hours,
            @RequestParam(defaultValue = "30") double minElevation,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") double altitude) {
        
        ObserverLocation customLocation = new ObserverLocation(latitude, longitude, altitude);
        List<SatellitePassDTO> passes = satellitePassService.findVisibleUpcomingPasses(hours, minElevation, customLocation);
        return ResponseEntity.ok(passes);
    }

    /**
     * Trova satelliti visibili con filtri avanzati: condizione osservazione + magnitudine
     * Usa posizione predefinita (San Marcellino)
     * BONUS: Invia notifiche Telegram agli utenti registrati se trova passaggi
     * 
     * @param hours ore da controllare (default 6)
     * @param minElevation elevazione minima in gradi (default 30)
     * @param observingCondition "night", "twilight", o "any" (default "any")
     * @param maxMagnitude magnitudine massima - più basso = più luminoso (default 6.0)
     * @return lista di pass ordinati per tempo
     */
    @GetMapping("/upcoming-passes/filtered")
    public ResponseEntity<List<SatellitePassDTO>> getUpcomingPassesFiltered(
            @RequestParam(defaultValue = "6") int hours,
            @RequestParam(defaultValue = "30") double minElevation,
            @RequestParam(defaultValue = "any") String observingCondition,
            @RequestParam(defaultValue = "6.0") double maxMagnitude) {
        
        List<SatellitePassDTO> passes = satellitePassService.findVisibleUpcomingPasses(hours, minElevation, 
                                                                                       observingCondition, maxMagnitude);
        
        // Se trova passaggi, invia notifiche Telegram agli utenti registrati
        if (!passes.isEmpty()) {
            try {
                List<TelegramSubscription> subscriptions = telegramNotificationService.getAllSubscriptions();
                LocalDateTime now = LocalDateTime.now();
                
                for (TelegramSubscription sub : subscriptions) {
                    if (!sub.getNotificationsEnabled()) continue;
                    
                    // Invia notifica per il primo passaggio (evita spam)
                    SatellitePassDTO firstPass = passes.get(0);
                    long minutesSinceLast = java.time.temporal.ChronoUnit.MINUTES
                        .between(sub.getLastNotificationSent(), now);
                    
                    if (minutesSinceLast >= 30) {
                        telegramNotificationService.sendNotificationToUser(
                            sub,
                            firstPass.satelliteName(),
                            firstPass.riseTime(),
                            firstPass.maxElevation(),
                            firstPass.estimatedMagnitude()
                        );
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Errore invio notifiche: " + e.getMessage());
            }
        }
        
        return ResponseEntity.ok(passes);
    }

    /**
     * Trova satelliti visibili con filtri avanzati (posizione custom)
     * 
     * @param hours ore da controllare
     * @param minElevation elevazione minima in gradi
     * @param observingCondition "night", "twilight", o "any"
     * @param maxMagnitude magnitudine massima
     * @param latitude latitudine osservatore
     * @param longitude longitudine osservatore
     * @param altitude altitudine osservatore in metri
     * @return lista di pass ordinati per tempo
     */
    @GetMapping("/upcoming-passes/filtered/custom")
    public ResponseEntity<List<SatellitePassDTO>> getUpcomingPassesFilteredCustom(
            @RequestParam(defaultValue = "6") int hours,
            @RequestParam(defaultValue = "30") double minElevation,
            @RequestParam(defaultValue = "any") String observingCondition,
            @RequestParam(defaultValue = "6.0") double maxMagnitude,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") double altitude) {
        
        ObserverLocation customLocation = new ObserverLocation(latitude, longitude, altitude);
        List<SatellitePassDTO> passes = satellitePassService.findVisibleUpcomingPasses(hours, minElevation, 
                                                                                       customLocation, observingCondition, maxMagnitude);
        return ResponseEntity.ok(passes);
    }

    /**
     * Calcola i passaggi visibili in una fascia oraria specifica
     * 
     * @param hours fascia oraria da ora (es. 3 = prossime 3 ore)
     * @param latitude latitudine osservatore (default: 41.01 - San Marcellino)
     * @param longitude longitudine osservatore (default: 14.42 - San Marcellino)
     * @param altitude altitudine osservatore in metri (default: 100)
     * @param minElevation elevazione minima in gradi (default: 30)
     * @return lista dei passaggi visibili ordinati per ora
     */
    @GetMapping("/passes/upcoming")
    public ResponseEntity<?> getUpcomingPasses(
            @RequestParam(value = "hours", defaultValue = "3") Integer hours,
            @RequestParam(value = "latitude", defaultValue = "41.01") Double latitude,
            @RequestParam(value = "longitude", defaultValue = "14.42") Double longitude,
            @RequestParam(value = "altitude", defaultValue = "100") Integer altitude,
            @RequestParam(value = "minElevation", defaultValue = "30.0") Double minElevation) {
        
        try {
            // Validazione input
            if (hours == null || hours <= 0 || hours > 24) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "hours deve essere tra 1 e 24", "received", hours)
                );
            }
            
            if (latitude == null || latitude < -90 || latitude > 90) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "latitude deve essere tra -90 e 90", "received", latitude)
                );
            }
            
            if (longitude == null || longitude < -180 || longitude > 180) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "longitude deve essere tra -180 e 180", "received", longitude)
                );
            }
            
            if (minElevation == null || minElevation < 0 || minElevation > 90) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "minElevation deve essere tra 0 e 90", "received", minElevation)
                );
            }
            
            // Crea location observer
            ObserverLocation observer = new ObserverLocation(
                latitude,
                longitude,
                altitude,
                String.format("Custom (%.2f, %.2f, %dm)", latitude, longitude, altitude)
            );
            
            // Calcola passaggi
            List<SatellitePassDTO> passes = satellitePassService.findVisibleUpcomingPasses(
                hours,
                minElevation,
                observer,
                "any",  // qualsiasi condizione di osservazione
                6.0    // magnitudine massima
            );
            
            // Prepara risposta dettagliata
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("query", Map.of(
                "hours", hours,
                "observer", Map.of(
                    "latitude", latitude,
                    "longitude", longitude,
                    "altitude", altitude
                ),
                "minElevation", minElevation + "°"
            ));
            response.put("totalPasses", passes.size());
            response.put("passes", passes);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "error", "Errore durante il calcolo dei passaggi",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                )
            );
        }
    }

    /**
     * Ottiene lo stato del cache dei passaggi
     * 
     * @return informazioni sul cache (numero entries, TTL, dettagli)
     */
    @GetMapping("/cache-status")
    public ResponseEntity<?> getCacheStatus() {
        return ResponseEntity.ok(satellitePassService.getCacheStatus());
    }

    /**
     * Pulisce il cache dei passaggi visibili
     * Utile se vuoi rigenerare i dati
     * 
     * @return messaggio di conferma
     */
    @DeleteMapping("/cache")
    public ResponseEntity<?> clearCache() {
        satellitePassService.clearPassesCache();
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "Cache pulito con successo");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}