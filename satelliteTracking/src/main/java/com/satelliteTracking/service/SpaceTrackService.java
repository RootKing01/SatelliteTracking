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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SpaceTrackService {

    private static final Logger log = LoggerFactory.getLogger(SpaceTrackService.class);

    // Space-Track si aspetta questo formato per CREATION_DATE
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
                .responseTimeout(Duration.ofMinutes(5));
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
                    .exchangeToMono(resp -> {
                        List<String> setCookie = resp.headers().header(HttpHeaders.SET_COOKIE);
                        return resp.bodyToMono(String.class).map(body -> setCookie);
                    })
                    .block();

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

    // =========================
    // DELTA via GP_HISTORY (corretto per aggiornamenti incrementali)
    // =========================

    /**
     * Scarica tutti i TLE pubblicati dopo lastFetchedAt usando GP_HISTORY.
     * 
     * PERCHÉ GP_HISTORY e non GP:
     * - GP contiene solo l'ultimo TLE per satellite → filtrare CREATION_DATE su GP
     *   restituisce solo satelliti il cui TLE ATTUALE è stato creato dopo quella data,
     *   perdendo tutti i satelliti aggiornati prima ma non ancora ri-aggiornati.
     * - GP_HISTORY contiene lo storico completo → filtrare CREATION_DATE restituisce
     *   tutti i TLE pubblicati nell'intervallo, indipendentemente da aggiornamenti successivi.
     */
    public String downloadDeltaTle(LocalDateTime lastFetchedAt) {
        String from = formatForSpaceTrack(lastFetchedAt);
        String to = formatForSpaceTrack(LocalDateTime.now());
        log.info("📡 DELTA FETCH via GP_HISTORY");
        log.info("   CREATION_DATE range: {} → {}", from, to);
        log.info("   💡 GP_HISTORY restituisce TUTTI i TLE pubblicati nell'intervallo");
        ensureLogin();
        ensureLogin();
        try {
            return downloadGpHistoryInternal(from, to, false, false);
        } catch (Exception e) {
            log.error("❌ Errore delta fetch: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Scarica TLE LEO pubblicati dopo lastFetchedAt usando GP_HISTORY.
     */
    public String downloadDeltaTleLeoOnly(LocalDateTime lastFetchedAt) {
        String from = formatForSpaceTrack(lastFetchedAt);
        String to = formatForSpaceTrack(LocalDateTime.now());
        log.info("📡 DELTA FETCH LEO via GP_HISTORY");
        log.info("   CREATION_DATE range: {} → {}", from, to);
        ensureLogin();
        ensureLogin();
        try {
            return downloadGpHistoryInternal(from, to, true, false);
        } catch (Exception e) {
            log.error("❌ Errore delta fetch LEO: {}", e.getMessage(), e);
            return null;
        }
    }

    private String downloadGpHistoryInternal(String from, String to, boolean leoOnly, boolean isRetry) {
        // TODO: implementare parser JSON in parseAndSaveJson() e richiamarlo da TleDataService
        StringBuilder allData = new StringBuilder();
        int offset = 0;
        final int limit = 1000;
        int totalEntries = 0;
        int chunkNumber = 0;
        String type = leoOnly ? "LEO" : "FULL";

        while (true) {
            chunkNumber++;
            log.info("📦 GP_HISTORY {} chunk #{} → offset={}, limit={}", type, chunkNumber, offset, limit);

            long startTime = System.currentTimeMillis();
            int currentOffset = offset;
            String creationDateRange = from + "--" + to;

            String chunk = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/basicspacedata/query/class/gp_history")
                                .queryParam("CREATION_DATE", creationDateRange)
                                .queryParam("DECAY_DATE", "null-val")
                                .queryParam("orderby", "CREATION_DATE asc")
                                .queryParam("limit", limit)
                                .queryParam("offset", currentOffset)
                                .queryParam("format", "json"); // Usa JSON come formato principale
                        if (leoOnly) {
                            builder = builder.queryParam("MEAN_MOTION", ">11.25");
                        }
                        return builder.build();
                    })
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .exchangeToMono(response -> {
                        int status = response.statusCode().value();
                        log.debug("📡 HTTP {}", status);
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(String.class).defaultIfEmpty("");
                        } else {
                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> {
                                        log.error("❌ HTTP ERROR {}: {}",
                                                status, body.substring(0, Math.min(300, body.length())));
                                        return Mono.empty();
                                    });
                        }
                    })
                    .block(Duration.ofMinutes(3));

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

            // GP_HISTORY in formato TLE restituisce 3 righe: nome + line1 + line2
            long nonEmptyLines = chunk.lines().filter(l -> !l.isBlank()).count();
            int entries = (int) (nonEmptyLines / 3);

            allData.append(chunk);
            totalEntries += entries;

            log.info("✅ Chunk #{}: ~{} entries ({} righe), {} bytes in {} ms",
                    chunkNumber, entries, nonEmptyLines, chunk.length(), duration);

            if (nonEmptyLines < limit * 3L) {
                log.info("✅ Ultimo chunk raggiunto");
                break;
            }

            offset += limit;
            try { Thread.sleep(200); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            if (offset > 500_000) {
                log.warn("⚠️ Safety stop offset={}", offset);
                break;
            }
        }

        String result = allData.toString();
        if (result.isBlank()) {
            log.info("ℹ️ Nessun aggiornamento disponibile nel range {} → {}", from, to);
            return "";
        }

        log.info("🚀 GP_HISTORY COMPLETATO: ~{} entries totali, {} bytes", totalEntries, result.length());
        return result;
    }

    // =========================
    // DOWNLOAD COMPLETO via GP (solo per primo popolamento)
    // =========================
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
                        .block(Duration.ofMinutes(3));

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
                    .block();

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
                            .block();
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
}