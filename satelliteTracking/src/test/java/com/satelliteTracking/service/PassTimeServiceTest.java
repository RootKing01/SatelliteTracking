package com.satelliteTracking.service;

import com.satelliteTracking.model.ObserverLocation;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PassTimeServiceTest {

    private final PassTimeService service = new PassTimeService();

    @Test
    void shouldResolveRomeZoneForItalianCoordinates() {
        ObserverLocation italy = new ObserverLocation(41.01, 14.30, 30.0, "Caserta, Italia");

        ZoneId zone = service.resolveOutputZone(italy);

        assertEquals(ZoneId.of("Europe/Rome"), zone);
    }

    @Test
    void shouldFallbackToSystemZoneForNullObserver() {
        ZoneId zone = service.resolveOutputZone(null);

        assertEquals(ZoneId.systemDefault(), zone);
    }

    @Test
    void shouldConvertAbsoluteDateToExpectedLocalTime() {
        AbsoluteDate absoluteDate = new AbsoluteDate(
            Date.from(Instant.parse("2026-01-15T12:00:00Z")),
            TimeScalesFactory.getUTC()
        );

        LocalDateTime localDateTime = service.toLocalDateTime(absoluteDate, ZoneId.of("Europe/Rome"));

        assertEquals(13, localDateTime.getHour());
        assertEquals(0, localDateTime.getMinute());
    }
}
