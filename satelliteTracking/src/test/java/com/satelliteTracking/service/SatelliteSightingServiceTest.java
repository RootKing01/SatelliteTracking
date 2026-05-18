package com.satelliteTracking.service;

import com.satelliteTracking.dto.SatellitePositionDTO;
import com.satelliteTracking.dto.SatelliteSightingCreateRequestDTO;
import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.repository.SatelliteSightingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.orekit.time.AbsoluteDate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatelliteSightingServiceTest {

    @Mock
    private SatelliteRepository satelliteRepository;

    @Mock
    private SatelliteSightingRepository satelliteSightingRepository;

    @Mock
    private SatellitePassService satellitePassService;

    @Mock
    private OrbitalParametersRepository orbitalParametersRepository;

    @Mock
    private PassTimeService passTimeService;

    @Mock
    private AuthService authService;

    @Mock
    private CityGeocodingService cityGeocodingService;

    @Mock
    private SatellitePositionService satellitePositionService;

    @InjectMocks
    private SatelliteSightingService satelliteSightingService;

    @Test
    void registerSightingShouldRejectDaylightConfirmations() {
        AppUser user = new AppUser();
        user.setId(7L);

        Satellite satellite = new Satellite();
        satellite.setId(42L);
        satellite.setObjectName("TESTSAT");
        satellite.setObjectId("1998-067A");
        satellite.setNoradCatId(25544L);
        satellite.setSatelliteType("LEO");

        OrbitalParameters params = new OrbitalParameters();
        params.setMeanMotion(15.0);
        params.setSatellite(satellite);

        LocalDateTime now = LocalDateTime.of(2026, 5, 18, 12, 0);

        when(authService.requireAuthenticatedUser()).thenReturn(user);
        when(satelliteRepository.findById(42L)).thenReturn(Optional.of(satellite));
        when(orbitalParametersRepository.findTopBySatelliteOrderByFetchedAtDesc(satellite)).thenReturn(params);
        when(passTimeService.nowForObserver(any())).thenReturn(now);
        when(passTimeService.nowUtc()).thenReturn(AbsoluteDate.J2000_EPOCH);
        when(satelliteSightingRepository.findByUserIdAndSatelliteIdAndSightedAtBetween(any(), any(), any(), any()))
            .thenReturn(List.of());
        when(satellitePositionService.computeObservation(any(), any(), any(), any(), any(), any()))
            .thenReturn(new SatellitePositionDTO(
                42L,
                "TESTSAT",
                "LEO",
                "1998-067A",
                25544L,
                now,
                0.0,
                0.0,
                400.0,
                6778.0,
                15.0,
                96.0,
                1.6,
                27600.0,
                90.0,
                null,
                41.9,
                12.5,
                30.0,
                35.0,
                120.0,
                800.0,
                1.0,
                true,
                "GOOD",
                "daylight"
            ));

        SatelliteSightingCreateRequestDTO request = new SatelliteSightingCreateRequestDTO(
            42L,
            null,
            41.9,
            12.5,
            30.0
        );

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> satelliteSightingService.registerSighting(request)
        );

        assertEquals("Errore, satellite non confermabile in pieno giorno", exception.getReason());
        verify(satelliteSightingRepository, never()).save(any());
    }
}