package com.satelliteTracking.controller;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.Satellite;
import com.satelliteTracking.model.TelegramSubscription;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.service.PassTimeService;
import com.satelliteTracking.service.SatellitePassService;
import com.satelliteTracking.service.TelegramNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SatellitePassControllerTest {

    private final SatelliteRepository satelliteRepository = Mockito.mock(SatelliteRepository.class);
    private final SatellitePassService satellitePassService = Mockito.mock(SatellitePassService.class);
    private final TelegramNotificationService telegramNotificationService = Mockito.mock(TelegramNotificationService.class);
    // FIX 1: aggiunto mock di PassTimeService richiesto dal costruttore
    private final PassTimeService passTimeService = Mockito.mock(PassTimeService.class);

    private final SatellitePassController controller = new SatellitePassController(
        satelliteRepository,
        satellitePassService,
        telegramNotificationService,
        passTimeService,   // FIX 1: aggiunto passTimeService
        41.01,
        14.30,
        30.0
    );

    @Test
    void shouldReturnIssPassesByName() throws Exception {
        Satellite iss = new Satellite();
        iss.setId(25544L);
        iss.setObjectName("ISS (ZARYA)");

        SatellitePassDTO pass = new SatellitePassDTO(
            25544L,
            "ISS (ZARYA)",
            LocalDateTime.of(2026, 4, 8, 19, 0),
            LocalDateTime.of(2026, 4, 8, 19, 5),
            LocalDateTime.of(2026, 4, 8, 19, 10),
            68.5,
            310.0,
            90.0,
            140.0,
            600.0,
            true,
            true,
            "excellent",
            "night",
            -1.5,
            420.0
        );

        when(satelliteRepository.findFirstByObjectNameContainingIgnoreCaseOrderByIdAsc("ISS"))
            .thenReturn(Optional.of(iss));
        when(satellitePassService.calculatePasses(eq(25544L), anyInt()))
            .thenReturn(List.of(pass));

        ResponseEntity<?> response = controller.getSatellitePassesByName("ISS", 6);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(List.class, response.getBody());
        @SuppressWarnings("unchecked")
        List<SatellitePassDTO> body = (List<SatellitePassDTO>) response.getBody();
        assertNotNull(body);
        assertEquals(1, body.size());
        assertEquals(25544L, body.get(0).satelliteId());
    }

    @Test
    void shouldReturnNotFoundWhenNameDoesNotExist() throws Exception {
        when(satelliteRepository.findFirstByObjectNameContainingIgnoreCaseOrderByIdAsc("UNKNOWN"))
            .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getSatellitePassesByName("UNKNOWN", 24);

        assertEquals(404, response.getStatusCode().value());

        Mockito.verifyNoInteractions(satellitePassService);
    }

    @Test
    void shouldUseIssShortcutEndpoint() throws Exception {
        Satellite iss = new Satellite();
        iss.setId(25544L);
        iss.setObjectName("ISS (ZARYA)");

        when(satelliteRepository.findFirstByObjectNameContainingIgnoreCaseOrderByIdAsc("ISS"))
            .thenReturn(Optional.of(iss));
        when(satellitePassService.calculatePasses(eq(25544L), anyInt()))
            .thenReturn(List.of());

        ResponseEntity<?> response = controller.getIssPasses(12);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void shouldReturnDetailedUpcomingPassesForValidRequest() {
        when(passTimeService.nowUtcDateTime()).thenReturn(LocalDateTime.now());
        when(satellitePassService.findVisibleUpcomingPasses(
            anyInt(), anyDouble(), any(ObserverLocation.class), eq("any"), eq(6.0)
        )).thenReturn(List.of());

        ResponseEntity<?> response = controller.getUpcomingPassesDetailed(3, 41.01, 14.3, 30.0, 30.0);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void shouldSendNotificationForUpcomingPasses() {
        LocalDateTime riseTime = LocalDateTime.now().plusMinutes(10);

        SatellitePassDTO pass = new SatellitePassDTO(
            25544L,
            "ISS",
            riseTime,
            LocalDateTime.now().plusMinutes(12),
            LocalDateTime.now().plusMinutes(15),
            60.0,
            300.0,
            120.0,
            150.0,
            650.0,
            true,
            true,
            "good",
            "night",
            1.2,
            420.0
        );

        TelegramSubscription sub = new TelegramSubscription();
        sub.setNotificationsEnabled(true);
        sub.setLastNotificationSent(LocalDateTime.now().minusMinutes(40));
        sub.setLocationName("Test");

        when(passTimeService.nowUtcDateTime()).thenReturn(LocalDateTime.now());
        when(satellitePassService.findVisibleUpcomingPasses(6, 30.0)).thenReturn(List.of(pass));
        when(telegramNotificationService.getAllSubscriptions()).thenReturn(Collections.singletonList(sub));

        ResponseEntity<List<SatellitePassDTO>> response = controller.getUpcomingPasses(6);

        assertEquals(200, response.getStatusCode().value());
        // FIX 2: la firma attuale è (TelegramSubscription, SatellitePassDTO)
        Mockito.verify(telegramNotificationService).sendNotificationToUser(
            eq(sub),
            eq(pass)
        );
    }
}