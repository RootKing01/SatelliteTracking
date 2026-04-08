package com.satelliteTracking.service;

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
}
