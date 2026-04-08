package com.satelliteTracking.service;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassVisibilityServiceTest {

    private final PassVisibilityService service = new PassVisibilityService();

    @Test
    void shouldClassifyObservingConditionBySunElevation() {
        assertEquals("night", service.determineObservingCondition(-20.0));
        assertEquals("twilight", service.determineObservingCondition(-10.0));
        assertEquals("daylight", service.determineObservingCondition(-2.0));
    }

    @Test
    void shouldClassifyVisibilityConsistently() {
        assertEquals("excellent", service.calculateVisibility(65.0, true, "night"));
        assertEquals("good", service.calculateVisibility(45.0, true, "night"));
        assertEquals("fair", service.calculateVisibility(25.0, true, "twilight"));
        assertEquals("poor", service.calculateVisibility(50.0, false, "night"));
    }

    @Test
    void shouldUseFastPathOutsideBorderline() {
        boolean sunlit = service.isSunlitHybrid(
            new Vector3D(-7000000.0, 0.0, 0.0),
            new Vector3D(1.5e11, 0.0, 0.0),
            70.0,
            -30.0
        );

        assertTrue(sunlit);
    }

    @Test
    void shouldDetectShadowInBorderlineGeometry() {
        boolean sunlit = service.isSunlitHybrid(
            new Vector3D(-7000000.0, 0.0, 0.0),
            new Vector3D(1.5e11, 0.0, 0.0),
            90.0,
            -8.0
        );

        assertFalse(sunlit);
    }
}
