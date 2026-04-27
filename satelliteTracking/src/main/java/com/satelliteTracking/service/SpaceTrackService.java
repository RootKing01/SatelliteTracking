package com.satelliteTracking.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.core.publisher.Mono;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SpaceTrackService {

    private static final Logger log = LoggerFactory.getLogger(SpaceTrackService.class);

    private static final DateTimeFormatter ST_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebClient webClient;

    @Value("${spacetrack.username}")
    private String username;

    @Value("${spacetrack.password}")
    private String password;

    private volatile String sessionCookie;
    private volatile LocalDateTime sessionCreatedAt;
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    public SpaceTrackService(WebClient.Builder builder) {
        log.info("🔧 Inizializzazione SpaceTrackService");

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(120))
                                .addHandlerLast(new WriteTimeoutHandler(120))
                );

        this.webClient = builder
                .baseUrl("https://www.space-track.org")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @PostConstruct
    public void login() {
        performLogin();
    }

    private synchronized void performLogin() {
        try {
            log.info("🔐 Tentativo login Space-Track...");
            List<String> cookies = webClient.post()
                    .uri("/ajaxauth/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue("identity=" + username + "&password=" + password)
                    .retrieve()
                    .toEntity(String.class)
                    .map(resp -> resp.getHeaders().get(HttpHeaders.SET_COOKIE))
                    .block(Duration.ofSeconds(30));

            if (cookies != null && !cookies.isEmpty()) {
                sessionCookie = cookies.get(0).split(";")[0];
                sessionCreatedAt = LocalDateTime.now();
                log.info("✅ Space-Track login SUCCESSO");
            } else {
                log.error("❌ Login fallito - nessun cookie ricevuto");
                sessionCookie = null;
                sessionCreatedAt = null;
            }
        } catch (Exception e) {
            log.error("❌ Errore login: {}", e.getMessage(), e);
            sessionCookie = null;
            sessionCreatedAt = null;
        }
    }

    private void ensureLogin() {
        boolean expired = sessionCreatedAt != null &&
                Duration.between(sessionCreatedAt, LocalDateTime.now()).compareTo(SESSION_TTL) > 0;
        if (sessionCookie == null || expired) {
            log.warn("⚠️ Sessione assente o scaduta, re-login...");
            performLogin();
        }
    }

    private boolean isHtmlResponse(String response) {
        if (response == null || response.isBlank()) return false;
        String t = response.trim();
        return t.startsWith("<!DOCTYPE") || t.startsWith("<html") || t.startsWith("<!doctype");
    }

    private String formatForSpaceTrack(LocalDateTime dt) {
        return dt.format(ST_FORMATTER);
    }

    // =============================
    // 🔥 METODI PUBBLICI (INVARIATI)
    // =============================

    public String downloadDeltaTle(LocalDateTime lastFetchedAt) {
        ensureLogin();

        // ⬇️ ORA USA CHUNK TEMPORALI
        return downloadDeltaChunked(lastFetchedAt, LocalDateTime.now(), false);
    }

    public String downloadDeltaTleLeoOnly(LocalDateTime lastFetchedAt) {
        ensureLogin();

        // ⬇️ ORA USA CHUNK TEMPORALI
        return downloadDeltaChunked(lastFetchedAt, LocalDateTime.now(), true);
    }

    // =============================
    // 🔥 NUOVO: CHUNK TEMPORALI
    // =============================
    private String downloadDeltaChunked(LocalDateTime from, LocalDateTime to, boolean leoOnly) {

        StringBuilder finalResult = new StringBuilder();
        LocalDateTime cursor = from;
        int chunkIndex = 0;

        while (cursor.isBefore(to)) {

            chunkIndex++;

            LocalDateTime next = cursor.plusHours(2);
            if (next.isAfter(to)) next = to;

            String fromStr = formatForSpaceTrack(cursor);
            String toStr = formatForSpaceTrack(next);

            log.info("⏱️ Time chunk #{} → {} → {}", chunkIndex, fromStr, toStr);

            String chunk = downloadGpHistoryInternal(fromStr, toStr, leoOnly, false);

            if (chunk == null) {
                log.warn("⚠️ Chunk temporale fallito, skip");
                cursor = next;
                continue;
            }

            if (!chunk.equals("[]")) {
                if (finalResult.length() > 0) {
                    finalResult.setLength(finalResult.length() - 1);
                    finalResult.append(",");
                    chunk = chunk.substring(1);
                }
                finalResult.append(chunk);
            }

            cursor = next;

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return finalResult.length() == 0 ? "[]" : finalResult.toString();
    }

    // =============================
    // 🔥 CORE MODIFICATO
    // =============================
    private String downloadGpHistoryInternal(String from, String to, boolean leoOnly, boolean isRetry) {

        StringBuilder allData = new StringBuilder();
        int offset = 0;
        final int limit = 200; // ⬅️ RIDOTTO
        int totalEntries = 0;
        int chunkNumber = 0;
        String type = leoOnly ? "LEO" : "FULL";

        while (true) {

            chunkNumber++;
            log.info("📦 GP_HISTORY {} chunk #{} → offset={}, limit={}", type, chunkNumber, offset, limit);

            long startTime = System.currentTimeMillis();
            int currentOffset = offset;
            String creationDateRange = from + "--" + to;

            String chunk = null;

            // 🔁 RETRY
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    chunk = webClient.get()
                            .uri(uriBuilder -> {
                                var builder = uriBuilder
                                        .path("/basicspacedata/query/class/gp_history")
                                        .queryParam("CREATION_DATE", creationDateRange)
                                        .queryParam("DECAY_DATE", "null-val")
                                        .queryParam("orderby", "CREATION_DATE asc")
                                        .queryParam("limit", limit)
                                        .queryParam("offset", currentOffset)
                                        .queryParam("format", "json");

                                if (leoOnly) {
                                    builder = builder.queryParam("MEAN_MOTION", ">11.25");
                                }

                                return builder.build();
                            })
                            .header(HttpHeaders.COOKIE, sessionCookie)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofMinutes(2)); // ⬅️ timeout ridotto

                    break;

                } catch (Exception e) {
                    log.warn("⚠️ Tentativo {} fallito", attempt);

                    if (attempt == 3) {
                        log.error("❌ Chunk fallito definitivamente");
                        return null;
                    }

                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            if (chunk == null) {
                log.warn("⚠️ Chunk #{} NULL ({} ms)", chunkNumber, duration);
                return null;
            }

            if (isHtmlResponse(chunk)) {
                log.error("❌ HTML response → sessione scaduta");
                if (!isRetry) {
                    sessionCookie = null;
                    performLogin();
                    if (sessionCookie != null) {
                        return downloadGpHistoryInternal(from, to, leoOnly, true);
                    }
                }
                return null;
            }

            if (chunk.isBlank()) {
                log.info("ℹ️ Chunk vuoto → fine dati");
                break;
            }

            int entries = 0;
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(chunk);
                if (root.isArray()) {
                    entries = root.size();
                }
            } catch (Exception e) {
                log.warn("⚠️ Errore parsing JSON");
            }

            if (allData.length() > 0) {
                allData.setLength(allData.length() - 1);
                allData.append(",");
                chunk = chunk.substring(1);
            }

            allData.append(chunk);
            totalEntries += entries;

            log.info("✅ Chunk #{}: {} entries in {} ms", chunkNumber, entries, duration);

            if (entries < limit) break;

            offset += limit;

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (offset > 500_000) break;
        }

        return allData.length() == 0 ? "[]" : allData.toString();
    }

    public String downloadAllLatestTle() {
        log.info("🚀 DOWNLOAD COMPLETO TLE via GP (ultimo TLE per satellite)");
        ensureLogin();
        try {
            StringBuilder allData = new StringBuilder();
            int offset = 0;
            final int limit = 1000;
            int totalDownloaded = 0;
            int chunkNumber = 0;

            while (true) {
                chunkNumber++;
                log.info("📦 GP Full chunk #{}: offset={}", chunkNumber, offset);

                String chunk = webClient.get()
                        .uri("/basicspacedata/query/class/gp/DECAY_DATE/null-val/limit/"
                                + limit + "/offset/" + offset
                                + "/orderby/NORAD_CAT_ID asc/format/tle")
                        .header(HttpHeaders.COOKIE, sessionCookie)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofMinutes(5));

                if (chunk == null || chunk.isBlank()) break;

                allData.append(chunk);
                offset += limit;

                if (offset > 100_000) break;
            }

            return allData.toString();
        } catch (Exception e) {
            log.error("❌ Errore: {}", e.getMessage(), e);
            return null;
        }
    }

    public String downloadTleByNoradId(Long noradId) {
        ensureLogin();
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/basicspacedata/query/class/gp")
                            .queryParam("NORAD_CAT_ID", noradId)
                            .queryParam("format", "tle")
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMinutes(2));

        } catch (WebClientResponseException e) {
            log.error("❌ HTTP error NORAD {}: {}", noradId, e.getStatusCode());
            return null;
        }
    }
}