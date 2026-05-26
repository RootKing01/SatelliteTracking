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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.stream.Stream;

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
    @Value("${satcat.cooldown.hours:2}")
    private long satcatCooldownHours;
    private Duration SATCAT_COOLDOWN = Duration.ofHours(2);
    // Simple sliding window counter to avoid exceeding Space-Track hourly quotas
    private volatile LocalDateTime satcatWindowStart = LocalDateTime.now();
    private final AtomicInteger satcatRequestsThisWindow = new AtomicInteger(0);
    private volatile LocalDateTime satcatMinuteWindowStart = LocalDateTime.now();
    private final AtomicInteger satcatRequestsThisMinute = new AtomicInteger(0);
    // Values configurable via application.properties
    @Value("${satcat.max.per.minute:29}")
    private int MAX_SATCAT_PER_MINUTE;
    @Value("${satcat.max.per.hour:299}")
    private int MAX_SATCAT_PER_HOUR;

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
        try {
            SATCAT_COOLDOWN = Duration.ofHours(satcatCooldownHours);
            log.info("SATCAT_COOLDOWN set to {} hours", satcatCooldownHours);
        } catch (Exception e) {
            SATCAT_COOLDOWN = Duration.ofHours(2);
        }
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
                    log.warn("⚠️ Sessione assente o scaduta, re-login...");
                    performLogin();
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
        log.info("📡 DELTA FETCH via GP recente (ultimo marker={})", lastFetchedAt);
        return downloadRecentGpJson(lastFetchedAt, false);
    }

    public String downloadDeltaTleLeoOnly(LocalDateTime lastFetchedAt) {
        ensureLogin();
        log.info("📡 DELTA FETCH LEO via GP recente (ultimo marker={})", lastFetchedAt);
        return downloadRecentGpJson(lastFetchedAt, true);
    }

    private String downloadRecentGpJson(LocalDateTime lastFetchedAt, boolean leoOnly) {
        LocalDateTime safeLastFetchedAt = lastFetchedAt != null ? lastFetchedAt : LocalDateTime.now().minusHours(1);
        String formatted = formatForSpaceTrack(safeLastFetchedAt);
        String path = leoOnly
                ? "/basicspacedata/query/class/gp/CREATION_DATE/%3E" + formatted + "/MEAN_MOTION/%3E11.25/orderby/CREATION_DATE%20asc/limit/5000/format/json"
                : "/basicspacedata/query/class/gp/CREATION_DATE/%3E" + formatted + "/orderby/CREATION_DATE%20asc/limit/5000/format/json";
        log.info("📡 GP delta path costruito con CREATION_DATE > {}", formatted);
        return executeSingleSpaceTrackGet(path, Duration.ofMinutes(2), true, "GP delta JSON");
    }

    private String executeSingleSpaceTrackGet(String path, Duration timeout, boolean retryOnHtml, String label) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String result = webClient.get()
                        .uri(path)
                        .header(HttpHeaders.COOKIE, sessionCookie)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(timeout);

                if (result != null && isHtmlResponse(result)) {
                    log.error("❌ HTML response su {}", label);
                    if (retryOnHtml && attempt == 1) {
                        sessionCookie = null;
                        performLogin();
                        continue;
                    }
                    return null;
                }

                return result;
            } catch (WebClientResponseException e) {
                log.warn("⚠️ {} tentativo {}/3 fallito: {}", label, attempt, e.getStatusCode());
                if (e.getStatusCode().value() == 500 && attempt == 1) {
                    log.warn("🔁 {}: 500 ricevuto, forzo re-login prima del retry", label);
                    sessionCookie = null;
                    performLogin();
                }
                if (attempt == 3) {
                    log.error("❌ {} fallito definitivamente", label);
                    return null;
                }
                try {
                    Thread.sleep(2000L * (long) Math.pow(2, attempt - 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (Exception e) {
                log.warn("⚠️ {} tentativo {}/3 fallito: {}", label, attempt, e.getMessage());
                if (attempt == 3) {
                    log.error("❌ {} fallito definitivamente", label, e);
                    return null;
                }
                try {
                    Thread.sleep(2000L * (long) Math.pow(2, attempt - 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    // =============================
    // DOWNLOAD COMPLETO via GP
    // =============================
    public String downloadAllLatestTle() {
        log.info("🚀 DOWNLOAD COMPLETO TLE via GP recente (single request)");
        ensureLogin();
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        String path = "/basicspacedata/query/class/gp/DECAY_DATE/null-val/EPOCH/%3E"
                + formatForSpaceTrack(since)
            + "/orderby/EPOCH asc/limit/5000/format/tle";
        return executeSingleSpaceTrackGet(path, Duration.ofMinutes(5), true, "GP recent TLE");
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
        // delegate to batched method for single id
        List<Long> single = List.of(noradId);
        String res = downloadSatcatByNoradIds(single);
        return res;
    }

    public String downloadSatcatByNoradIds(List<Long> noradIds) {
        if (isSatcatRateLimited()) {
            log.warn("⏸️ SATCAT in cooldown fino a {}: skip NORADs {}", satcatCooldownUntil, noradIds);
            return null;
        }

        ensureLogin();

        // ensure sliding window
        synchronized (this) {
            ensureSatcatWindow();
            // Each grouped API call is a single request against the quota
            if (satcatRequestsThisWindow.get() + 1 > MAX_SATCAT_PER_HOUR) {
                log.warn("⚠️ Richiesta SATCAT eccede soglia oraria: current={} + 1 > max={}", satcatRequestsThisWindow.get(), MAX_SATCAT_PER_HOUR);
                return null;
            }
            if (satcatRequestsThisMinute.get() + 1 > MAX_SATCAT_PER_MINUTE) {
                log.warn("⚠️ Richiesta SATCAT eccede soglia per minuto: current={} + 1 > max={}", satcatRequestsThisMinute.get(), MAX_SATCAT_PER_MINUTE);
                return null;
            }
        }

        try {
            String joined = noradIds.stream().map(Object::toString).collect(Collectors.joining(","));
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/basicspacedata/query/class/satcat/NORAD_CAT_ID/" + joined + "/format/json")
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
                                    .path("/basicspacedata/query/class/satcat/NORAD_CAT_ID/" + joined + "/format/json")
                                    .build())
                            .header(HttpHeaders.COOKIE, sessionCookie)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofMinutes(2));
                }
            }

            // record usage on success (count 1 per grouped request)
            if (result != null) {
                synchronized (this) {
                    ensureSatcatWindow();
                    satcatRequestsThisWindow.incrementAndGet();
                    satcatRequestsThisMinute.incrementAndGet();
                }
            }

            return result;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                satcatCooldownUntil = LocalDateTime.now().plus(SATCAT_COOLDOWN);
                log.error("❌ HTTP 429 SATCAT NORADs {}: cooldown fino a {}", noradIds, satcatCooldownUntil);
                // reset windows to avoid further requests until cooldown
                synchronized (this) {
                    satcatRequestsThisWindow.set(0);
                    satcatRequestsThisMinute.set(0);
                    satcatWindowStart = LocalDateTime.now();
                    satcatMinuteWindowStart = LocalDateTime.now();
                }
                return null;
            }
            log.error("❌ HTTP error SATCAT NORADs {}: {}", noradIds, e.getStatusCode());
            return null;
        }
    }

    private void ensureSatcatWindow() {
        if (satcatWindowStart == null) satcatWindowStart = LocalDateTime.now();
        if (satcatMinuteWindowStart == null) satcatMinuteWindowStart = LocalDateTime.now();
        if (Duration.between(satcatMinuteWindowStart, LocalDateTime.now()).toMinutes() >= 1) {
            satcatMinuteWindowStart = LocalDateTime.now();
            satcatRequestsThisMinute.set(0);
        }
        if (Duration.between(satcatWindowStart, LocalDateTime.now()).toHours() >= 1) {
            satcatWindowStart = LocalDateTime.now();
            satcatRequestsThisWindow.set(0);
        }
    }

    public boolean isSatcatRateLimited() {
        return satcatCooldownUntil != null && LocalDateTime.now().isBefore(satcatCooldownUntil);
    }

    public LocalDateTime getSatcatCooldownUntil() {
        return satcatCooldownUntil;
    }
}