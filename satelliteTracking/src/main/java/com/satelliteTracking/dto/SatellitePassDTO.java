package com.satelliteTracking.dto;

import java.time.LocalDateTime;

/**
 * DTO che rappresenta un passaggio visibile di un satellite
 */
public record SatellitePassDTO(
    Long satelliteId,
    String satelliteName,
    LocalDateTime riseTime,
    LocalDateTime maxElevationTime,
    LocalDateTime setTime,
    double maxElevation,
    double riseAzimuth,
    double setAzimuth,
    double maxDistance,
    boolean isVisible,
    // Nuovi campi per visibilità avanzata
    boolean isSunlit,
    String visibility,  // "excellent", "good", "fair", "poor"
    String observingCondition, // "night", "twilight", "daylight"
    double estimatedMagnitude,
    double satelliteAltitudeKm
) {
    /**
     * Durata del passaggio in secondi
     */
    public long getDurationSeconds() {
        return java.time.Duration.between(riseTime, setTime).getSeconds();
    }
    
    /**
     * Direzione cardinale del sorgere
     */
    public String getRiseDirection() {
        return azimuthToDirection(riseAzimuth);
    }
    
    /**
     * Direzione cardinale del tramonto
     */
    public String getSetDirection() {
        return azimuthToDirection(setAzimuth);
    }
    
    /**
     * Descrizione testuale per individuare il satellite
     */
    public String getViewingTips() {
        StringBuilder tips = new StringBuilder();
        tips.append("Cerca il satellite verso ").append(getRiseDirection());
        tips.append(" (azimuth ").append(String.format("%.1f", riseAzimuth)).append("°). ");
        
        if (maxElevation > 60) {
            tips.append("Passerà quasi sopra la tua testa! ");
        } else if (maxElevation > 30) {
            tips.append("Sarà ben visibile alto nel cielo. ");
        } else {
            tips.append("Sarà basso sull'orizzonte. ");
        }
        
        if (isSunlit && observingCondition.equals("night")) {
            tips.append("Ottima visibilità: satellite illuminato su cielo scuro.");
        } else if (observingCondition.equals("twilight")) {
            tips.append("Visibile durante il crepuscolo.");
        } else {
            tips.append("Difficile da vedere (cielo troppo luminoso).");
        }
        
        return tips.toString();
    }
    
    private static String azimuthToDirection(double azimuth) {
        if (azimuth < 22.5 || azimuth >= 337.5) return "N";
        if (azimuth < 67.5) return "NE";
        if (azimuth < 112.5) return "E";
        if (azimuth < 157.5) return "SE";
        if (azimuth < 202.5) return "S";
        if (azimuth < 247.5) return "SW";
        if (azimuth < 292.5) return "W";
        return "NW";
    }
}
