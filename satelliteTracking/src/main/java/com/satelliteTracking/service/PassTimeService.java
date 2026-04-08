package com.satelliteTracking.service;

import com.satelliteTracking.model.ObserverLocation;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class PassTimeService {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    public AbsoluteDate nowUtc() {
        return new AbsoluteDate(new Date(), TimeScalesFactory.getUTC());
    }

    public LocalDateTime toLocalDateTime(AbsoluteDate absoluteDate, ZoneId zoneId) {
        return LocalDateTime.ofInstant(
            absoluteDate.toDate(TimeScalesFactory.getUTC()).toInstant(),
            zoneId
        );
    }

    public LocalDateTime nowForObserver(ObserverLocation observerLocation) {
        return LocalDateTime.now(resolveOutputZone(observerLocation));
    }

    public ZoneId resolveOutputZone(ObserverLocation observerLocation) {
        if (observerLocation == null) {
            return SYSTEM_ZONE;
        }

        String locationName = observerLocation.getLocationName();
        if (locationName != null) {
            String normalized = locationName.toLowerCase();
            if (normalized.contains("italia") || normalized.contains("italy")) {
                return ZoneId.of("Europe/Rome");
            }
        }

        if (isLikelyInItaly(observerLocation)) {
            return ZoneId.of("Europe/Rome");
        }

        return SYSTEM_ZONE;
    }

    private boolean isLikelyInItaly(ObserverLocation observerLocation) {
        double lat = observerLocation.getLatitude();
        double lon = observerLocation.getLongitude();
        return lat >= 35.0 && lat <= 48.0 && lon >= 6.0 && lon <= 19.0;
    }
}
