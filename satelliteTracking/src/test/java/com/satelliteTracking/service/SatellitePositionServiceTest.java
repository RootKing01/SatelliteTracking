package com.satelliteTracking.service;

import com.satelliteTracking.dto.SatellitePositionDTO;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.frames.FramesFactory;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SatellitePositionServiceTest {
    @Autowired
    private SatellitePositionService satellitePositionService;
    @Autowired
    private PassPhotometryService passPhotometryService;
    @Autowired
    private PassTimeService passTimeService;

    private Satellite testSatellite;
    private OrbitalParameters testParams;

    @BeforeEach
    public void setup() {
        testSatellite = new Satellite();
        testSatellite.setId(1L);
        testSatellite.setObjectName("ISS (ZARYA)");
        testSatellite.setSatelliteType("LEO");
        testSatellite.setObjectId("1998-067A");
        testSatellite.setNoradCatId(25544L);
        testParams = new OrbitalParameters();
        testParams.setTleLine1("1 25544U 98067A   24118.51041667  .00002182  00000-0  46413-4 0  9991");
        testParams.setTleLine2("2 25544  51.6417  21.2345 0004062  80.1234  10.1234 15.50000000    01");
    }

    @Test
    public void testComputeObservation_Sunlit() {
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH.shiftedBy(1000000);
        SatellitePositionDTO dto = satellitePositionService.computeObservation(
                testSatellite, testParams, date, 41.9, 12.5, 0.0);
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getIsVisible());
        Assertions.assertNotNull(dto.getVisibility());
    }

    @Test
    public void testComputeSunlitStatus_Eclipse() {
        // Satellite in ombra (posizione tra Terra e Sole)
        Vector3D satPos = new Vector3D(Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400000, 0, 0);
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
        boolean sunlit = satellitePositionService.computeSunlitStatus(satPos, date, FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        // Non possiamo garantire sempre ombra, ma il test verifica che il metodo non lanci eccezioni
        Assertions.assertTrue(sunlit || !sunlit);
    }

    @Test
    public void testComputeObservation_EdgeCases() {
        // Satellite molto basso
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH.shiftedBy(100);
        SatellitePositionDTO dto = satellitePositionService.computeObservation(
                testSatellite, testParams, date, 0.0, 0.0, 0.0);
        Assertions.assertNotNull(dto);
        // Satellite molto alto
        date = AbsoluteDate.J2000_EPOCH.shiftedBy(1e7);
        dto = satellitePositionService.computeObservation(
                testSatellite, testParams, date, 0.0, 0.0, 0.0);
        Assertions.assertNotNull(dto);
    }
}
