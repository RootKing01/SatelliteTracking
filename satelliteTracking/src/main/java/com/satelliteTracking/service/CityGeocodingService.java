package com.satelliteTracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.satelliteTracking.model.ObserverLocation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class CityGeocodingService {

    private static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";

    private final WebClient webClient;

    public CityGeocodingService() {
        this.webClient = WebClient.builder()
            .baseUrl(NOMINATIM_BASE_URL)
            .defaultHeader("User-Agent", "SatelliteTracker/1.0 (sightings)")
            .build();
    }

    public ObserverLocation resolveCity(String city) {
        String normalizedCity = city == null ? "" : city.trim();
        if (normalizedCity.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
        }

        String encodedCity = UriUtils.encode(normalizedCity, StandardCharsets.UTF_8);

        try {
            JsonNode response = webClient.get()
                .uri("/search?format=jsonv2&limit=1&q=" + encodedCity)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(5));

            if (response == null || !response.isArray() || response.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
            }

            JsonNode first = response.get(0);
            double latitude = Double.parseDouble(first.path("lat").asText("NaN"));
            double longitude = Double.parseDouble(first.path("lon").asText("NaN"));
            String displayName = first.path("display_name").asText(normalizedCity);

            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
            }

            return new ObserverLocation(latitude, longitude, 30.0, displayName);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore, dati non compatibili");
        }
    }
}
