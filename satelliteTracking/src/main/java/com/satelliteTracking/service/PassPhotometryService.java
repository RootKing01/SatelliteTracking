package com.satelliteTracking.service;

import com.satelliteTracking.model.Satellite;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PassPhotometryService {

    private static final double DEFAULT_ABSOLUTE_MAGNITUDE = 4.0;

    private static final Map<Long, Double> ABSOLUTE_MAGNITUDE_BY_NORAD = Map.ofEntries(
        Map.entry(25544L, -3.9),  // ISS
        Map.entry(20580L, 1.8),   // Hubble Space Telescope
        Map.entry(48274L, 0.3)    // Tiangong
    );

    public double estimateMagnitude(double distanceKm, double phaseAngleDeg, boolean isSunlit) {
        return estimateMagnitude(distanceKm, phaseAngleDeg, isSunlit, null);
    }

    public double estimateMagnitude(double distanceKm, double phaseAngleDeg, boolean isSunlit, Satellite satellite) {
        double absoluteMagnitude = resolveAbsoluteMagnitude(satellite);
        double magnitude = absoluteMagnitude + 5.0 * Math.log10(distanceKm) - 15.0;

        double phaseRad = Math.toRadians(Math.max(0.0, Math.min(180.0, phaseAngleDeg)));
        double phaseFactor = (Math.sin(phaseRad) + (Math.PI - phaseRad) * Math.cos(phaseRad)) / Math.PI;
        phaseFactor = Math.max(1.0e-3, phaseFactor);
        double phaseCorrection = -2.5 * Math.log10(phaseFactor);

        if (isSunlit) {
            magnitude -= phaseCorrection;
        } else {
            magnitude += 6.0;
        }

        magnitude = Math.max(-5.0, Math.min(9.0, magnitude));
        return Math.round(magnitude * 10.0) / 10.0;
    }

    private double resolveAbsoluteMagnitude(Satellite satellite) {
        if (satellite == null) {
            return DEFAULT_ABSOLUTE_MAGNITUDE;
        }

        Long noradCatId = satellite.getNoradCatId();
        if (noradCatId != null) {
            Double knownMagnitude = ABSOLUTE_MAGNITUDE_BY_NORAD.get(noradCatId);
            if (knownMagnitude != null) {
                return knownMagnitude;
            }
        }

        String satelliteType = satellite.getEffectiveType();
        if (satelliteType != null && satelliteType.equalsIgnoreCase("starlink")) {
            return 4.5;
        }

        return DEFAULT_ABSOLUTE_MAGNITUDE;
    }
}
