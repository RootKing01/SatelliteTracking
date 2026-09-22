package com.satelliteTracking.service;

import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.service.SpaceTrackService.DeltaFetchResult;
import com.satelliteTracking.service.SpaceTrackService.DeltaFetchStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.Collection;
import java.util.ArrayList;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TleDataServiceTest {
    @Mock
    private SatelliteRepository satelliteRepository;
    @Mock
    private OrbitalParametersRepository orbitalParametersRepository;
    @Mock
    private SpaceTrackService spaceTrackService;
    @Mock
    private CelestrakService celestrakService;
    @Mock
    private SatelliteAuditFileService satelliteAuditFileService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private TleDataService tleDataService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        lenient().when(orbitalParametersRepository.findEpochKeysByNoradCatIdIn(anySet())).thenReturn(Set.of());
        setPrimarySource("spacetrack");
    }

    private void setPrimarySource(String value) {
        try {
            Field field = TleDataService.class.getDeclaredField("primarySource");
            field.setAccessible(true);
            field.set(tleDataService, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testParseAndSaveJson_savesValidEntry() {
        String json = "[{\"NORAD_CAT_ID\":12345,\"EPOCH\":\"2024-01-01T00:00:00\",\"INCLINATION\":98.7,\"RA_OF_ASC_NODE\":120.5,\"ECCENTRICITY\":0.001,\"ARG_OF_PERICENTER\":87.6,\"MEAN_ANOMALY\":45.2,\"MEAN_MOTION\":15.2,\"TLE_LINE1\":\"1 12345U 98067A   24001.00000000  .00000000  00000-0  00000-0 0  9991\",\"TLE_LINE2\":\"2 12345 098.7000 120.5000 0001000 087.6000 045.2000 15.20000000    01\"}]";
        Satellite sat = new Satellite(1L, "TESTSAT", "1998-067A", 12345L);
        when(satelliteRepository.findByNoradCatIdIn(anyCollection())).thenReturn(List.of(sat));
        when(orbitalParametersRepository.findEpochKeysByNoradCatIdIn(anySet())).thenReturn(Set.of());
        int saved = tleDataService.parseAndSaveJson(json);
        assertEquals(1, saved);
        verify(orbitalParametersRepository, times(1)).saveAll(any());
    }

    @Test
    void testParseAndSaveJson_createsSatelliteIfMissing() {
        String json = "[{\"NORAD_CAT_ID\":99999,\"OBJECT_NAME\":\"NEWTST\",\"OBJECT_ID\":\"2024-001A\",\"EPOCH\":\"2024-01-01T00:00:00\",\"INCLINATION\":10.1,\"RA_OF_ASC_NODE\":20.2,\"ECCENTRICITY\":0.001,\"ARG_OF_PERICENTER\":30.3,\"MEAN_ANOMALY\":40.4,\"MEAN_MOTION\":15.2,\"TLE_LINE1\":\"L1\",\"TLE_LINE2\":\"L2\",\"OBJECT_TYPE\":\"PAYLOAD\"}]";
        when(satelliteRepository.findByNoradCatIdIn(anyCollection())).thenReturn(List.of());
        when(orbitalParametersRepository.findEpochKeysByNoradCatIdIn(anySet())).thenReturn(Set.of());
        when(satelliteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orbitalParametersRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        int saved = tleDataService.parseAndSaveJson(json);
        assertEquals(1, saved);
        verify(satelliteRepository, times(1)).saveAll(any());
        verify(orbitalParametersRepository, times(1)).saveAll(any());
    }

    @Test
    void testParseAndSaveJson_rejectsIncompleteRow() {
        String json = "[{\"NORAD_CAT_ID\":12345,\"EPOCH\":\"2024-01-01T00:00:00\"}]";
        when(satelliteRepository.findByNoradCatIdIn(anyCollection())).thenReturn(List.of());
        when(orbitalParametersRepository.findEpochKeysByNoradCatIdIn(anySet())).thenReturn(Set.of());
        int saved = tleDataService.parseAndSaveJson(json);
        assertEquals(0, saved);
        verify(satelliteRepository, never()).saveAll(any());
        verify(orbitalParametersRepository, never()).saveAll(any());
    }

    @Test
    void testParseAndSaveJson_acceptsStringEncodedNumbersFromSpaceTrack() {
        String json = "[{"
                + "\"NORAD_CAT_ID\":\"51656\"," 
                + "\"OBJECT_NAME\":\"EOS-4\"," 
                + "\"OBJECT_ID\":\"2022-013A\"," 
                + "\"EPOCH\":\"2026-05-29T07:30:52.825824\"," 
                + "\"INCLINATION\":\"97.5110\"," 
                + "\"RA_OF_ASC_NODE\":\"156.2513\"," 
                + "\"ECCENTRICITY\":\"0.00018300\"," 
                + "\"ARG_OF_PERICENTER\":\"98.0617\"," 
                + "\"MEAN_ANOMALY\":\"262.0821\"," 
                + "\"MEAN_MOTION\":\"15.12723206\"," 
                + "\"TLE_LINE1\":\"1 51656U 22013A   26149.31311141  .00002639  00000-0  15437-3 0  9991\"," 
                + "\"TLE_LINE2\":\"2 51656  97.5110 156.2513 0001830  98.0617 262.0821 15.12723206236634\"," 
                + "\"OBJECT_TYPE\":\"PAYLOAD\""
                + "}]";

        Satellite sat = new Satellite(1L, "EOS-4", "2022-013A", 51656L);
        when(satelliteRepository.findByNoradCatIdIn(anyCollection())).thenReturn(List.of(sat));
        when(orbitalParametersRepository.findEpochKeysByNoradCatIdIn(anySet())).thenReturn(Set.of());
        when(orbitalParametersRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int saved = tleDataService.parseAndSaveJson(json);

        assertEquals(1, saved);
        @SuppressWarnings("unchecked")
        var noradCaptor = org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(satelliteRepository).findByNoradCatIdIn(noradCaptor.capture());
        assertTrue(noradCaptor.getValue().contains(51656L));
        verify(orbitalParametersRepository, times(1)).saveAll(any());
        verify(satelliteRepository, never()).saveAll(any());
    }

    @Test
    void testUpdateTleDoesNotFallbackOnEmptyDelta() {
        OrbitalParameters last = mock(OrbitalParameters.class);
        when(last.getFetchedAt()).thenReturn(LocalDateTime.now().minusHours(1));
        when(orbitalParametersRepository.findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L))
                .thenReturn(last);
        when(spaceTrackService.downloadDeltaTle(any())).thenReturn(DeltaFetchResult.successEmpty());

        tleDataService.updateTle();

        verify(spaceTrackService, times(1)).downloadDeltaTle(any());
        verify(celestrakService, never()).fetchAndSaveStations();
        verify(orbitalParametersRepository, never()).save(any());
    }

    @Test
    void testUpdateTleFallsBackOnErrorDelta() {
        OrbitalParameters last = mock(OrbitalParameters.class);
        when(last.getFetchedAt()).thenReturn(LocalDateTime.now().minusHours(1));
        when(orbitalParametersRepository.findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L))
                .thenReturn(last);
        when(spaceTrackService.downloadDeltaTle(any())).thenReturn(DeltaFetchResult.error());

        tleDataService.updateTle();

        verify(spaceTrackService, times(1)).downloadDeltaTle(any());
        verify(celestrakService, times(1)).fetchAndSaveStations();
    }
}
