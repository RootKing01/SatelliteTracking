package com.satelliteTracking.util;

import com.satelliteTracking.model.Satellite;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SatelliteTypeNormalizerTest {

    @Test
    void prefersKnownSatelliteGroupOverRawObjectType() {
        Satellite satellite = new Satellite();
        satellite.setSatelliteType("stations");
        satellite.setObjectTypeRaw("PAYLOAD");

        Assertions.assertEquals("stations", SatelliteTypeNormalizer.canonicalizeSatelliteType(satellite));
        Assertions.assertEquals("stations", SatelliteTypeNormalizer.resolveEffectiveType(satellite));
    }

    @Test
    void treatsTbaAsPendingClassification() {
        Satellite satellite = new Satellite();
        satellite.setSatelliteType("UNKNOWN");
        satellite.setObjectTypeRaw("TBA - To be assigned");
        satellite.setObjectTypeInferred("LEO");

        Assertions.assertTrue(SatelliteTypeNormalizer.isPendingClassification(satellite));
        Assertions.assertEquals("", SatelliteTypeNormalizer.canonicalizeSatelliteType(satellite));
        Assertions.assertEquals("leo", SatelliteTypeNormalizer.resolveEffectiveType(satellite));
    }

    @Test
    void keepsRawTypeForNonGroupObjects() {
        Satellite satellite = new Satellite();
        satellite.setSatelliteType("UNKNOWN");
        satellite.setObjectTypeRaw("PAYLOAD");

        Assertions.assertEquals("payload", SatelliteTypeNormalizer.canonicalizeSatelliteType(satellite));
        Assertions.assertEquals("payload", SatelliteTypeNormalizer.resolveEffectiveType(satellite));
    }
}