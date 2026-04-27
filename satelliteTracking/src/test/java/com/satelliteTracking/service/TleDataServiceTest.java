package com.satelliteTracking.service;

import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TleDataServiceTest {
    @Mock
    private SatelliteRepository satelliteRepository;
    @Mock
    private OrbitalParametersRepository orbitalParametersRepository;
    @InjectMocks
    private TleDataService tleDataService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testParseAndSaveJson_savesValidEntry() {
        String json = "[{\"NORAD_CAT_ID\":12345,\"EPOCH\":\"2024-01-01T00:00:00\",\"INCLINATION\":98.7,\"RA_OF_ASC_NODE\":120.5,\"ECCENTRICITY\":0.001,\"ARG_OF_PERICENTER\":87.6,\"MEAN_ANOMALY\":45.2,\"MEAN_MOTION\":15.2,\"TLE_LINE1\":\"1 12345U 98067A   24001.00000000  .00000000  00000-0  00000-0 0  9991\",\"TLE_LINE2\":\"2 12345 098.7000 120.5000 0001000 087.6000 045.2000 15.20000000    01\"}]";
        Satellite sat = new Satellite(1L, "TESTSAT", "1998-067A", 12345L);
        when(satelliteRepository.findByNoradCatId(12345L)).thenReturn(Optional.of(sat));
        int saved = tleDataService.parseAndSaveJson(json);
        assertEquals(1, saved);
        verify(orbitalParametersRepository, times(1)).save(any(OrbitalParameters.class));
    }

    @Test
    void testParseAndSaveJson_skipsIfSatelliteNotFound() {
        String json = "[{\"NORAD_CAT_ID\":99999,\"EPOCH\":\"2024-01-01T00:00:00\"}]";
        when(satelliteRepository.findByNoradCatId(99999L)).thenReturn(Optional.empty());
        int saved = tleDataService.parseAndSaveJson(json);
        assertEquals(0, saved);
        verify(orbitalParametersRepository, never()).save(any());
    }
}
