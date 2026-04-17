package com.satelliteTracking.service;

import com.satelliteTracking.model.ObserverLocation;
import net.iakovlev.timeshape.TimeZoneEngine;
import org.orekit.errors.OrekitException;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class PassTimeService {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final TimeZoneEngine TIME_ZONE_ENGINE = TimeZoneEngine.initialize();

    public AbsoluteDate nowUtc() {
        return new AbsoluteDate(new Date(), TimeScalesFactory.getUTC());
    }

    public LocalDateTime toLocalDateTime(AbsoluteDate absoluteDate, ZoneId zoneId) {
        try {
            return LocalDateTime.ofInstant(
                absoluteDate.toDate(TimeScalesFactory.getUTC()).toInstant(),
                zoneId
            );
        } catch (OrekitException ex) {
            // Fallback for test/runtime environments where UTC-TAI history is not loaded.
            return LocalDateTime.ofInstant(
                absoluteDate.toDate(TimeScalesFactory.getTAI()).toInstant(),
                zoneId
            );
        }
    }

    public LocalDateTime nowForObserver(ObserverLocation observerLocation) {
        return LocalDateTime.now(resolveOutputZone(observerLocation));
    }

    public ZoneId resolveOutputZone(ObserverLocation observerLocation) {
        if (observerLocation == null) {
            return SYSTEM_ZONE;
        }

        return TIME_ZONE_ENGINE.query(observerLocation.getLatitude(), observerLocation.getLongitude())
            .orElseGet(() -> zoneFromLocationName(observerLocation.getLocationName()));
    }

    private ZoneId zoneFromLocationName(String locationName) {
        if (locationName == null || locationName.isBlank()) {
            return SYSTEM_ZONE;
        }

        String normalized = locationName.trim();
        if (normalized.contains("/")) {
            try {
                return ZoneId.of(normalized);
            } catch (DateTimeException ignored) {
                // Fallback to system zone
            }
        }

        String lower = normalized.toLowerCase();
        if (lower.contains("italia") || lower.contains("italy")) {
            return ZoneId.of("Europe/Rome");
        }

        return SYSTEM_ZONE;
    }
}
