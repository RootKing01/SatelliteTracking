package com.satelliteTracking.service;

import com.satelliteTracking.dto.SatellitePositionDTO;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
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
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class SatellitePositionService {
        private final PassTimeService passTimeService;

        @org.springframework.beans.factory.annotation.Autowired
        public SatellitePositionService(PassTimeService passTimeService) {
                this.passTimeService = passTimeService;
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
        double range = topoCoords.getRange() / 1000.0;
        // TODO: calcolo magnitudine, visibilità, observingCondition
        LocalDateTime calculatedAtUtc = passTimeService.toLocalDateTime(date, java.time.ZoneOffset.UTC);
        return new SatellitePositionDTO(
                satellite.getId(),
                satellite.getObjectName(),
                satellite.getSatelliteType(),
                satellite.getObjectId(),
                satellite.getNoradCatId(),
                calculatedAtUtc,
                FastMath.toDegrees(satPoint.getLatitude()),
                FastMath.toDegrees(satPoint.getLongitude()),
                satPoint.getAltitude() / 1000.0,
                0.0, // distanceFromEarthCenterKm
                0.0, // meanMotion
                0.0, // orbitalPeriodMinutes
                0.0, // orbitalPeriodHours
                0.0, // velocityKmh
                0.0, // directionDeg
                null, // latestOrbitalParameters
                observerLat,
                observerLon,
                observerAlt,
                elevation,
                azimuth,
                range,
                null, // estimatedMagnitude
                null, // isVisible
                null, // visibility
                null  // observingCondition
        );
    }
}
