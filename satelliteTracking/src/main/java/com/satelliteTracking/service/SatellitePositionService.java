package com.satelliteTracking.service;

import com.satelliteTracking.dto.SatellitePositionDTO;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SatellitePositionService {
    private final PassTimeService passTimeService;
    private final PassPhotometryService passPhotometryService;

    @org.springframework.beans.factory.annotation.Autowired
    public SatellitePositionService(
            PassTimeService passTimeService,
            PassPhotometryService passPhotometryService
    ) {
        this.passTimeService = passTimeService;
        this.passPhotometryService = passPhotometryService;
    }

    public SatellitePositionDTO computeObservation(
            Satellite satellite,
            OrbitalParameters params,
            AbsoluteDate date,
            double observerLat,
            double observerLon,
            double observerAlt
    ) {
        String[] tleLines = com.satelliteTracking.util.TLEConverter.buildTLE(
                satellite.getNoradCatId(),
                satellite.getObjectName(),
                params
        );
        TLE tle = new TLE(tleLines[1], tleLines[2]);
        TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle);
        
        Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING,
                itrf
        );
        
        PVCoordinates pv = propagator.getPVCoordinates(date, itrf);
        GeodeticPoint satPoint = earth.transform(pv.getPosition(), itrf, date);
        
        GeodeticPoint observerPoint = new GeodeticPoint(
                FastMath.toRadians(observerLat),
                FastMath.toRadians(observerLon),
                observerAlt
        );
        TopocentricFrame topoFrame = new TopocentricFrame(earth, observerPoint, "Observer");
        var topoCoords = topoFrame.getTrackingCoordinates(pv.getPosition(), itrf, date);
        
        double elevation = FastMath.toDegrees(topoCoords.getElevation());
        double azimuth = FastMath.toDegrees(topoCoords.getAzimuth());
        double range = topoCoords.getRange() / 1000.0; // km
        
        // Calcolo distanza dal centro della Terra
        double distanceFromEarthCenterKm = pv.getPosition().getNorm() / 1000.0;
        
        // Calcolo velocità
        Vector3D velocity = pv.getVelocity();
        double velocityKmh = velocity.getNorm() * 3.6; // m/s -> km/h
        
        // Calcolo direzione (azimuth della velocità)
        double vx = velocity.getX();
        double vy = velocity.getY();
        double directionDeg = FastMath.toDegrees(FastMath.atan2(vy, vx));
        if (directionDeg < 0) directionDeg += 360.0;
        
        // Mean motion e periodo orbitale dal TLE
        double meanMotion = tle.getMeanMotion() * 60.0; // rev/day -> rev/min
        double orbitalPeriodMinutes = 1440.0 / (tle.getMeanMotion()); // minuti
        double orbitalPeriodHours = orbitalPeriodMinutes / 60.0;
        
        // Altitudine del satellite
        double altitudeKm = satPoint.getAltitude() / 1000.0;
        
        // Calcolo illuminazione solare
        boolean isSunlit = computeSunlitStatus(pv.getPosition(), date, itrf);
        
        // Calcolo angolo di fase (semplificato: assumiamo opposizione se sunlit)
        double phaseAngleDeg = isSunlit ? 30.0 : 150.0;
        
        // Stima magnitudine usando PassPhotometryService
        Double estimatedMagnitude = null;
        Boolean isVisible = null;
        String visibility = null;
        String observingCondition = null;
        
        if (elevation > 0) {
            estimatedMagnitude = passPhotometryService.estimateMagnitude(
                    range, 
                    phaseAngleDeg, 
                    isSunlit, 
                    satellite
            );
            
            // Calcolo visibilità per fasce
            VisibilityAssessment assessment = assessVisibility(
                    estimatedMagnitude,
                    altitudeKm,
                    range,
                    elevation,
                    isSunlit
            );
            
            isVisible = assessment.isVisible;
            visibility = assessment.category;
            observingCondition = assessment.description;
        }
        
        LocalDateTime calculatedAtUtc = passTimeService.toLocalDateTime(
                date, 
                java.time.ZoneOffset.UTC
        );
        
        return new SatellitePositionDTO(
                satellite.getId(),
                satellite.getObjectName(),
                satellite.getSatelliteType(),
                satellite.getObjectId(),
                satellite.getNoradCatId(),
                calculatedAtUtc,
                FastMath.toDegrees(satPoint.getLatitude()),
                FastMath.toDegrees(satPoint.getLongitude()),
                altitudeKm,
                distanceFromEarthCenterKm,
                meanMotion,
                orbitalPeriodMinutes,
                orbitalPeriodHours,
                velocityKmh,
                directionDeg,
                null, // latestOrbitalParameters
                observerLat,
                observerLon,
                observerAlt,
                elevation,
                azimuth,
                range,
                estimatedMagnitude,
                isVisible,
                visibility,
                observingCondition
        );
    }
    
    /**
     * Valuta la visibilità del satellite considerando magnitudine, altitudine,
     * distanza ed elevazione in un sistema a fasce
     */
    private VisibilityAssessment assessVisibility(
            double magnitude,
            double altitudeKm,
            double rangeKm,
            double elevationDeg,
            boolean isSunlit
    ) {
        // Se non è illuminato dal sole, non è visibile
        if (!isSunlit) {
            return new VisibilityAssessment(
                    false,
                    "NOT_VISIBLE",
                    "Satellite in ombra terrestre, non illuminato dal Sole"
            );
        }
        
        // Se l'elevazione è troppo bassa, atmosfera degrada visibilità
        if (elevationDeg < 10) {
            return new VisibilityAssessment(
                    false,
                    "NOT_VISIBLE",
                    String.format("Elevazione troppo bassa (%.1f°), assorbimento atmosferico eccessivo", elevationDeg)
            );
        }
        
        // Calcolo del "visual score" combinando i fattori
        double visualScore = computeVisualScore(magnitude, altitudeKm, rangeKm, elevationDeg);
        
        // Fasce di visibilità basate sullo score
        if (visualScore >= 80) {
            return new VisibilityAssessment(
                    true,
                    "EXCELLENT",
                    String.format("Eccellente: mag %.1f, facilmente visibile a occhio nudo. " +
                            "Altitudine ottimale (~%.0f km) per osservazione.", 
                            magnitude, altitudeKm)
            );
        } else if (visualScore >= 60) {
            return new VisibilityAssessment(
                    true,
                    "VERY_GOOD",
                    String.format("Molto buona: mag %.1f, ben visibile in cieli bui. " +
                            "Altitudine %.0f km, distanza %.0f km.", 
                            magnitude, altitudeKm, rangeKm)
            );
        } else if (visualScore >= 40) {
            return new VisibilityAssessment(
                    true,
                    "GOOD",
                    String.format("Buona: mag %.1f, visibile con attenzione. " +
                            "Richiede cieli relativamente bui.", 
                            magnitude)
            );
        } else if (visualScore >= 25) {
            return new VisibilityAssessment(
                    true,
                    "MODERATE",
                    String.format("Moderata: mag %.1f, visibile solo in condizioni ottimali. " +
                            "L'altitudine elevata (%.0f km) riduce la luminosità apparente.", 
                            magnitude, altitudeKm)
            );
        } else if (visualScore >= 10) {
            return new VisibilityAssessment(
                    true,
                    "POOR",
                    String.format("Scarsa: mag %.1f, al limite della visibilità. " +
                            "Richiede cieli perfetti e occhi adattati al buio.", 
                            magnitude)
            );
        } else {
            return new VisibilityAssessment(
                    false,
                    "NOT_VISIBLE",
                    String.format("Non visibile: mag %.1f troppo debole. " +
                            "Distanza %.0f km eccessiva per osservazione a occhio nudo.", 
                            magnitude, rangeKm)
            );
        }
    }
    
    /**
     * Calcola uno score di visibilità (0-100) basato su multipli fattori
     */
    private double computeVisualScore(
            double magnitude,
            double altitudeKm,
            double rangeKm,
            double elevationDeg
    ) {
        double score = 100.0;
        
        // Penalità per magnitudine crescente
        // mag -4: nessuna penalità
        // mag 0: -20
        // mag 2: -40
        // mag 4: -60
        // mag 6: -80
        score -= (magnitude + 4.0) * 10.0;
        
        // Fattore altitudine: satelliti più bassi sono più luminosi
        // ma la relazione non è lineare
        double altitudeFactor = 0.0;
        if (altitudeKm < 400) {
            // LEO basso: ottimale (Starlink ~550km, ISS ~400km)
            altitudeFactor = 15.0;
        } else if (altitudeKm < 600) {
            // LEO medio: molto buono
            altitudeFactor = 10.0;
        } else if (altitudeKm < 1000) {
            // LEO alto: buono
            altitudeFactor = 5.0;
        } else if (altitudeKm < 2000) {
            // LEO molto alto: penalità moderata
            altitudeFactor = -5.0;
        } else {
            // MEO/GEO: penalità alta
            altitudeFactor = -15.0;
        }
        score += altitudeFactor;
        
        // Fattore distanza dall'osservatore
        // A parità di altitudine, range maggiore = più debole
        if (rangeKm < 500) {
            score += 10.0;
        } else if (rangeKm < 1000) {
            score += 5.0;
        } else if (rangeKm < 2000) {
            score += 0.0;
        } else {
            score -= 10.0;
        }
        
        // Fattore elevazione: più alto è meglio
        if (elevationDeg > 60) {
            score += 15.0; // Quasi allo zenith
        } else if (elevationDeg > 45) {
            score += 10.0;
        } else if (elevationDeg > 30) {
            score += 5.0;
        } else if (elevationDeg > 20) {
            score += 0.0;
        } else {
            score -= (20.0 - elevationDeg) * 2.0; // Penalità progressiva
        }
        
        return Math.max(0.0, Math.min(100.0, score));
    }
    
    /**
     * Determina se il satellite è illuminato dal Sole (semplificato)
     */
    /**
     * Determina se il satellite è illuminato dal Sole (fisica realistica)
     * Usa Orekit per calcolare la posizione del Sole e verifica se il satellite è in ombra.
     */
    private boolean computeSunlitStatus(Vector3D satPosition, AbsoluteDate date, Frame frame) {
        try {
            // Ottieni la posizione del Sole nel frame richiesto
            var sun = org.orekit.bodies.CelestialBodyFactory.getSun();
            Vector3D sunPos = sun.getPVCoordinates(date, frame).getPosition();

            // Vettore dal centro Terra al satellite e al Sole
            Vector3D satVec = satPosition;
            Vector3D sunVec = sunPos;

            // Calcola se il satellite è in ombra (eclisse geometrica)
            // Proietta il satellite sulla linea Sole-Terra
            double satDotSun = Vector3D.dotProduct(satVec, sunVec);
            double sunNormSq = sunVec.getNormSq();
            double proj = satDotSun / sunNormSq;
            Vector3D closestPoint = sunVec.scalarMultiply(proj);
            double distanceToAxis = Vector3D.distance(satVec, closestPoint);

            // Raggio della Terra (approssimato)
            double earthRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

            // Se la distanza satellite-asse Sole-Terra è minore del raggio terrestre e il satellite è tra la Terra e il Sole, è in ombra
            boolean inUmbra = (distanceToAxis < earthRadius) && (proj < 1.0) && (proj > 0.0);
            return !inUmbra;
        } catch (Exception e) {
            // In caso di errore fallback alla vecchia logica
            double altitude = satPosition.getNorm() - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
            return altitude > 200000; // 200 km
        }
    }
    
    /**
     * Classe interna per il risultato della valutazione di visibilità
     */
    private static class VisibilityAssessment {
        final boolean isVisible;
        final String category;
        final String description;
        
        VisibilityAssessment(boolean isVisible, String category, String description) {
            this.isVisible = isVisible;
            this.category = category;
            this.description = description;
        }
    }
}