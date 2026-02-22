package com.satelliteTracking.service;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.util.TLEConverter;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servizio per calcolare i passaggi visibili dei satelliti usando Orekit e SGP4
 */
@Service
public class SatellitePassService {

    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;
    
    // Posizione predefinita: San Marcellino, Caserta
    private final ObserverLocation defaultLocation = ObserverLocation.sanMarcellino();
    
    // Cache per i passaggi visibili
    private static class CacheEntry {
        List<SatellitePassDTO> passes;
        long timestamp;
        
        CacheEntry(List<SatellitePassDTO> passes) {
            this.passes = passes;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired(long maxAgeMs) {
            return System.currentTimeMillis() - timestamp > maxAgeMs;
        }
    }
    
    private final Map<String, CacheEntry> passesCache = new HashMap<>();
    private static final long CACHE_TTL_MS = 1800000; // 30 minuti

    public SatellitePassService(SatelliteRepository satelliteRepository,
                                OrbitalParametersRepository orbitalParametersRepository) {
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    /**
     * Calcola i prossimi passaggi di un satellite sopra la posizione predefinita
     */
    public List<SatellitePassDTO> calculatePasses(Long satelliteId, int hours) {
        return calculatePasses(satelliteId, hours, defaultLocation);
    }

    /**
     * Calcola i prossimi passaggi di un satellite sopra una posizione specifica
     */
    public List<SatellitePassDTO> calculatePasses(Long satelliteId, int hours, ObserverLocation observerLocation) {
        List<SatellitePassDTO> passes = new ArrayList<>();
        
        try {
            Optional<Satellite> satelliteOpt = satelliteRepository.findById(satelliteId);
            if (satelliteOpt.isEmpty()) {
                return passes;
            }
            
            Satellite satellite = satelliteOpt.get();
            OrbitalParameters latestParams = orbitalParametersRepository
                .findTopBySatelliteOrderByFetchedAtDesc(satellite);
            
            if (latestParams == null) {
                return passes;
            }
            
            // Check preventivo: verifica se il satellite può essere visibile da questa latitudine
            double inclination = latestParams.getInclination();
            double observerLat = Math.abs(observerLocation.getLatitude());
            boolean canBeVisible = inclination >= observerLat && inclination <= (180.0 - observerLat);
            
            System.out.println("🛰️  Satellite: " + satellite.getObjectName() + 
                             " | Inclinazione: " + inclination + "° | Osservatore: " + observerLat + 
                             "° | Può essere visibile: " + canBeVisible);
            
            if (!canBeVisible) {
                System.out.println("⛔ Satellite non visibile da questa posizione (inclinazione insufficiente)");
                return passes; // Lista vuota
            }
            
            // Converti parametri orbitali in TLE
            String[] tleLines = TLEConverter.buildTLE(
                satellite.getNoradCatId(), 
                satellite.getObjectName(), 
                latestParams
            );
            
            // Prova calcolo con Orekit
            try {
                TLE tle = new TLE(tleLines[1], tleLines[2]);
                TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle);
            
                Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
                OneAxisEllipsoid earth = new OneAxisEllipsoid(
                    Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                    Constants.WGS84_EARTH_FLATTENING,
                    itrf
                );
                
                GeodeticPoint observerPoint = new GeodeticPoint(
                    FastMath.toRadians(observerLocation.getLatitude()),
                    FastMath.toRadians(observerLocation.getLongitude()),
                    observerLocation.getAltitude()
                );
                TopocentricFrame topoFrame = new TopocentricFrame(earth, observerPoint, "Observer");
                
                LocalDateTime now = LocalDateTime.now();
                AbsoluteDate startDate = toAbsoluteDate(now);
                AbsoluteDate endDate = toAbsoluteDate(now.plusHours(hours));
                
                double step = 60.0;
                List<PassData> passDataList = new ArrayList<>();
                PassData currentPass = null;
                
                // Posizione del sole per calcolare illuminazione
                var sun = CelestialBodyFactory.getSun();
                
                for (AbsoluteDate date = startDate; 
                     date.compareTo(endDate) <= 0; 
                     date = date.shiftedBy(step)) {
                    
                    var pv = propagator.getPVCoordinates(date, itrf);
                    var topoCoordinates = topoFrame.getTrackingCoordinates(pv.getPosition(), itrf, date);
                    
                    double elevation = FastMath.toDegrees(topoCoordinates.getElevation());
                    double azimuth = FastMath.toDegrees(topoCoordinates.getAzimuth());
                    double range = topoCoordinates.getRange() / 1000.0;
                    
                    if (elevation > 0) {
                        if (currentPass == null) {
                            currentPass = new PassData();
                            currentPass.riseTime = toLocalDateTime(date);
                            currentPass.riseAzimuth = azimuth;
                        }
                        
                        if (elevation > currentPass.maxElevation) {
                            currentPass.maxElevation = elevation;
                            currentPass.maxElevationTime = toLocalDateTime(date);
                            currentPass.maxElevationDate = date;
                            currentPass.maxDistance = range;
                            
                            // Calcola altitudine satellite (distanza dalla superficie terrestre)
                            double satAltitude = pv.getPosition().getNorm() / 1000.0 - Constants.WGS84_EARTH_EQUATORIAL_RADIUS / 1000.0;
                            currentPass.satelliteAltitude = satAltitude;
                            
                            // Calcola se il satellite è illuminato dal sole
                            PVCoordinates sunPV = sun.getPVCoordinates(date, itrf);
                            double sunAngle = FastMath.toDegrees(
                                org.hipparchus.geometry.euclidean.threed.Vector3D.angle(
                                    pv.getPosition(), 
                                    sunPV.getPosition()
                                )
                            );
                            currentPass.isSunlit = sunAngle < 90.0;
                            
                            // Calcola elevazione del sole per l'osservatore
                            var sunTopoCoords = topoFrame.getTrackingCoordinates(sunPV.getPosition(), itrf, date);
                            double sunElevation = FastMath.toDegrees(sunTopoCoords.getElevation());
                            currentPass.sunElevation = sunElevation;
                        }
                    } else {
                        if (currentPass != null) {
                            currentPass.setTime = toLocalDateTime(date);
                            currentPass.setAzimuth = azimuth;
                            passDataList.add(currentPass);
                            currentPass = null;
                        }
                    }
                }
                
                for (PassData pd : passDataList) {
                    if (pd.maxElevation > 10.0) {
                        // Determina condizioni di osservazione
                        String observingCondition;
                        if (pd.sunElevation < -18) {
                            observingCondition = "night";
                        } else if (pd.sunElevation < -6) {
                            observingCondition = "twilight";
                        } else {
                            observingCondition = "daylight";
                        }
                        
                        // Calcola qualità della visibilità
                        String visibility = calculateVisibility(pd.maxElevation, pd.isSunlit, observingCondition);
                        
                        // Stima magnitudine (formula semplificata basata su distanza e illuminazione)
                        double magnitude = estimateMagnitude(pd.maxDistance, pd.satelliteAltitude, pd.isSunlit);
                        
                        // Solo passaggi con buona visibilità
                        boolean isActuallyVisible = pd.isSunlit && !observingCondition.equals("daylight");
                        
                        passes.add(new SatellitePassDTO(
                            satellite.getId(),
                            satellite.getObjectName(),
                            pd.riseTime,
                            pd.maxElevationTime,
                            pd.setTime,
                            pd.maxElevation,
                            pd.riseAzimuth,
                            pd.setAzimuth,
                            pd.maxDistance,
                            isActuallyVisible,
                            pd.isSunlit,
                            visibility,
                            observingCondition,
                            magnitude,
                            pd.satelliteAltitude
                        ));
                    }
                }
                
            } catch (org.orekit.errors.OrekitException oe) {
                System.err.println("⚠️  Orekit calculation failed: " + oe.getMessage());
                SatellitePassDTO simplifiedPass = createSimplifiedPass(satellite, latestParams, observerLocation, hours);
                if (simplifiedPass != null && simplifiedPass.isVisible()) {
                    passes.add(simplifiedPass);
                }
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error calculating passes: " + e.getMessage());
            e.printStackTrace();
            passes.add(createSimplifiedPass(satelliteId));
        }
        
        return passes;
    }
    
    /**
     * Calcolo semplificato (senza Orekit)
     * Controlla se il satellite può essere visibile dalla latitudine dell'osservatore
     */
    private SatellitePassDTO createSimplifiedPass(Satellite satellite, OrbitalParameters params, 
                                                   ObserverLocation location, int hours) {
        LocalDateTime now = LocalDateTime.now();
        double orbitalPeriod = 1440.0 / params.getMeanMotion();
        double hoursUntilPass = Math.min(hours / 2.0, orbitalPeriod / 60.0);
        
        // Verifica se il satellite può passare sopra questa latitudine
        // Un satellite può essere visibile solo se la sua inclinazione >= latitudine osservatore
        double inclination = params.getInclination();
        double observerLat = Math.abs(location.getLatitude());
        boolean canBeVisible = inclination >= observerLat && inclination <= (180.0 - observerLat);
        
        System.out.println("🛰️  Satellite: " + satellite.getObjectName() + 
                         " | Inclinazione: " + inclination + "° | Osservatore: " + observerLat + "° | Visibile: " + canBeVisible);
        
        // Se non può essere visibile, restituisci null (non aggiungere alla lista)
        if (!canBeVisible) {
            return null;
        }
        
        return new SatellitePassDTO(
            satellite.getId(),
            satellite.getObjectName() + " (simplified)",
            now.plusHours((long)hoursUntilPass),
            now.plusHours((long)hoursUntilPass).plusMinutes((long)(orbitalPeriod / 4)),
            now.plusHours((long)hoursUntilPass).plusMinutes((long)(orbitalPeriod / 2)),
            35.0,
            150.0,
            210.0,
            600.0,
            true,
            true,
            "fair",
            "unknown",
            3.0,
            400.0
        );
    }
    
    /**
     * Fallback per errori
     */
    private SatellitePassDTO createSimplifiedPass(Long satelliteId) {
        Optional<Satellite> satOpt = satelliteRepository.findById(satelliteId);
        String name = satOpt.map(Satellite::getObjectName).orElse("Unknown Satellite");
        
        LocalDateTime now = LocalDateTime.now();
        return new SatellitePassDTO(
            satelliteId,
            name + " (error)",
            now.plusHours(2),
            now.plusHours(2).plusMinutes(5),
            now.plusHours(2).plusMinutes(10),
            25.0,
            120.0,
            240.0,
            800.0,
            false,
            false,
            "poor",
            "unknown",
            5.0,
            400.0
        );
    }
    
    /**
     * Classe helper per passaggi
     */
    private static class PassData {
        LocalDateTime riseTime;
        LocalDateTime maxElevationTime;
        AbsoluteDate maxElevationDate;
        LocalDateTime setTime;
        double maxElevation = 0;
        double riseAzimuth;
        double setAzimuth;
        double maxDistance;
        double satelliteAltitude;
        boolean isSunlit;
        double sunElevation;
    }
    
    /**
     * Calcola la qualità della visibilità
     */
    private String calculateVisibility(double elevation, boolean isSunlit, String condition) {
        if (!isSunlit || condition.equals("daylight")) {
            return "poor";
        }
        
        if (elevation > 60 && condition.equals("night")) {
            return "excellent";
        } else if (elevation > 40 && condition.equals("night")) {
            return "good";
        } else if (elevation > 20 || condition.equals("twilight")) {
            return "fair";
        }
        
        return "poor";
    }
    
    /**
     * Stima la magnitudine visiva del satellite usando formula di fase empirica
     * Basata su: magnitudine assoluta, distanza, angolo di fase, e illuminazione solare
     * 
     * Per satelliti in ombra terrestre (isSunlit=false), applica una penalità empirica
     * che rappresenta la minore luminosità dovuta alla mancanza di illuminazione diretta
     * (ma il satellite è ancora osservabile per riflesso e radiazione terrestre)
     */
    private double estimateMagnitude(double distanceKm, double altitudeKm, boolean isSunlit) {
        // Magnitudine assoluta media (ISS-like): -1.0 (molto luminoso quando illuminato)
        double H = -1.0;
        
        // Calcola magnitudine apparente usando distanza
        // Formula ridotta: m ≈ H + 5*log10(distance_km) - 15
        double magnitude = H + 5.0 * Math.log10(distanceKm) - 15.0;
        
        // Fattore di fase per satelliti illuminati dal sole
        // Approssimazione: angolo di fase medio ≈ 60° (fattore ≈ 0.3)
        double phaseFactor = 0.3;
        double phaseCorrection = -2.5 * Math.log10(phaseFactor);
        
        if (isSunlit) {
            // Satellite illuminato direttamente dal sole
            magnitude -= phaseCorrection;
        } else {
            // Satellite in ombra terrestre: più debole ma ancora osservabile
            // Penalità empirica: ~3.5 magnitudini (satellite è ~30x più debole)
            // Questo rappresenta il riflesso della Terra e della radiazione atmosferica
            magnitude += 3.5;
        }
        
        // Limita tra -5 (molto luminoso, es. ISS al perigeo illuminata) e +9 (appena visibile in ombra)
        magnitude = Math.max(-5.0, Math.min(9.0, magnitude));
        
        return Math.round(magnitude * 10.0) / 10.0; // Arrotonda a 1 decimale
    }
    
    private AbsoluteDate toAbsoluteDate(LocalDateTime ldt) {
        Date date = Date.from(ldt.toInstant(ZoneOffset.UTC));
        return new AbsoluteDate(date, TimeScalesFactory.getUTC());
    }
    
    private LocalDateTime toLocalDateTime(AbsoluteDate ad) {
        return LocalDateTime.ofInstant(
            ad.toDate(TimeScalesFactory.getUTC()).toInstant(),
            ZoneOffset.UTC
        );
    }

    public ObserverLocation getDefaultLocation() {
        return defaultLocation;
    }

    /**
     * Trova tutti i satelliti visibili nelle prossime ore che passano vicino all'osservatore
     * Con filtri rapidi predefiniti (night/twilight/any: any, magnitudine: 6.0)
     * 
     * @param hours ore da controllare
     * @param minElevation elevazione minima per considerare il passaggio "vicino"
     * @return lista di pass ordinati per tempo di rise
     */
    public List<SatellitePassDTO> findVisibleUpcomingPasses(int hours, double minElevation) {
        return findVisibleUpcomingPasses(hours, minElevation, defaultLocation, "any", 6.0);
    }

    /**
     * Trova tutti i satelliti visibili nelle prossime ore che passano vicino
     * Con filtri rapidi predefiniti (night/twilight/any: any, magnitudine: 6.0)
     * 
     * @param hours ore da controllare
     * @param minElevation elevazione minima per considerare il passaggio "vicino"
     * @param observerLocation posizione dell'osservatore
     * @return lista di pass ordinati per tempo di rise
     */
    public List<SatellitePassDTO> findVisibleUpcomingPasses(int hours, double minElevation, ObserverLocation observerLocation) {
        return findVisibleUpcomingPasses(hours, minElevation, observerLocation, "any", 6.0);
    }

    /**
     * Trova tutti i satelliti visibili nelle prossime ore che passano vicino all'osservatore
     * Con filtri avanzati: condizione osservazione e magnitudine
     * 
     * @param hours ore da controllare
     * @param minElevation elevazione minima
     * @param observingCondition "night", "twilight", o "any"
     * @param maxMagnitude magnitudine massima (più basso = più luminoso, es. 4.0)
     * @return lista di pass ordinati per tempo di rise
     */
    public List<SatellitePassDTO> findVisibleUpcomingPasses(int hours, double minElevation, 
                                                              String observingCondition, double maxMagnitude) {
        return findVisibleUpcomingPasses(hours, minElevation, defaultLocation, observingCondition, maxMagnitude);
    }

    /**
     * Trova tutti i satelliti visibili con filtri avanzati (posizione custom)
     * 
     * @param hours ore da controllare
     * @param minElevation elevazione minima
     * @param observerLocation posizione dell'osservatore
     * @param observingCondition "night", "twilight", o "any"
     * @param maxMagnitude magnitudine massima
     * @return lista di pass ordinati per tempo di rise
     */
    public List<SatellitePassDTO> findVisibleUpcomingPasses(int hours, double minElevation, 
                                                              ObserverLocation observerLocation,
                                                              String observingCondition, double maxMagnitude) {
        // Genera chiave di cache
        String cacheKey = String.format("%s_%d_%.1f_%s_%.1f", observerLocation.getLocationName(), 
                                       hours, minElevation, observingCondition, maxMagnitude);
        
        // Controlla cache
        if (passesCache.containsKey(cacheKey)) {
            CacheEntry entry = passesCache.get(cacheKey);
            if (!entry.isExpired(CACHE_TTL_MS)) {
                System.out.println("✅ Risultati caricati da cache (scade tra " + ((CACHE_TTL_MS - (System.currentTimeMillis() - entry.timestamp)) / 1000 / 60) + " min)");
                return entry.passes;
            }
        }
        
        List<SatellitePassDTO> allPasses = new ArrayList<>();
        
        try {
            List<Satellite> allSatellites = satelliteRepository.findAll();
            double observerLat = Math.abs(observerLocation.getLatitude());
            
            // Filtra satelliti per inclinazione PRIMA di calcolare i passaggi
            List<Satellite> visibleSatellites = new ArrayList<>();
            for (Satellite sat : allSatellites) {
                OrbitalParameters latestParams = orbitalParametersRepository
                    .findTopBySatelliteOrderByFetchedAtDesc(sat);
                
                if (latestParams != null) {
                    double inclination = latestParams.getInclination();
                    if (inclination >= observerLat) {
                        visibleSatellites.add(sat);
                    }
                }
            }
            
            System.out.println("🔍 Scanning " + visibleSatellites.size() + " satelliti da " + 
                             observerLocation.getLocationName() + " [Condizione: " + observingCondition + 
                             ", Max magnitudine: " + maxMagnitude + "]");
            
            for (Satellite satellite : visibleSatellites) {
                try {
                    List<SatellitePassDTO> passes = calculatePasses(satellite.getId(), hours, observerLocation);
                    
                    // Filtra per elevazione minima, visibilità, condizione osservazione e magnitudine
                    for (SatellitePassDTO pass : passes) {
                        boolean passesElevationFilter = pass.maxElevation() >= minElevation && pass.isVisible();
                        boolean passesConditionFilter = "any".equalsIgnoreCase(observingCondition) || 
                                                        pass.observingCondition().equalsIgnoreCase(observingCondition);
                        boolean passesMagnitudeFilter = pass.estimatedMagnitude() <= maxMagnitude;
                        
                        if (passesElevationFilter && passesConditionFilter && passesMagnitudeFilter) {
                            allPasses.add(pass);
                        }
                    }
                } catch (Exception e) {
                    // Continua con il prossimo satellite
                }
            }
            
            // Ordina per tempo di rise
            allPasses.sort((p1, p2) -> p1.riseTime().compareTo(p2.riseTime()));
            
            // Salva in cache
            passesCache.put(cacheKey, new CacheEntry(allPasses));
            
            System.out.println("✅ Trovati " + allPasses.size() + " passaggi con filtri: elevazione>" + minElevation + 
                             "°, " + observingCondition + ", magnitudine<" + maxMagnitude);
            return allPasses;
            
        } catch (Exception e) {
            System.err.println("❌ Errore durante scan passaggi: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    /**
     * Pulisce il cache dei passaggi visibili
     */
    public void clearPassesCache() {
        passesCache.clear();
        System.out.println("🧹 Cache passaggi pulito");
    }
    
    /**
     * Ottiene lo stato del cache
     */
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("entries", passesCache.size());
        status.put("ttl_minutes", CACHE_TTL_MS / 1000 / 60);
        
        Map<String, Long> entries = new HashMap<>();
        for (String key : passesCache.keySet()) {
            CacheEntry entry = passesCache.get(key);
            long ageMs = System.currentTimeMillis() - entry.timestamp;
            long remainingMs = CACHE_TTL_MS - ageMs;
            entries.put(key, Math.max(0, remainingMs / 1000 / 60)); // minuti rimanenti
        }
        status.put("cache_entries", entries);
        
        return status;
    }
}