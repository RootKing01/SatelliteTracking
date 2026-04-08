package com.satelliteTracking.service;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.utils.Constants;
import org.springframework.stereotype.Service;

@Service
public class PassVisibilityService {

    public String determineObservingCondition(double sunElevation) {
        if (sunElevation < -18) {
            return "night";
        }
        if (sunElevation < -6) {
            return "twilight";
        }
        return "daylight";
    }

    public String calculateVisibility(double elevation, boolean isSunlit, String condition) {
        if (!isSunlit || condition.equals("daylight")) {
            return "poor";
        }

        if (elevation > 60 && condition.equals("night")) {
            return "excellent";
        }
        if (elevation > 40 && condition.equals("night")) {
            return "good";
        }
        if (elevation > 20 || condition.equals("twilight")) {
            return "fair";
        }

        return "poor";
    }

    public boolean isSunlitHybrid(Vector3D satPosition,
                                  Vector3D sunPosition,
                                  double sunAngleDeg,
                                  double sunElevationDeg) {
        boolean fastSunlit = sunAngleDeg < 90.0;

        boolean nearTerminator = sunAngleDeg >= 85.0 && sunAngleDeg <= 95.0;
        boolean twilightLike = sunElevationDeg >= -12.0 && sunElevationDeg <= 2.0;

        if (nearTerminator || twilightLike) {
            return isSunlitRefinedUmbra(satPosition, sunPosition);
        }

        return fastSunlit;
    }

    private boolean isSunlitRefinedUmbra(Vector3D satPosition, Vector3D sunPosition) {
        Vector3D sunDir = sunPosition.normalize();
        double projectionOnSunDir = satPosition.dotProduct(sunDir);

        if (projectionOnSunDir > 0.0) {
            return true;
        }

        Vector3D orthogonal = satPosition.subtract(sunDir.scalarMultiply(projectionOnSunDir));
        double distanceFromShadowAxis = orthogonal.getNorm();

        double nightSideDistance = -projectionOnSunDir;
        double earthRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
        double sunDistance = sunPosition.getNorm();
        double umbraLength = (earthRadius * sunDistance) / (Constants.SUN_RADIUS - earthRadius);

        if (nightSideDistance >= umbraLength) {
            return true;
        }

        double umbraRadius = earthRadius * (1.0 - (nightSideDistance / umbraLength));
        return distanceFromShadowAxis > umbraRadius;
    }
}
