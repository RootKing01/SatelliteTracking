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
import java.util.List;
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
     * Stima la magnitudine visiva del satellite
     */
    private double estimateMagnitude(double distanceKm, double altitudeKm, boolean isSunlit) {
        if (!isSunlit) {
            return 10.0; // Non visibile
        }
        
        // Formula semplificata: magnitudine aumenta con la distanza
        // ISS tipicamente tra -3 e 2
        double baseMagnitude = 0.0;
        double distanceFactor = (distanceKm - 400) / 1000.0;
        
        return baseMagnitude + distanceFactor * 2.0;
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
}
