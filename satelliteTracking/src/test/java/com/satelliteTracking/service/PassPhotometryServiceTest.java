package com.satelliteTracking.service;

import com.satelliteTracking.model.Satellite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PassPhotometryServiceTest {

    private final PassPhotometryService service = new PassPhotometryService();

    @Test
    void closerSunlitSatelliteShouldBeBrighter() {
        double nearMagnitude = service.estimateMagnitude(600.0, 40.0, true);
        double farMagnitude = service.estimateMagnitude(30000.0, 40.0, true);

        assertTrue(nearMagnitude < farMagnitude);
    }

    @Test
    void shadowShouldBeDimmerThanSunlitAtSameGeometry() {
        double sunlitMagnitude = service.estimateMagnitude(1000.0, 60.0, true);
        double shadowMagnitude = service.estimateMagnitude(1000.0, 60.0, false);

        assertTrue(shadowMagnitude > sunlitMagnitude);
    }

    @Test
    void magnitudeShouldBeClampedToExpectedRange() {
        double veryBright = service.estimateMagnitude(10.0, 0.0, true);
        double veryDim = service.estimateMagnitude(1000000.0, 180.0, false);

        assertTrue(veryBright >= -5.0);
        assertTrue(veryDim <= 9.0);
    }

    @Test
    void issShouldBeBrighterThanUnknownSatelliteAtSameGeometry() {
        Satellite iss = new Satellite();
        iss.setNoradCatId(25544L);
        iss.setObjectName("ISS (ZARYA)");

        Satellite unknown = new Satellite();
        unknown.setNoradCatId(999999L);
        unknown.setObjectName("UNKNOWN SAT");

        double issMagnitude = service.estimateMagnitude(800.0, 50.0, true, iss);
        double unknownMagnitude = service.estimateMagnitude(800.0, 50.0, true, unknown);

        assertTrue(issMagnitude < unknownMagnitude);
    }

    @Test
    void starlinkShouldUseAReasonableFallback() {
        Satellite starlink = new Satellite();
        starlink.setObjectName("STARLINK-1234");

        Satellite generic = new Satellite();
        generic.setObjectName("GENERIC SAT");

        double starlinkMagnitude = service.estimateMagnitude(900.0, 60.0, true, starlink);
        double genericMagnitude = service.estimateMagnitude(900.0, 60.0, true, generic);

        assertTrue(starlinkMagnitude > genericMagnitude);
    }
}
