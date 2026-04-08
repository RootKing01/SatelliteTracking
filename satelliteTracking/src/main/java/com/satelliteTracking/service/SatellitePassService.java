package com.satelliteTracking.service;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.model.TelegramSubscription;
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
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.propagation.events.ElevationExtremumDetector;
import org.orekit.propagation.events.EventSlopeFilter;
import org.orekit.propagation.events.EventsLogger;
import org.orekit.propagation.events.FilterType;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servizio per calcolare i passaggi visibili dei satelliti usando Orekit e SGP4
 */
@Service
public class SatellitePassService {

    private final SatelliteRepository satelliteRepository;
    private final OrbitalParametersRepository orbitalParametersRepository;
    private final PassTimeService passTimeService;
    private final PassVisibilityService passVisibilityService;
    private final PassPhotometryService passPhotometryService;
    
    // Posizione predefinita caricata da configuration (single source of truth)
    private final ObserverLocation defaultLocation;
    
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
    
    private final Map<String, CacheEntry> passesCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 1800000; // 30 minuti
    private static final double EVENT_MAX_CHECK_SECONDS = 60.0;
    private static final double EVENT_THRESHOLD_SECONDS = 0.001;

    public SatellitePassService(SatelliteRepository satelliteRepository,
                                OrbitalParametersRepository orbitalParametersRepository,
                                PassTimeService passTimeService,
                                PassVisibilityService passVisibilityService,
                                PassPhotometryService passPhotometryService,
                                @Value("${satellite.default-location.latitude:41.01}") double defaultLatitude,
                                @Value("${satellite.default-location.longitude:14.30}") double defaultLongitude,
                                @Value("${satellite.default-location.altitude:30.0}") double defaultAltitude,
                                @Value("${satellite.default-location.name:San Marcellino, Caserta, Italia}") String defaultLocationName) {
        this.satelliteRepository = satelliteRepository;
        this.orbitalParametersRepository = orbitalParametersRepository;
        this.passTimeService = passTimeService;
        this.passVisibilityService = passVisibilityService;
        this.passPhotometryService = passPhotometryService;
        this.defaultLocation = new ObserverLocation(defaultLatitude, defaultLongitude, defaultAltitude, defaultLocationName);
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
            
            return calculatePasses(satellite, latestParams, hours, observerLocation);
            
        } catch (Exception e) {
            System.err.println("❌ Error calculating passes: " + e.getMessage());
            e.printStackTrace();
            passes.add(createSimplifiedPass(satelliteId));
        }
        
        return passes;
    }

    /**
     * Calcola i passaggi usando i parametri orbitali già caricati.
     */
    private List<SatellitePassDTO> calculatePasses(Satellite satellite, OrbitalParameters latestParams,
                                                   int hours, ObserverLocation observerLocation) {
        List<SatellitePassDTO> passes = new ArrayList<>();

        if (latestParams == null) {
            return passes;
        }

        // Check preventivo: verifica se il satellite può essere visibile da questa latitudine
        double inclination = latestParams.getInclination();
        double observerLat = Math.abs(observerLocation.getLatitude());
        boolean canBeVisible = canBeVisibleAtLatitude(inclination, observerLat);

        System.out.println("🛰️  Satellite: " + satellite.getObjectName() +
                         " | Inclinazione: " + inclination + "° | Osservatore: " + observerLat +
                         "° | Può essere visibile: " + canBeVisible);

        if (!canBeVisible) {
            System.out.println("⛔ Satellite non visibile da questa posizione (inclinazione insufficiente)");
            return passes;
        }

        // Converti parametri orbitali in TLE
        String[] tleLines = TLEConverter.buildTLE(
            satellite.getNoradCatId(),
            satellite.getObjectName(),
            latestParams
        );

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
            ZoneId outputZone = passTimeService.resolveOutputZone(observerLocation);

            AbsoluteDate startDate = passTimeService.nowUtc();
            AbsoluteDate endDate = startDate.shiftedBy(hours * 3600.0);

            double step = 60.0;
            List<PassData> passDataList = new ArrayList<>();
            PassData currentPass = null;

            AbsoluteDate previousDate = startDate;
            double previousElevation = sampleElevationDegrees(propagator, itrf, topoFrame, previousDate);

            if (previousElevation > 0) {
                currentPass = new PassData();
                currentPass.riseBracketStart = startDate;
                currentPass.riseBracketEnd = startDate;
                currentPass.maxElevation = previousElevation;
                currentPass.maxElevationDate = startDate;
            }

            // Posizione del sole per calcolare illuminazione
            var sun = CelestialBodyFactory.getSun();

            for (AbsoluteDate date = startDate.shiftedBy(step);
                 date.compareTo(endDate) <= 0;
                 date = date.shiftedBy(step)) {

                var pv = propagator.getPVCoordinates(date, itrf);
                var topoCoordinates = topoFrame.getTrackingCoordinates(pv.getPosition(), itrf, date);

                double elevation = FastMath.toDegrees(topoCoordinates.getElevation());
                double azimuth = FastMath.toDegrees(topoCoordinates.getAzimuth());
                double range = topoCoordinates.getRange() / 1000.0;

                if (elevation > 0) {
                    if (currentPass == null && previousElevation <= 0) {
                        currentPass = new PassData();
                        currentPass.riseBracketStart = previousDate;
                        currentPass.riseBracketEnd = date;
                        currentPass.riseAzimuth = azimuth;
                    } else if (currentPass != null && currentPass.riseBracketStart == null) {
                        currentPass.riseBracketStart = startDate;
                        currentPass.riseBracketEnd = date;
                    }

                    if (elevation > currentPass.maxElevation) {
                        currentPass.maxElevation = elevation;
                        currentPass.maxElevationDate = date;
                        currentPass.maxElevationAzimuth = azimuth;
                        currentPass.maxDistance = range;
                    }
                } else {
                    if (currentPass != null) {
                        currentPass.setBracketStart = previousDate;
                        currentPass.setBracketEnd = date;
                        passDataList.add(currentPass);
                        currentPass = null;
                    }
                }

                previousDate = date;
                previousElevation = elevation;
            }

            // Se la finestra termina mentre il satellite e' ancora sopra l'orizzonte,
            // chiudi comunque il passaggio al bordo della finestra per non perderlo.
            if (currentPass != null) {
                currentPass.setBracketStart = previousDate;
                currentPass.setBracketEnd = endDate;
                passDataList.add(currentPass);
            }

            for (PassData pd : passDataList) {
                if (pd.maxElevation > 10.0) {
                    AbsoluteDate refinedRise = refineRiseOrSet(pd.riseBracketStart, pd.riseBracketEnd, propagator, itrf, topoFrame);
                    AbsoluteDate refinedSet = refineRiseOrSet(pd.setBracketStart, pd.setBracketEnd, propagator, itrf, topoFrame);
                    AbsoluteDate refinedPeak = refinePeakTime(refinedRise, refinedSet, propagator, itrf, topoFrame);

                    PassSample riseSample = sampleAt(refinedRise, propagator, itrf, topoFrame);
                    PassSample setSample = sampleAt(refinedSet, propagator, itrf, topoFrame);
                    PassSample peakSample = sampleAt(refinedPeak, propagator, itrf, topoFrame);

                    pd.riseTime = passTimeService.toLocalDateTime(refinedRise, outputZone);
                    pd.riseAzimuth = riseSample.azimuth;
                    pd.setTime = passTimeService.toLocalDateTime(refinedSet, outputZone);
                    pd.setAzimuth = setSample.azimuth;
                    pd.maxElevationTime = passTimeService.toLocalDateTime(refinedPeak, outputZone);
                    pd.maxElevationDate = refinedPeak;
                    pd.maxElevation = peakSample.elevation;
                    pd.maxElevationAzimuth = peakSample.azimuth;
                    pd.maxDistance = peakSample.rangeKm;

                    PVCoordinates peakPv = peakSample.pv;
                    PVCoordinates sunPV = sun.getPVCoordinates(refinedPeak, itrf);
                    var sunTopoCoords = topoFrame.getTrackingCoordinates(sunPV.getPosition(), itrf, refinedPeak);
                    double sunElevation = FastMath.toDegrees(sunTopoCoords.getElevation());
                    pd.sunElevation = sunElevation;

                    double satAltitude = peakPv.getPosition().getNorm() / 1000.0 - Constants.WGS84_EARTH_EQUATORIAL_RADIUS / 1000.0;
                    pd.satelliteAltitude = satAltitude;

                    Vector3D observerPosition = earth.transform(observerPoint);
                    Vector3D satToSun = sunPV.getPosition().subtract(peakPv.getPosition());
                    Vector3D satToObserver = observerPosition.subtract(peakPv.getPosition());
                    pd.phaseAngleDeg = FastMath.toDegrees(Vector3D.angle(satToSun, satToObserver));

                    double sunAngle = FastMath.toDegrees(
                        org.hipparchus.geometry.euclidean.threed.Vector3D.angle(
                            peakPv.getPosition(),
                            sunPV.getPosition()
                        )
                    );
                    pd.isSunlit = passVisibilityService.isSunlitHybrid(
                        peakPv.getPosition(),
                        sunPV.getPosition(),
                        sunAngle,
                        sunElevation
                    );

                    String observingCondition = passVisibilityService.determineObservingCondition(pd.sunElevation);

                    String visibility = passVisibilityService.calculateVisibility(pd.maxElevation, pd.isSunlit, observingCondition);

                    double magnitude = passPhotometryService.estimateMagnitude(pd.maxDistance, pd.phaseAngleDeg, pd.isSunlit);

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
                        pd.maxElevationAzimuth,
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

        return passes;
    }

    private PassSample sampleAt(AbsoluteDate date, TLEPropagator propagator, Frame itrf, TopocentricFrame topoFrame) {
        var pv = propagator.getPVCoordinates(date, itrf);
        var topoCoordinates = topoFrame.getTrackingCoordinates(pv.getPosition(), itrf, date);

        return new PassSample(
            date,
            FastMath.toDegrees(topoCoordinates.getElevation()),
            FastMath.toDegrees(topoCoordinates.getAzimuth()),
            topoCoordinates.getRange() / 1000.0,
            pv
        );
    }

    private double sampleElevationDegrees(TLEPropagator propagator, Frame itrf, TopocentricFrame topoFrame, AbsoluteDate date) {
        return sampleAt(date, propagator, itrf, topoFrame).elevation;
    }

    private AbsoluteDate refineRiseOrSet(AbsoluteDate lower, AbsoluteDate upper, TLEPropagator propagator, Frame itrf, TopocentricFrame topoFrame) {
        if (lower == null && upper == null) {
            return null;
        }
        if (lower == null) {
            return upper;
        }
        if (upper == null || lower.equals(upper)) {
            return lower;
        }

        EventsLogger logger = new EventsLogger();
        ElevationDetector detector = new ElevationDetector(EVENT_MAX_CHECK_SECONDS, EVENT_THRESHOLD_SECONDS, topoFrame)
            .withConstantElevation(0.0)
            .withHandler(new ContinueOnEvent());

        try {
            propagator.clearEventsDetectors();
            propagator.addEventDetector(logger.monitorDetector(detector));
            propagator.propagate(lower, upper);

            if (!logger.getLoggedEvents().isEmpty()) {
                return logger.getLoggedEvents().get(0).getDate();
            }

            return lower.shiftedBy(upper.durationFrom(lower) / 2.0);
        } finally {
            propagator.clearEventsDetectors();
        }
    }

    private AbsoluteDate refinePeakTime(AbsoluteDate lower, AbsoluteDate upper, TLEPropagator propagator, Frame itrf, TopocentricFrame topoFrame) {
        if (lower == null && upper == null) {
            return null;
        }
        if (lower == null) {
            return upper;
        }
        if (upper == null || lower.equals(upper)) {
            return lower;
        }

        EventsLogger logger = new EventsLogger();
        EventSlopeFilter<ElevationExtremumDetector> detector =
            new EventSlopeFilter<>(
                new ElevationExtremumDetector(EVENT_MAX_CHECK_SECONDS, EVENT_THRESHOLD_SECONDS, topoFrame),
                FilterType.TRIGGER_ONLY_DECREASING_EVENTS
            ).withHandler(new ContinueOnEvent());

        try {
            propagator.clearEventsDetectors();
            propagator.addEventDetector(logger.monitorDetector(detector));
            propagator.propagate(lower, upper);

            if (!logger.getLoggedEvents().isEmpty()) {
                return logger.getLoggedEvents().get(0).getDate();
            }

            return lower.shiftedBy(upper.durationFrom(lower) / 2.0);
        } finally {
            propagator.clearEventsDetectors();
        }
    }
    
    /**
     * Calcolo semplificato (senza Orekit)
     * Controlla se il satellite può essere visibile dalla latitudine dell'osservatore
     */
    private SatellitePassDTO createSimplifiedPass(Satellite satellite, OrbitalParameters params, 
                                                   ObserverLocation location, int hours) {
        LocalDateTime now = passTimeService.nowForObserver(location);
        double orbitalPeriod = 1440.0 / params.getMeanMotion();
        double hoursUntilPass = Math.min(hours / 2.0, orbitalPeriod / 60.0);
        
        // Verifica se il satellite può passare sopra questa latitudine
        // Un satellite può essere visibile solo se la sua inclinazione >= latitudine osservatore
        double inclination = params.getInclination();
        double observerLat = Math.abs(location.getLatitude());
        boolean canBeVisible = canBeVisibleAtLatitude(inclination, observerLat);
        
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
            180.0,  // maxElevationAzimuth
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
        
        LocalDateTime now = passTimeService.nowForObserver(defaultLocation);
        return new SatellitePassDTO(
            satelliteId,
            name + " (error)",
            now.plusHours(2),
            now.plusHours(2).plusMinutes(5),
            now.plusHours(2).plusMinutes(10),
            25.0,
            120.0,
            180.0,  // maxElevationAzimuth
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
        AbsoluteDate riseBracketStart;
        AbsoluteDate riseBracketEnd;
        LocalDateTime riseTime;
        LocalDateTime maxElevationTime;
        AbsoluteDate maxElevationDate;
        AbsoluteDate setBracketStart;
        AbsoluteDate setBracketEnd;
        LocalDateTime setTime;
        double maxElevation = 0;
        double riseAzimuth;
        double maxElevationAzimuth;  // 🆕 Azimuth al massimo dell'elevazione
        double setAzimuth;
        double maxDistance;
        double satelliteAltitude;
        double phaseAngleDeg = 90.0;
        boolean isSunlit;
        double sunElevation;
    }

    private static class PassSample {
        final AbsoluteDate date;
        final double elevation;
        final double azimuth;
        final double rangeKm;
        final PVCoordinates pv;

        PassSample(AbsoluteDate date, double elevation, double azimuth, double rangeKm, PVCoordinates pv) {
            this.date = date;
            this.elevation = elevation;
            this.azimuth = azimuth;
            this.rangeKm = rangeKm;
            this.pv = pv;
        }
    }
    
    /**
     * Verifica rapida di raggiungibilita' latitudinale in base all'inclinazione orbitale.
     */
    private boolean canBeVisibleAtLatitude(double inclinationDeg, double observerLatitudeDegAbs) {
        return inclinationDeg >= observerLatitudeDegAbs &&
               inclinationDeg <= (180.0 - observerLatitudeDegAbs);
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
        String cacheKey = String.format("%.4f_%.4f_%.1f_%d_%.1f_%s_%.1f",
                   observerLocation.getLatitude(),
                   observerLocation.getLongitude(),
                   observerLocation.getAltitude(),
                   hours, minElevation, observingCondition.toLowerCase(), maxMagnitude);
        
        // Controlla cache
        if (passesCache.containsKey(cacheKey)) {
            CacheEntry entry = passesCache.get(cacheKey);
            if (!entry.isExpired(CACHE_TTL_MS)) {
                LocalDateTime now = passTimeService.nowForObserver(observerLocation);
                List<SatellitePassDTO> filtered = new ArrayList<>();
                for (SatellitePassDTO pass : entry.passes) {
                    if (pass.riseTime().isAfter(now)) {
                        filtered.add(pass);
                    }
                }

                if (!filtered.isEmpty()) {
                    System.out.println("Cache hit: returning " + filtered.size() + " upcoming passes");
                    return filtered;
                }

                passesCache.remove(cacheKey);
            }
        }
        
        List<SatellitePassDTO> allPasses = Collections.synchronizedList(new ArrayList<>());
        
        try {
            double observerLat = Math.abs(observerLocation.getLatitude());
            Map<Long, OrbitalParameters> latestParametersBySatelliteId = loadLatestOrbitalParameters();
            
            // Filtra satelliti per inclinazione PRIMA di calcolare i passaggi
            List<Satellite> visibleSatellites = new ArrayList<>();
            for (OrbitalParameters latestParams : latestParametersBySatelliteId.values()) {
                Satellite sat = latestParams.getSatellite();
                if (sat == null || sat.getId() == null) {
                    continue;
                }
                
                double inclination = latestParams.getInclination();
                if (canBeVisibleAtLatitude(inclination, observerLat)) {
                    visibleSatellites.add(sat);
                }
            }
            
            System.out.println("🔍 Scanning " + visibleSatellites.size() + " satelliti da " + 
                             observerLocation.getLocationName() + " [Condizione: " + observingCondition + 
                             ", Max magnitudine: " + maxMagnitude + "]");
            
            LongAdder rejectedVisibility = new LongAdder();
            LongAdder rejectedElevation = new LongAdder();
            LongAdder rejectedCondition = new LongAdder();
            LongAdder rejectedMagnitude = new LongAdder();

            visibleSatellites.parallelStream().forEach(satellite -> {
                try {
                    OrbitalParameters latestParams = latestParametersBySatelliteId.get(satellite.getId());
                    if (latestParams == null) {
                        return;
                    }

                    List<SatellitePassDTO> passes = calculatePasses(satellite, latestParams, hours, observerLocation);

                    // Filtra per elevazione minima, visibilità, condizione osservazione e magnitudine
                    for (SatellitePassDTO pass : passes) {
                        if (!pass.isVisible()) {
                            rejectedVisibility.increment();
                            continue;
                        }

                        if (pass.maxElevation() < minElevation) {
                            rejectedElevation.increment();
                            continue;
                        }

                        if (!"any".equalsIgnoreCase(observingCondition) &&
                            !pass.observingCondition().equalsIgnoreCase(observingCondition)) {
                            rejectedCondition.increment();
                            continue;
                        }

                        if (pass.estimatedMagnitude() > maxMagnitude) {
                            rejectedMagnitude.increment();
                            continue;
                        }

                        allPasses.add(pass);
                    }
                } catch (Exception e) {
                    // Continua con il prossimo satellite
                }
            });
            
            // Ordina per tempo di rise
            allPasses.sort((p1, p2) -> p1.riseTime().compareTo(p2.riseTime()));
            
            // Salva in cache solo se ci sono risultati
            if (!allPasses.isEmpty()) {
                passesCache.put(cacheKey, new CacheEntry(allPasses));
            }

            System.out.println("Found " + allPasses.size() + " passes after filters (minElevation=" + minElevation +
                             ", condition=" + observingCondition + ", maxMagnitude=" + maxMagnitude + "). Rejected: " +
                             " notVisible=" + rejectedVisibility.sum() +
                             ", elevation=" + rejectedElevation.sum() +
                             ", condition=" + rejectedCondition.sum() +
                             ", magnitude=" + rejectedMagnitude.sum());
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
     * Pre-calcola e mette in cache i passaggi per ogni subscription Telegram attiva,
     * usando i parametri esatti di ciascun utente (posizione, condizione, magnitudine, elevazione).
     * Viene chiamato dallo scheduler dopo ogni aggiornamento TLE e ogni ora.
     */
    public void precomputePassesForSubscriptions(List<TelegramSubscription> subscriptions) {
        for (TelegramSubscription sub : subscriptions) {
            try {
                ObserverLocation loc = new ObserverLocation(
                    sub.getLatitude(), sub.getLongitude(), sub.getAltitude(), sub.getLocationName()
                );
                findVisibleUpcomingPasses(3, sub.getMinElevation(), loc, sub.getObservingCondition(), sub.getMaxMagnitude());
                System.out.println("✅ [Cache] Pre-calcolati passaggi per " + sub.getLocationName() +
                                 " (user: " + sub.getUserIdentifier() + ")");
            } catch (Exception e) {
                System.err.println("⚠️  [Cache] Errore pre-calcolo per " + sub.getLocationName() +
                                 ": " + e.getMessage());
            }
        }
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

    private Map<Long, OrbitalParameters> loadLatestOrbitalParameters() {
        return orbitalParametersRepository.findLatestForAllSatellites().stream()
            .filter(parameters -> parameters.getSatellite() != null && parameters.getSatellite().getId() != null)
            .collect(Collectors.toMap(
                parameters -> parameters.getSatellite().getId(),
                Function.identity(),
                (left, right) -> left,
                HashMap::new
            ));
    }

}