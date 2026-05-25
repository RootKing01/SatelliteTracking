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
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SpaceTrackService {

    private static final Logger log = LoggerFactory.getLogger(SpaceTrackService.class);

        private static final DateTimeFormatter ST_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final WebClient webClient;

    @Value("${spacetrack.username}")
    private String username;

    @Value("${spacetrack.password}")
    private String password;

    private volatile String sessionCookie;
    private volatile LocalDateTime sessionCreatedAt;
    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private volatile boolean loginInProgress = false;
    private volatile LocalDateTime satcatCooldownUntil;
    private static final Duration SATCAT_COOLDOWN = Duration.ofHours(2);

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
        loginInProgress = true;
        try {
            // 🔁 RETRY con backoff esponenziale (1s, 2s, 4s)
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    log.info("🔐 Tentativo login Space-Track {}/3...", attempt);

                    List<String> cookies = webClient.post()
                            .uri("/ajaxauth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .bodyValue("identity=" + username + "&password=" + password)
                            .exchangeToMono(resp -> {
                                // Prendi i cookie dalle header
                                List<String> setCookie = resp.headers().header(HttpHeaders.SET_COOKIE);
                                // Consuma il body ma ritorna i cookie
                                return resp.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .map(body -> setCookie);
                            })
                            .block(Duration.ofSeconds(60)); // Increased from 30s to 60s

                    if (cookies != null && !cookies.isEmpty()) {
                        sessionCookie = cookies.get(0).split(";")[0];
                        sessionCreatedAt = LocalDateTime.now();
                        log.info("✅ Space-Track login SUCCESSO (tentativo {}/3)", attempt);
                        log.debug("🔑 Cookie: {}", sessionCookie);
                        return; // Login succeeded, exit retry loop
                    } else {
                        log.warn("⚠️ Login tentativo {}/3 fallito - nessun cookie ricevuto", attempt);
                        if (attempt == 3) {
                            log.error("❌ Login definitivamente fallito dopo 3 tentativi");
                            sessionCookie = null;
                            sessionCreatedAt = null;
                        }
                    }

                } catch (Exception e) {
                    log.warn("⚠️ Login tentativo {}/3 fallito: {}", attempt, e.getMessage());

                    if (attempt == 3) {
                        log.error("❌ Login definitivamente fallito dopo 3 tentativi: {}", e.getMessage(), e);
                        sessionCookie = null;
                        sessionCreatedAt = null;
                        return;
                    }

                    // Backoff esponenziale: 1s, 2s, 4s
                    try {
                        long backoffMs = 1000L * (long) Math.pow(2, attempt - 1);
                        log.info("⏳ Attesa {} ms prima del retry...", backoffMs);
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("❌ Retry interrotto: {}", ie.getMessage());
                        sessionCookie = null;
                        sessionCreatedAt = null;
                        return;
                    }
                }
            }
        } finally {
            loginInProgress = false;
        }
    }

    private void ensureLogin() {
        if (loginInProgress) {
            log.debug("⏳ Login già in corso, attendo...");
            // Attendi che il login in corso finisca
            int maxWait = 10;
            while (loginInProgress && maxWait-- > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return;
        }

        boolean expired = sessionCreatedAt != null &&
                Duration.between(sessionCreatedAt, LocalDateTime.now())
                        .compareTo(SESSION_TTL) > 0;

        if (sessionCookie == null || expired) {
            synchronized (this) {
                // Double-check dopo aver acquisito il lock
                if (sessionCookie == null || 
                    (sessionCreatedAt != null && 
                     Duration.between(sessionCreatedAt, LocalDateTime.now()).compareTo(SESSION_TTL) > 0)) {
                    
                    loginInProgress = true;
                    try {
                        log.warn("⚠️ Sessione assente o scaduta, re-login...");
                        performLogin();
                    } finally {
                        loginInProgress = false;
                    }
                }
            }
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
    // 🔥 METODI PUBBLICI
    // =============================

    public String downloadDeltaTle(LocalDateTime lastFetchedAt) {
        ensureLogin();
        return downloadDeltaChunked(lastFetchedAt, LocalDateTime.now(), false);
    }

    public String downloadDeltaTleLeoOnly(LocalDateTime lastFetchedAt) {
        ensureLogin();
        return downloadDeltaChunked(lastFetchedAt, LocalDateTime.now(), true);
    }

    // =============================
    // 🔥 CHUNK TEMPORALI
    // =============================
    private String downloadDeltaChunked(LocalDateTime from, LocalDateTime to, boolean leoOnly) {

        StringBuilder finalResult = new StringBuilder();
        LocalDateTime cursor = from;
        int chunkIndex = 0;
        int successfulChunks = 0;
        boolean encounteredHardFailure = false;

        log.info("📡 DELTA FETCH{} via GP (chunk temporali)", leoOnly ? " LEO" : "");
        log.info("   EPOCH range: {} → {}", formatForSpaceTrack(from), formatForSpaceTrack(to));

        while (cursor.isBefore(to)) {

            chunkIndex++;

            LocalDateTime next = cursor.plusMinutes(60);
            if (next.isAfter(to)) next = to;

            String fromStr = formatForSpaceTrack(cursor);
            String toStr = formatForSpaceTrack(next);

            log.info("⏱️ Time chunk #{} → {} → {}", chunkIndex, fromStr, toStr);

            String chunk = downloadGpHistoryInternal(fromStr, toStr, leoOnly, false);

            if (chunk == null) {
                log.warn("⚠️ Chunk temporale #{} fallito, skip", chunkIndex);
                encounteredHardFailure = true;
                cursor = next;
                continue;
            }

            if (!chunk.equals("[]")) {
                if (finalResult.length() > 0) {
                    // Rimuovi ']' dal precedente e '[' dal nuovo per concatenare array JSON
                    finalResult.setLength(finalResult.length() - 1);
                    finalResult.append(",");
                    if (chunk.startsWith("[")) {
                        chunk = chunk.substring(1);
                    }
                }
                finalResult.append(chunk);
                successfulChunks++;
            }

            cursor = next;

            try {
                // Piccola attesa random per evitare burst sincronizzati
                long wait = 1000L + ThreadLocalRandom.current().nextInt(0, 1000);
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); 
            }
        }

        if (successfulChunks == 0 && encounteredHardFailure) {
            log.error("❌ Nessun chunk Space-Track riuscito: fallback consigliato");
            return null;
        }

        String result = finalResult.length() == 0 ? "[]" : finalResult.toString();
        
        if (!result.equals("[]")) {
            log.info("✅ CHUNK TEMPORALI COMPLETATI: {} chunk processati", chunkIndex);
        } else if (encounteredHardFailure) {
            log.warn("⚠️ CHUNK TEMPORALI completati con errori, ma nessun dato valido ricevuto");
        } else {
            log.info("ℹ️ Nessun aggiornamento disponibile nel range temporale");
        }
        
        return result;
    }

    // =============================
    // 🔥 CORE GP
    // =============================
    private String downloadGpHistoryInternal(String from, String to, boolean leoOnly, boolean isRetry) {

        StringBuilder allData = new StringBuilder();
        int offset = 0;
        final int limit = 200;
        int totalEntries = 0;
        int chunkNumber = 0;
        String type = leoOnly ? "LEO" : "FULL";

        while (true) {

            chunkNumber++;
            log.info("📦 GP {} chunk #{} → offset={}, limit={}", type, chunkNumber, offset, limit);

            long startTime = System.currentTimeMillis();
            int currentOffset = offset;

            String chunk = null;

            // 🔁 RETRY con backoff esponenziale
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    chunk = webClient.get()
                            .uri(uriBuilder -> {
                                var builder = uriBuilder
                                        .path("/basicspacedata/query/class/gp/DECAY_DATE/null-val/EPOCH");

                                        // Use EPOCH range syntax: from--to (ISO 'T' formatted)
                                        builder = builder.pathSegment(from + "--" + to);

                                if (leoOnly) {
                                    builder = builder.pathSegment("MEAN_MOTION", ">11.25");
                                }

                                builder = builder
                                        .pathSegment("limit", String.valueOf(limit))
                                        .pathSegment("offset", String.valueOf(currentOffset))
                                        .pathSegment("format", "json");

                                return builder.build();
                            })
                            .header(HttpHeaders.COOKIE, sessionCookie)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofMinutes(2));

                    break; // Successo, esci dal retry loop

                } catch (Exception e) {
                    log.warn("⚠️ Tentativo {}/3 fallito: {}: {}", attempt, e.getClass().getSimpleName(), e.getMessage());

                    if (e instanceof WebClientResponseException responseException) {
                        String responseBody = responseException.getResponseBodyAsString();
                        if (responseBody != null && !responseBody.isBlank()) {
                            log.warn("   Risposta HTTP Space-Track: {}", responseBody.substring(0, Math.min(500, responseBody.length())));
                        }
                        log.warn("   Status HTTP: {}", responseException.getStatusCode());
                    }

                    if (attempt == 3) {
                        log.error("❌ Chunk fallito definitivamente dopo 3 tentativi");
                        return null;
                    }

                    // Backoff esponenziale: 2s, 4s, 8s (aggressivamente più conservativo)
                    try {
                        Thread.sleep(2000L * (long)Math.pow(2, attempt - 1));
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
                    log.info("🔄 Re-login e retry...");
                    sessionCookie = null;
                    performLogin();
                    if (sessionCookie != null) {
                        return downloadGpHistoryInternal(from, to, leoOnly, true);
                    }
                }
                log.error("❌ Re-login fallito");
                return null;
            }

            if (chunk.isBlank()) {
                log.info("ℹ️ Chunk #{} vuoto → fine dati ({} ms)", chunkNumber, duration);
                break;
            }

            // Conta entries nel JSON
            int entries = 0;
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(chunk);
                if (root.isArray()) {
                    entries = root.size();
                }
            } catch (Exception e) {
                log.warn("⚠️ Errore parsing JSON: {}", e.getMessage());
            }

            // Concatena chunk JSON
            if (allData.length() > 0) {
                allData.setLength(allData.length() - 1); // Rimuovi ']'
                allData.append(",");
                if (chunk.startsWith("[")) {
                    chunk = chunk.substring(1); // Rimuovi '['
                }
            }

            allData.append(chunk);
            totalEntries += entries;

            log.info("✅ Chunk #{}: {} entries in {} ms", chunkNumber, entries, duration);

            if (entries < limit) {
                log.info("✅ Ultimo chunk raggiunto");
                break;
            }

            offset += limit;

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (offset > 500_000) {
                log.warn("⚠️ Safety stop offset={}", offset);
                break;
            }
        }

        String result = allData.length() == 0 ? "[]" : allData.toString();
        
        if (!result.equals("[]")) {
            log.info("🚀 GP interno completato: {} entries totali", totalEntries);
        }
        
        return result;
    }

    // =============================
    // DOWNLOAD COMPLETO via GP
    // =============================
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
                long startTime = System.currentTimeMillis();

                String chunk = webClient.get()
                        .uri("/basicspacedata/query/class/gp/DECAY_DATE/null-val/limit/"
                                + limit + "/offset/" + offset
                                + "/orderby/NORAD_CAT_ID asc/format/tle")
                        .header(HttpHeaders.COOKIE, sessionCookie)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofMinutes(5));

                long duration = System.currentTimeMillis() - startTime;

                if (chunk == null || chunk.isBlank()) {
                    log.info("✅ Fine download ({} ms)", duration);
                    break;
                }

                if (isHtmlResponse(chunk)) {
                    log.error("❌ HTML → sessione scaduta, re-login...");
                    sessionCookie = null;
                    performLogin();
                    if (sessionCookie == null) return null;
                    continue;
                }

                long nonEmptyLines = chunk.lines().filter(l -> !l.isBlank()).count();
                int entries = (int)(nonEmptyLines / 2); // GP = 2 righe per satellite
                allData.append(chunk);
                totalDownloaded += entries;

                log.info("✅ Chunk #{}: {} entries in {} ms", chunkNumber, entries, duration);

                if (nonEmptyLines < limit * 2L) break;
                offset += limit;
                if (offset > 100_000) {
                    log.warn("⚠️ Safety stop");
                    break;
                }
                Thread.sleep(200);
            }

            String result = allData.toString();
            if (result.isBlank()) {
                log.error("❌ Nessun dato ricevuto");
                return null;
            }
            log.info("✅ DOWNLOAD COMPLETO: {} entries, {} bytes", totalDownloaded, result.length());
            return result;
        } catch (Exception e) {
            log.error("❌ Errore: {}", e.getMessage(), e);
            return null;
        }
    }

    // Download singolo per NORAD ID
    public String downloadTleByNoradId(Long noradId) {
        log.info("📡 Singolo TLE per NORAD: {}", noradId);
        ensureLogin();
        try {
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/basicspacedata/query/class/gp")
                            .queryParam("NORAD_CAT_ID", noradId)
                            .queryParam("format", "tle")
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMinutes(2));

            if (result != null && isHtmlResponse(result)) {
                sessionCookie = null;
                performLogin();
                if (sessionCookie != null) {
                    result = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/basicspacedata/query/class/gp")
                                    .queryParam("NORAD_CAT_ID", noradId)
                                    .queryParam("format", "tle")
                                    .build())
                            .header(HttpHeaders.COOKIE, sessionCookie)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofMinutes(2));
                }
            }

            if (result != null && !result.isBlank()) {
                log.info("✅ TLE per NORAD {}: {} bytes", noradId, result.length());
            } else {
                log.warn("⚠️ Nessun TLE per NORAD {}", noradId);
            }
            return result;
        } catch (WebClientResponseException e) {
            log.error("❌ HTTP error NORAD {}: {}", noradId, e.getStatusCode());
            return null;
        }
    }

    // Download SATCAT info for a NORAD id
    public String downloadSatcatByNoradId(Long noradId) {
        if (isSatcatRateLimited()) {
            log.warn("⏸️ SATCAT in cooldown fino a {}: skip NORAD {}", satcatCooldownUntil, noradId);
            return null;
        }

        ensureLogin();
        try {
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/basicspacedata/query/class/satcat/NORAD_CAT_ID/" + noradId + "/format/json")
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMinutes(2));

            if (result != null && isHtmlResponse(result)) {
                sessionCookie = null;
                performLogin();
                if (sessionCookie != null) {
                    result = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/basicspacedata/query/class/satcat/NORAD_CAT_ID/" + noradId + "/format/json")
                                    .build())
                            .header(HttpHeaders.COOKIE, sessionCookie)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofMinutes(2));
                }
            }

            return result;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                satcatCooldownUntil = LocalDateTime.now().plus(SATCAT_COOLDOWN);
                log.error("❌ HTTP 429 SATCAT NORAD {}: cooldown fino a {}", noradId, satcatCooldownUntil);
                return null;
            }
            log.error("❌ HTTP error SATCAT NORAD {}: {}", noradId, e.getStatusCode());
            return null;
        }
    }

    public boolean isSatcatRateLimited() {
        return satcatCooldownUntil != null && LocalDateTime.now().isBefore(satcatCooldownUntil);
    }

    public LocalDateTime getSatcatCooldownUntil() {
        return satcatCooldownUntil;
    }
}