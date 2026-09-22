package com.satelliteTracking.controller;

import com.satelliteTracking.config.OrekitConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final OrekitConfig orekitConfig;
    private final JdbcTemplate jdbcTemplate;

    public SystemStatusController(OrekitConfig orekitConfig,
                                  JdbcTemplate jdbcTemplate) {
        this.orekitConfig = orekitConfig;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/orekit-status")
    public Map<String, Object> getOrekitStatus() {
        boolean loaded = orekitConfig.isOrekitDataLoaded();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orekitDataLoaded", loaded);
        response.put("orekitDataPath", orekitConfig.getOrekitDataPath());
        response.put("status", loaded ? "loaded" : "fallback");
        response.put("checkedAt", Instant.now().toString());
        return response;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        boolean databaseUp = isDatabaseUp();
        boolean orekitLoaded = orekitConfig.isOrekitDataLoaded();

        String overallStatus;
        HttpStatus httpStatus;

        if (!databaseUp) {
            overallStatus = "DOWN";
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
        } else if (!orekitLoaded) {
            overallStatus = "DEGRADED";
            httpStatus = HttpStatus.OK;
        } else {
            overallStatus = "UP";
            httpStatus = HttpStatus.OK;
        }

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("api", "UP");
        components.put("database", databaseUp ? "UP" : "DOWN");
        components.put("orekit", orekitLoaded ? "UP" : "FALLBACK");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", overallStatus);
        response.put("checkedAt", Instant.now().toString());
        response.put("components", components);
        response.put("orekitDataPath", orekitConfig.getOrekitDataPath());

        return ResponseEntity.status(httpStatus).body(response);
    }

    private boolean isDatabaseUp() {
        try {
            Integer ping = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ping != null && ping == 1;
        } catch (Exception ex) {
            return false;
        }
    }
}
