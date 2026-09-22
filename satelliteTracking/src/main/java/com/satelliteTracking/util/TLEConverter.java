package com.satelliteTracking.util;

import com.satelliteTracking.model.OrbitalParameters;

/**
 * Utility per convertire parametri orbitali in formato TLE (Two-Line Element)
 * 
 * Il TLE è il formato standard per rappresentare orbite satellitari.
 * Formato: 2 linee di 69 caratteri ciascuna
 */
public class TLEConverter {

    /**
     * Converte parametri orbitali in formato TLE line 1
     * 
     * Formato Line 1:
     * 1 NNNNNC NNNNNAAA NNNNN.NNNNNNNN +.NNNNNNNN +NNNNN-N +NNNNN-N N NNNNN
     * 
     * Dove:
     * - Column 01-01: Line number
     * - Column 03-07: Satellite number
     * - Column 08-08: Classification (U=Unclassified)
     * - Column 10-17: International Designator (Launch year)
     * - Column 19-20: Element set epoch (Year)
     * - Column 21-32: Element set epoch (Day of year)
     * - Column 34-43: First derivative of mean motion
     * - Column 45-52: Second derivative of mean motion
     * - Column 54-61: Drag term (B*)
     * - Column 63-63: Ephemeris type
     * - Column 65-68: Element set number
     * - Column 69-69: Checksum
     */
    public static String buildLine1(Long noradId, String epoch) {
        // Semplificazione: creiamo una line 1 di base
        String satNum = String.format("%05d", noradId % 100000);
        String classification = "U";
        String intlDesignator = "00000A  "; // placeholder
        
        // Estrai anno e giorno dall'epoch (formato: 2024-123.45678901)
        String epochFormatted = formatEpoch(epoch);
        
        String line1 = String.format("1 %sU %-8s %s  .00000000  00000-0  00000-0 0    00",
            satNum, intlDesignator, epochFormatted);
        
        // Aggiungi checksum
        int checksum = calculateChecksum(line1);
        line1 += checksum;
        
        return line1;
    }

    /**
     * Converte parametri orbitali in formato TLE line 2
     * 
     * Formato Line 2:
     * 2 NNNNN NNN.NNNN NNN.NNNN NNNNNNN NNN.NNNN NNN.NNNN NN.NNNNNNNNNNNNNN
     * 
     * Dove:
     * - Column 01-01: Line number
     * - Column 03-07: Satellite number
     * - Column 09-16: Inclination (degrees)
     * - Column 18-25: Right Ascension of Ascending Node (degrees)
     * - Column 27-33: Eccentricity (decimal point assumed)
     * - Column 35-42: Argument of Perigee (degrees)
     * - Column 44-51: Mean Anomaly (degrees)
     * - Column 53-63: Mean Motion (revs per day)
     * - Column 64-68: Revolution number at epoch
     * - Column 69-69: Checksum
     */
    public static String buildLine2(Long noradId, OrbitalParameters params) {
        String satNum = String.format("%05d", noradId % 100000);
        
        // Formatta i parametri orbitali
        String inclination = String.format("%8.4f", params.getInclination());
        String raan = String.format("%8.4f", params.getRaOfAscNode());
        
        // Eccentricity senza punto decimale (0.1234567 -> 1234567)
        String eccentricity = String.format("%07d", 
            (int)(params.getEccentricity() * 10000000) % 10000000);
        
        String argPerigee = String.format("%8.4f", params.getArgOfPericenter());
        String meanAnomaly = String.format("%8.4f", params.getMeanAnomaly());
        String meanMotion = String.format("%11.8f", params.getMeanMotion());
        
        String line2 = String.format("2 %s %s %s %s %s %s %s    00",
            satNum, inclination, raan, eccentricity, 
            argPerigee, meanAnomaly, meanMotion);
        
        // Aggiungi checksum
        int checksum = calculateChecksum(line2);
        line2 += checksum;
        
        return line2;
    }

    /**
     * Formatta l'epoch nel formato TLE (YY DDD.DDDDDDDD)
     */
    private static String formatEpoch(String epoch) {
        // L'epoch da Celestrak è già in formato simile
        // Esempio: "2024-02-22T10:30:45.123Z"
        // Dobbiamo convertirlo in: "24045.12345678" (anno giorno.frazione)
        
        try {
            // Parsing semplificato - in produzione usare un parser più robusto
            if (epoch.contains("-")) {
                String[] parts = epoch.split("-");
                String year = parts[0].substring(2); // ultime 2 cifre
                // Per ora usiamo un placeholder
                return year + "045.12345678";
            }
            return epoch;
        } catch (Exception e) {
            return "24001.00000000";
        }
    }

    /**
     * Calcola il checksum per una linea TLE
     * Somma di tutte le cifre, con '-' = 1
     */
    private static int calculateChecksum(String line) {
        int sum = 0;
        for (int i = 0; i < Math.min(line.length(), 68); i++) {
            char c = line.charAt(i);
            if (Character.isDigit(c)) {
                sum += Character.getNumericValue(c);
            } else if (c == '-') {
                sum += 1;
            }
        }
        return sum % 10;
    }

    /**
     * Crea un TLE completo (entrambe le linee)
     */
    public static String[] buildTLE(Long noradId, String satelliteName, OrbitalParameters params) {
        return new String[] {
            satelliteName,
            buildLine1(noradId, params.getEpoch()),
            buildLine2(noradId, params)
        };
    }
}
