package com.satelliteTracking.service;

import org.springframework.stereotype.Service;

@Service
public class PassPhotometryService {

    public double estimateMagnitude(double distanceKm, double phaseAngleDeg, boolean isSunlit) {
        double absoluteMagnitude = -1.0;
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
}
