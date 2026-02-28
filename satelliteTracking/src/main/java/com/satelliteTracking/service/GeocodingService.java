package com.satelliteTracking.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Servizio per convertire città/indirizzi in coordinate geografiche
 * Usa l'API gratuita di OpenStreetMap Nominatim
 */
@Service
public class GeocodingService {
    
    private static final String NOMINATIM_API = "https://nominatim.openstreetmap.org/search";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public GeocodingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Converte un nome di città in coordinate geografiche
     * 
     * @param cityName Nome della città (es: "Roma", "Milano", "San Marcellino")
     * @return Map con "latitude", "longitude", "displayName" o errore
     */
    public Map<String, Object> geocodeCity(String cityName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (cityName == null || cityName.trim().isEmpty()) {
                result.put("error", "Nome città non valido");
                return result;
            }
            
            // Prepara headers con User-Agent (richiesto da Nominatim)
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SatelliteTracker/1.0 (https://github.com/satellite-tracker)");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Prepara query per Nominatim - prima prova globale, poi fallback su Italia
            String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
            String url = String.format("%s?q=%s&format=json&limit=1&addressdetails=1",
                NOMINATIM_API, encodedCity);
            
            System.out.println("🌍 Geocoding query: '" + cityName + "' → URL: " + url);
            
            // Chiama API Nominatim con headers
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String response = responseEntity.getBody();
            
            System.out.println("📡 Nominatim response: " + (response != null ? response.substring(0, Math.min(200, response.length())) : "null"));
            
            if (response == null || response.equals("[]") || response.trim().isEmpty()) {
                // Fallback: prova con filtro Italia se non ci sono risultati globali
                url = String.format("%s?q=%s&format=json&limit=1&countrycodes=it&addressdetails=1",
                    NOMINATIM_API, encodedCity);
                System.out.println("🔄 Retry con countrycodes=it: " + url);
                responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                response = responseEntity.getBody();
            }
            
            if (response == null || response.equals("[]") || response.trim().isEmpty()) {
                result.put("error", "Città non trovata: " + cityName);
                return result;
            }
            
            // Parsa il JSON
            JsonNode root = objectMapper.readTree(response);
            
            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);
                
                double latitude = first.get("lat").asDouble();
                double longitude = first.get("lon").asDouble();
                String displayName = first.get("display_name").asText();
                
                result.put("success", true);
                result.put("latitude", latitude);
                result.put("longitude", longitude);
                result.put("displayName", displayName);
                result.put("altitude", 100); // Default altitude in meters
                result.put("city", cityName);
                
                System.out.println("✅ Geocoding riuscito: " + cityName + " → (" + latitude + ", " + longitude + ")");
            } else {
                result.put("error", "Nessun risultato trovato per: " + cityName);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Errore durante il geocoding di '" + cityName + "': " + e.getMessage());
            result.put("error", "Errore durante il geocoding: " + e.getMessage());
        }
        
        return result;
    }
}
