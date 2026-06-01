package com.satelliteTracking.util;

import com.satelliteTracking.model.Satellite;

import java.util.regex.Pattern;

public final class SatelliteTypeNormalizer {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private SatelliteTypeNormalizer() {
    }

    public static String normalizeToken(String input) {
        if (input == null) return "";
        String lower = input.toLowerCase().trim();
        if (lower.isBlank()) return "";
        String cleaned = NON_ALNUM.matcher(lower).replaceAll(" ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    public static String canonicalizeType(String input) {
        String cleaned = normalizeToken(input);
        if (cleaned.isBlank()) return "unknown";
        return canonicalizeToken(cleaned);
    }

    public static String canonicalizeSatelliteType(Satellite satellite) {
        if (satellite == null) return "";

        String satelliteType = satellite.getSatelliteType();
        if (satelliteType != null && !satelliteType.isBlank()) {
            String normalizedSatelliteType = canonicalizeType(satelliteType);
            if ("starlink".equalsIgnoreCase(normalizedSatelliteType)) {
                return "starlink";
            }
        }

        String raw = satellite.getObjectTypeRaw();
        if (raw == null || raw.isBlank() || "UNKNOWN".equalsIgnoreCase(raw)) {
            raw = satellite.getObjectTypeInferred();
        }
        if (raw == null || raw.isBlank()) {
            raw = satelliteType;
        }

        return canonicalizeType(raw);
    }

    public static boolean isPendingClassification(Satellite satellite) {
        if (satellite == null) return false;
        String rawType = satellite.getObjectTypeRaw();
        return rawType == null || rawType.isBlank() || "UNKNOWN".equalsIgnoreCase(rawType);
    }

    private static String canonicalizeToken(String cleaned) {
        String c = cleaned.toLowerCase();

        if (c.contains("space mission") || c.contains("space missions") || c.contains("spacemissions")) return "space-missions";

        if (c.contains("station") || c.contains("iss") || c.contains("space station")) return "stations";

        if (c.contains("starlink")) return "starlink";
        if (c.contains("oneweb")) return "oneweb";
        if (c.contains("iridium")) return "iridium-NEXT";
        if (c.contains("spire")) return "spire";
        if (c.contains("gps")) return "gps-ops";
        if (c.contains("galileo")) return "galileo";
        if (c.contains("glonass")) return "glonass-ops";
        if (c.contains("beidou")) return "beidou";
        if (c.contains("sbas")) return "sbas";

        if (c.contains("science")) return "science";
        if (c.contains("weather")) return "weather";
        if (c.contains("planet")) return "planet";
        if (c.contains("radar")) return "radar";

        if (c.contains("geo")) return "geo";
        if (c.contains("amateur")) return "amateur";
        if (c.contains("cube") || c.contains("cubesat")) return "cubesat";
        if (c.contains("education")) return "education";
        if (c.contains("engineer")) return "engineering";
        if (c.contains("military") || c.contains("mil")) return "military";

        if (c.contains("debris") || c.contains("fragment") || c.contains("frag") || c.contains("breakup") || c.contains("deb")) return "debris";
        if (c.contains("payload")) return "payload";

        if (c.contains("rocket") || c.contains("stage") || c.contains("booster") || c.contains("upper stage") || c.contains("rocket body") || c.contains("rb")) return "space-rocket";

        return c;
    }
}