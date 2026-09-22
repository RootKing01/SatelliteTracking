package com.satelliteTracking.util;

import com.satelliteTracking.model.Satellite;

import java.util.regex.Pattern;
import java.util.Set;

public final class SatelliteTypeNormalizer {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> FRONTEND_GROUPS = Set.of(
        "stations",
        "starlink",
        "oneweb",
        "iridium-next",
        "spire",
        "gps-ops",
        "galileo",
        "glonass-ops",
        "beidou",
        "sbas",
        "science",
        "weather",
        "planet",
        "radar",
        "geo",
        "amateur",
        "cubesat",
        "education",
        "engineering",
        "military",
        "space-missions"
    );

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
        if (cleaned.isBlank() || isPendingToken(cleaned)) return "unknown";
        return canonicalizeToken(cleaned);
    }

    public static String canonicalizeSatelliteType(Satellite satellite) {
        if (satellite == null) return "";

        String satelliteType = satellite.getSatelliteType();
        if (satelliteType != null && !satelliteType.isBlank()) {
            String normalizedSatelliteType = canonicalizeType(satelliteType);
            if (isFrontendGroup(normalizedSatelliteType)) {
                return normalizedSatelliteType;
            }
        }

        String raw = satellite.getObjectTypeRaw();
        if (isPendingType(raw)) {
            String inferred = canonicalizeType(satellite.getObjectTypeInferred());
            return isFrontendGroup(inferred) ? inferred : "";
        }

        if (raw != null && !raw.isBlank()) {
            String normalizedRaw = canonicalizeType(raw);
            if (!normalizedRaw.isBlank() && !"unknown".equalsIgnoreCase(normalizedRaw)) {
                return normalizedRaw;
            }
        }

        String inferred = canonicalizeType(satellite.getObjectTypeInferred());
        if (isFrontendGroup(inferred)) {
            return inferred;
        }

        return canonicalizeType(satelliteType);
    }

    public static boolean isPendingClassification(Satellite satellite) {
        if (satellite == null) return false;
        String rawType = satellite.getObjectTypeRaw();
        return isPendingType(rawType);
    }

    public static String resolveEffectiveType(Satellite satellite) {
        if (satellite == null) {
            return "unknown";
        }

        String satelliteType = canonicalizeType(satellite.getSatelliteType());
        if (isFrontendGroup(satelliteType)) {
            return satelliteType;
        }

        String raw = satellite.getObjectTypeRaw();
        if (!isPendingType(raw)) {
            String normalizedRaw = canonicalizeType(raw);
            if (!normalizedRaw.isBlank() && !"unknown".equalsIgnoreCase(normalizedRaw)) {
                return normalizedRaw;
            }
        }

        String inferred = canonicalizeType(satellite.getObjectTypeInferred());
        if (!inferred.isBlank() && !"unknown".equalsIgnoreCase(inferred)) {
            return inferred;
        }

        if (!satelliteType.isBlank()) {
            return satelliteType;
        }

        return "unknown";
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

    private static boolean isFrontendGroup(String token) {
        return token != null && FRONTEND_GROUPS.contains(token.toLowerCase());
    }

    private static boolean isPendingToken(String cleaned) {
        return cleaned.isBlank()
            || "unknown".equalsIgnoreCase(cleaned)
            || cleaned.startsWith("tba")
            || cleaned.contains("to be assigned")
            || cleaned.contains("to be determined");
    }

    private static boolean isPendingType(String input) {
        return isPendingToken(normalizeToken(input));
    }
}