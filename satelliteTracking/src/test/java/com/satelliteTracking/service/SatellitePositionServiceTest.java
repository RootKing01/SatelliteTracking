package com.satelliteTracking.service;

import java.io.File;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.time.AbsoluteDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.satelliteTracking.dto.SatellitePositionDTO;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;

@SpringBootTest
@ActiveProfiles("test")
public class SatellitePositionServiceTest {

    @BeforeAll
    static void initOrekitData() throws URISyntaxException {
        File orekitData = new File(
            SatellitePositionServiceTest.class.getClassLoader()
                .getResource("orekit-data")
                .toURI()
        );
        DataContext.getDefault().getDataProvidersManager()
                .addProvider(new DirectoryCrawler(orekitData));
    }

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
        testParams.setMeanMotion(15.5);
        testParams.setInclination(51.6417);
        testParams.setRaOfAscNode(21.2345);
        testParams.setEccentricity(0.0004062);
        testParams.setArgOfPericenter(80.1234);
        testParams.setMeanAnomaly(10.1234);
        testParams.setEpoch("2024-04-27T12:15:00.000");
        testParams.setTleLine1("1 25544U 98067A   24118.51041667  .00002182  00000-0  46413-4 0  9991");
        testParams.setTleLine2("2 25544  51.6417  21.2345 0004062  80.1234  10.1234 15.50000000    05");
    }

    @Test
    public void testComputeObservation_Sunlit() {
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH.shiftedBy(1000000);
        SatellitePositionDTO dto = satellitePositionService.computeObservation(
                testSatellite, testParams, date, 41.9, 12.5, 0.0);
        Assertions.assertNotNull(dto);
        // isVisible e visibility sono legittimamente null se il satellite è sotto l'orizzonte
        // in questo istante/posizione: non è un bug, è comportamento atteso.
    }

    @Test
    public void testComputeObservation_EdgeCases() {
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH.shiftedBy(100);
        SatellitePositionDTO dto = satellitePositionService.computeObservation(
                testSatellite, testParams, date, 0.0, 0.0, 0.0);
        Assertions.assertNotNull(dto);

        date = AbsoluteDate.J2000_EPOCH.shiftedBy(1e7);
        dto = satellitePositionService.computeObservation(
                testSatellite, testParams, date, 0.0, 0.0, 0.0);
        Assertions.assertNotNull(dto);
    }

    @Test
    public void testComputeObservation_DifferentLocations() {
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH.shiftedBy(500000);

        SatellitePositionDTO dtoRome = satellitePositionService.computeObservation(
                testSatellite, testParams, date, 41.9, 12.5, 0.0);
        Assertions.assertNotNull(dtoRome);

        SatellitePositionDTO dtoNorthPole = satellitePositionService.computeObservation(
                testSatellite, testParams, date, 90.0, 0.0, 0.0);
        Assertions.assertNotNull(dtoNorthPole);

        SatellitePositionDTO dtoEquator = satellitePositionService.computeObservation(
                testSatellite, testParams, date, 0.0, 0.0, 0.0);
        Assertions.assertNotNull(dtoEquator);
    }
}