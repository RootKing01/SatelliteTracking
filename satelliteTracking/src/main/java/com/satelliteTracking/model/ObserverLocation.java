package com.satelliteTracking.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rappresenta la posizione geografica di un osservatore sulla Terra
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObserverLocation {
    
    /**
     * Latitudine in gradi (positiva a Nord, negativa a Sud)
     * Range: -90 to +90
     */
    private double latitude;
    
    /**
     * Longitudine in gradi (positiva a Est, negativa a Ovest)
     * Range: -180 to +180
     */
    private double longitude;
    
    /**
     * Altitudine in metri sul livello del mare
     */
    private double altitude;
    
    /**
     * Nome della località (opzionale)
     */
    private String locationName;
    
    public ObserverLocation(double latitude, double longitude, double altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }
    
    /**
     * Posizione predefinita: San Marcellino, Caserta, Italia
     */
    public static ObserverLocation sanMarcellino() {
        return new ObserverLocation(41.01, 14.30, 30.0, "San Marcellino, Caserta, Italia");
    }
}
