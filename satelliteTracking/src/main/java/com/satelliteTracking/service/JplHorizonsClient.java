package com.satelliteTracking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Client per NASA JPL Horizons API
 * Fornisce dati di effemeridi per oggetti spaziali (sonde, veicoli spaziali, missioni)
 * Documentation: https://ssd-api.jpl.nasa.gov/doc/horizons.html
 */
@Service
public class JplHorizonsClient {

    private static final Logger log = LoggerFactory.getLogger(JplHorizonsClient.class);
    private static final String JPL_HORIZONS_API = "https://ssd.jpl.nasa.gov/api/horizons.api";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public JplHorizonsClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Definizioni di oggetti spaziali disponibili
     */
    public enum SpaceMissionObject {
        ARTEMIS_ORION("artemis_orion", "Artemis Orion Capsule", "space-missions"),
        ARTEMIS_GATEWAY("artemis_gateway", "Lunar Gateway", "space-missions"),
        CAPSTONE("capstone", "CAPSTONE Lunar Relay", "space-missions"),
        LUNAR_HLS("lunar_hls", "Lunar HLS (Starship)", "space-missions"),
        ORION_2024("orion_2024", "Orion (2024)", "space-missions");

        private final String horizonsId;
        private final String displayName;
        private final String satelliteType;

        SpaceMissionObject(String horizonsId, String displayName, String satelliteType) {
            this.horizonsId = horizonsId;
            this.displayName = displayName;
            this.satelliteType = satelliteType;
        }

        public String getHorizonsId() { return horizonsId; }
        public String getDisplayName() { return displayName; }
        public String getSatelliteType() { return satelliteType; }
    }

    /**
     * Fetcha posizione di un oggetto spaziale per una data/ora specifica
     * @param mission Oggetto spaziale (Artemis, sonda, etc.)
     * @param observerLatitude Latitudine observer (es. 51.5074 per Londra)
     * @param observerLongitude Longitudine observer (es. -0.1278)
     * @param observerElevation Elevazione observer in km
     * @param dateTime Data/ora della posizione desiderata (UTC)
     * @return Mappa con RA, DEC, distance, velocity, etc.
     */
    public Map<String, Object> getObjectPosition(
            SpaceMissionObject mission,
            double observerLatitude,
            double observerLongitude,
            double observerElevation,
            LocalDateTime dateTime) {

        try {
            String startTime = dateTime.format(ISO_FORMATTER);
            String stopTime = dateTime.plusMinutes(1).format(ISO_FORMATTER);
            
            // Use VECTORS+CSV format for stable machine parsing.
            String url = String.format(
                "%s?format=json&MAKE_EPHEM='YES'&EPHEM_TYPE='VECTORS'&CENTER='500@399'&CSV_FORMAT='YES'" +
                "&COMMAND='%s'&START_TIME='%s'&STOP_TIME='%s'&STEP_SIZE='1 m'",
                JPL_HORIZONS_API,
                mission.getHorizonsId(),
                startTime,
                stopTime
            );

            log.debug("🌍 JPL Horizons Request: {}", mission.getDisplayName());
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode response = rawResponse == null ? null : objectMapper.readTree(rawResponse);
            
            if (response == null || !response.has("result")) {
                log.warn("⚠️ JPL Horizons returned null for {}", mission.getDisplayName());
                return null;
            }

            String resultText = response.get("result").asText("");
            VectorSample sample = extractFirstVectorSample(resultText);
            if (sample == null) {
                log.warn("⚠️ JPL Horizons no data for {}", mission.getDisplayName());
                return null;
            }
            Map<String, Object> position = new HashMap<>();
            
            position.put("objectName", mission.getDisplayName());
            position.put("objectId", mission.getHorizonsId());
            position.put("timestamp", System.currentTimeMillis());
            position.put("timestamp_date", LocalDateTime.now(ZoneOffset.UTC).toString());

            position.put("distance_km", sample.rangeKm);
            position.put("velocity_km_s", sample.speedKmS);
            position.put("range_rate_km_s", sample.rangeRateKmS);
            position.put("observer_lat", observerLatitude);
            position.put("observer_lon", observerLongitude);
            position.put("observer_elevation_km", observerElevation);
            
            position.put("source", "JPL Horizons API");
            position.put("dataQuality", "ephemeris");
            
            return position;

        } catch (Exception e) {
            log.warn("⚠️ JPL Horizons Error for {}: {}", mission.getDisplayName(), e.getMessage());
            return null;
        }
    }

    /**
     * Fetcha posizione per tutti gli oggetti spaziali disponibili
     */
    public List<Map<String, Object>> getAllSpaceMissionPositions(
            double observerLatitude,
            double observerLongitude,
            double observerElevation) {

        List<Map<String, Object>> positions = new ArrayList<>();
        
        for (SpaceMissionObject mission : SpaceMissionObject.values()) {
            try {
                Map<String, Object> position = getObjectPosition(
                    mission,
                    observerLatitude,
                    observerLongitude,
                    observerElevation,
                    LocalDateTime.now(ZoneId.of("UTC"))
                );
                
                if (position != null) {
                    positions.add(position);
                }
            } catch (Exception e) {
                log.debug("⚠️ Could not fetch {} from JPL Horizons", mission.getDisplayName());
            }
        }
        
        return positions;
    }

    /**
     * Verifica connettività e disponibilità API
     */
    public boolean checkAvailability() {
        try {
            String testUrl = JPL_HORIZONS_API + "?format=json&COMMAND='299'";
            String rawResponse = restTemplate.getForObject(testUrl, String.class);
            JsonNode response = rawResponse == null ? null : objectMapper.readTree(rawResponse);
            return response != null && response.has("result");
        } catch (Exception e) {
            log.warn("⚠️ JPL Horizons API unavailable: {}", e.getMessage());
            return false;
        }
    }

    private VectorSample extractFirstVectorSample(String resultText) {
        if (resultText == null) {
            return null;
        }

        int start = resultText.indexOf("$$SOE");
        int end = resultText.indexOf("$$EOE");
        if (start < 0 || end <= start) {
            return null;
        }

        String body = resultText.substring(start + 5, end).trim();
        if (body.isEmpty()) {
            return null;
        }

        String[] lines = body.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] cols = line.split(",");
            if (cols.length < 11) {
                continue;
            }

            try {
                double vx = Double.parseDouble(cols[5].trim());
                double vy = Double.parseDouble(cols[6].trim());
                double vz = Double.parseDouble(cols[7].trim());
                double rg = Double.parseDouble(cols[9].trim());
                double rr = Double.parseDouble(cols[10].trim());
                double speed = Math.sqrt((vx * vx) + (vy * vy) + (vz * vz));
                return new VectorSample(rg, rr, speed);
            } catch (NumberFormatException ignored) {
                // Continue until we find the first parseable vector row.
            }
        }

        return null;
    }

    private static class VectorSample {
        private final double rangeKm;
        private final double rangeRateKmS;
        private final double speedKmS;

        private VectorSample(double rangeKm, double rangeRateKmS, double speedKmS) {
            this.rangeKm = rangeKm;
            this.rangeRateKmS = rangeRateKmS;
            this.speedKmS = speedKmS;
        }
    }
}
