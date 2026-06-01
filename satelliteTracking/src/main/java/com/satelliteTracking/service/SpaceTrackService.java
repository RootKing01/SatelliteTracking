package com.satelliteTracking.service;

import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class SpaceTrackService {

    private static final double LEO_THRESHOLD = 11.0;

    public enum DeltaFetchStatus {
        SUCCESS_WITH_DATA,
        SUCCESS_EMPTY,
        ERROR
    }

    public static final class DeltaFetchResult {
        private final DeltaFetchStatus status;
        private final String raw;

        private DeltaFetchResult(DeltaFetchStatus status, String raw) {
            this.status = status;
            this.raw = raw;
        }

        public static DeltaFetchResult successWithData(String raw) {
            return new DeltaFetchResult(DeltaFetchStatus.SUCCESS_WITH_DATA, raw);
        }

        public static DeltaFetchResult successEmpty() {
            return new DeltaFetchResult(DeltaFetchStatus.SUCCESS_EMPTY, "[]");
        }

        public static DeltaFetchResult error() {
            return new DeltaFetchResult(DeltaFetchStatus.ERROR, null);
        }

        public DeltaFetchStatus getStatus() {
            return status;
        }

        public String getRaw() {
            return raw;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SpaceTrackService.class);

    private static final DateTimeFormatter ST_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${tle.leo.threshold:11.0}")
    private double leoThreshold;

    @Value("${spacetrack.username}")
    private String username;

    @Value("${spacetrack.password}")
    private String password;

    private volatile String sessionCookie;
    private volatile LocalDateTime sessionCreatedAt;
    // FIX: ridotto da 2 ore a 1 ora per ridurre il rischio di cookie invalidi
    private static final Duration SESSION_TTL = Duration.ofHours(1);
    private volatile boolean loginInProgress = false;
    private volatile LocalDateTime satcatCooldownUntil;
    @Value("${satcat.cooldown.hours:2}")
    private long satcatCooldownHours;
    private Duration SATCAT_COOLDOWN = Duration.ofHours(2);
    private volatile LocalDateTime satcatDailyCooldownUntil;
    @Value("${satcat.daily.cooldown.hours:24}")
    private long satcatDailyCooldownHours;
    private Duration SATCAT_DAILY_COOLDOWN = Duration.ofHours(24);
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

    private volatile LocalDateTime gpMinuteWindowStart = LocalDateTime.now();
    private final AtomicInteger gpRequestsThisMinute = new AtomicInteger(0);
    @Value("${spacetrack.gp.max.per.minute:20}")
    private int MAX_GP_PER_MINUTE;

    @Value("${spacetrack.gp.max.results:1000}")
    private int maxGpResults;
    @Value("${spacetrack.gp.lookback.max.hours:12}")
    private int maxGpLookbackHours;

    @Value("${spacetrack.max.concurrent:1}")
    private int maxConcurrentRequests;

    private volatile Semaphore spaceTrackConcurrencySemaphore = new Semaphore(1);

    public SpaceTrackService(WebClient.Builder builder, ObjectMapper objectMapper) {
        log.info("🔧 Inizializzazione SpaceTrackService");
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.create()
                // FIX: aumentato da 30s a 90s per tollerare risposte lente di Space-Track
                .responseTimeout(Duration.ofSeconds(90))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(120))
                                .addHandlerLast(new WriteTimeoutHandler(120))
                );

        this.webClient = builder
                .baseUrl("https://www.space-track.org")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    @PostConstruct
    public void login() {
        this.spaceTrackConcurrencySemaphore = new Semaphore(Math.max(1, maxConcurrentRequests));
        performLogin();
        try {
            SATCAT_COOLDOWN = Duration.ofHours(satcatCooldownHours);
            log.info("SATCAT_COOLDOWN set to {} hours", satcatCooldownHours);
            SATCAT_DAILY_COOLDOWN = Duration.ofHours(satcatDailyCooldownHours);
            log.info("SATCAT_DAILY_COOLDOWN set to {} hours", satcatDailyCooldownHours);
        } catch (Exception e) {
            SATCAT_COOLDOWN = Duration.ofHours(2);
            SATCAT_DAILY_COOLDOWN = Duration.ofHours(24);
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
                                List<String> setCookie = resp.headers().header(HttpHeaders.SET_COOKIE);
                                return resp.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .map(body -> setCookie);
                            })
                            .block(Duration.ofSeconds(60));

                    if (cookies != null && !cookies.isEmpty()) {
                        sessionCookie = cookies.get(0).split(";")[0];
                        sessionCreatedAt = LocalDateTime.now();
                        log.info("✅ Space-Track login SUCCESSO (tentativo {}/3)", attempt);
                        log.debug("🔑 Cookie: {}", sessionCookie);
                        try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            return;
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

                    try {
                        long backoffMs = jitteredBackoffMillis(attempt, 1000L);
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
            notifyAll();
        }
    }

    private void ensureLogin() {
        if (loginInProgress) {
            log.debug("⏳ Login già in corso, attendo...");
            synchronized (this) {
                while (loginInProgress) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            return;
        }

        boolean expired = sessionCreatedAt != null &&
                Duration.between(sessionCreatedAt, LocalDateTime.now())
                        .compareTo(SESSION_TTL) > 0;

        if (sessionCookie == null || expired) {
            synchronized (this) {
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

    public DeltaFetchResult downloadDeltaTle(LocalDateTime lastFetchedAt) {
        ensureLogin();
        log.info("📡 DELTA FETCH via GP recente (ultimo marker={})", lastFetchedAt);
        return downloadRecentGpJson(lastFetchedAt, false);
    }

    public DeltaFetchResult downloadDeltaTleLeoOnly(LocalDateTime lastFetchedAt) {
        ensureLogin();
        log.info("📡 DELTA FETCH LEO via GP recente (ultimo marker={})", lastFetchedAt);
        return downloadRecentGpJson(lastFetchedAt, true);
    }

    private DeltaFetchResult downloadRecentGpJson(LocalDateTime lastFetchedAt, boolean leoOnly) {
        LocalDateTime safeLastFetchedAt = lastFetchedAt != null
                ? lastFetchedAt
                : LocalDateTime.now().minusHours(1);
        LocalDateTime maxLookback = LocalDateTime.now().minusHours(maxGpLookbackHours);
        if (safeLastFetchedAt.isBefore(maxLookback)) {
            log.warn("⚠️ GP lookback cap applicato: {} -> {} (max {}h)", safeLastFetchedAt, maxLookback, maxGpLookbackHours);
            safeLastFetchedAt = maxLookback;
        }
        String formatted = formatForSpaceTrack(safeLastFetchedAt);
        String path = leoOnly
            ? "/basicspacedata/query/class/gp/CREATION_DATE/%3E" + formatted + "/MEAN_MOTION/%3E" + leoThreshold + "/orderby/CREATION_DATE%20asc/limit/" + maxGpResults + "/format/json"
            : "/basicspacedata/query/class/gp/CREATION_DATE/%3E" + formatted + "/orderby/CREATION_DATE%20asc/limit/" + maxGpResults + "/format/json";
        log.info("📡 GP delta path costruito con CREATION_DATE > {} (limit={})", formatted, maxGpResults);
        return executeSingleSpaceTrackGet(path, Duration.ofMinutes(2), true, "GP delta JSON");
    }

    private void logSpaceTrackRequest(String label, String path) {
        log.info("🌐 Space-Track request [{}] path={}", label, path);
    }

    private void logSpaceTrackResponse(String label, int status, String body) {
        int length = body != null ? body.length() : 0;
        log.info("🌐 Space-Track response [{}] status={} bodyLength={}", label, status, length);
    }

    private ResponseEntity<byte[]> performSpaceTrackGet(String path, Duration timeout, String label) {
        logSpaceTrackRequest(label, path);
        return webClient.get()
                .uri(path)
                .header(HttpHeaders.COOKIE, sessionCookie)
            .exchangeToMono(response ->
                response.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(body -> ResponseEntity.status(response.statusCode())
                        .headers(response.headers().asHttpHeaders())
                        .body(body))
            )
                .block(timeout);
    }

    private DeltaFetchResult executeSingleSpaceTrackGet(String path, Duration timeout, boolean retryOnHtml, String label) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            boolean permitAcquired = false;
            try {
                if (!reserveGpRequestSlot()) {
                    log.warn("⏸️ GP rate limit raggiunto, skip {}", label);
                    return DeltaFetchResult.error();
                }

                permitAcquired = acquireSpaceTrackPermit(label);
                if (!permitAcquired) {
                    return DeltaFetchResult.error();
                }

                long requestStart = System.currentTimeMillis();
                ResponseEntity<byte[]> response = performSpaceTrackGet(path, timeout, label);
                long elapsedMs = System.currentTimeMillis() - requestStart;

                if (response == null) {
                    log.warn("⚠️ {} tentativo {}/3 senza risposta ({} ms)", label, attempt, elapsedMs);
                    if (attempt == 3) {
                        log.error("❌ {} fallito definitivamente", label);
                        return DeltaFetchResult.error();
                    }
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
                    continue;
                }

                int status = response.getStatusCode().value();
                byte[] body = response.getBody();
                String result = body != null ? new String(body, StandardCharsets.UTF_8) : "";

                if (status < 200 || status >= 300) {
                    log.warn("⚠️ {} tentativo {}/3 fallito: status {} | body: {}", label, attempt, status,
                            result.substring(0, Math.min(300, result.length())));
                    if (status == 500 && attempt == 1) {
                        log.warn("🔁 {}: 500 ricevuto, forzo re-login prima del retry", label);
                        sessionCookie = null;
                        performLogin();
                    }
                    if (attempt == 3) {
                        log.error("❌ {} fallito definitivamente", label);
                        return DeltaFetchResult.error();
                    }
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
                    continue;
                }

                logSpaceTrackResponse(label, status, result);
                log.info("⏱️ {} tentativo {}/3 completato in {} ms", label, attempt, elapsedMs);

                if (result.isBlank()) {
                    if (status >= 200 && status < 300) {
                        log.info("📭 {} vuoto con status {}: nessun aggiornamento", label, status);
                        return DeltaFetchResult.successEmpty();
                    }
                    log.warn("⚠️ {} body vuoto/non valido con status {}", label, status);
                    if (attempt == 3) {
                        log.error("❌ {} fallito definitivamente", label);
                        return DeltaFetchResult.error();
                    }
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
                    continue;
                }

                if (isHtmlResponse(result)) {
                    log.warn("❌ HTML response su {} (primi 200 char): {}", label, 
                                result.substring(0, Math.min(200, result.length())));
                    // FIX: re-login su HTML a ogni tentativo, non solo al primo
                    if (retryOnHtml) {
                        sessionCookie = null;
                        performLogin();
                        continue;
                    }
                    return DeltaFetchResult.error();
                }

                log.info("🔍 Response preview ({} chars): {}", result.length(), 
                             result.substring(0, Math.min(300, result.length())));

                String trimmed = result.trim();
                if (trimmed.isEmpty()) {
                    log.warn("⚠️ {} body vuoto/non valido", label);
                    if (attempt == 3) {
                        log.error("❌ {} fallito definitivamente", label);
                        return DeltaFetchResult.error();
                    }
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
                    continue;
                }

                try {
                    JsonNode root = objectMapper.readTree(trimmed);
                    if (root.isObject() && root.has("error")) {
                        log.error("❌ {} response contiene error object: {}", label, root.path("error").asText());
                        return DeltaFetchResult.error();
                    }
                    if (!root.isArray()) {
                        log.error("❌ {} JSON non è un array", label);
                        return DeltaFetchResult.error();
                    }
                    if (root.isEmpty()) {
                        log.info("📭 {} vuoto: nessun aggiornamento", label);
                        return DeltaFetchResult.successEmpty();
                    }
                    log.info("📦 {} JSON valido: {} record ricevuti ({} bytes)", label, root.size(), result.length());
                    // FIX: warning se i risultati sono al limite, possibile troncamento
                    if (root.size() >= maxGpResults) {
                        log.warn("⚠️ Risultati al limite massimo ({}): possibile troncamento delta, considera di aumentare spacetrack.gp.max.results", maxGpResults);
                    }
                    return DeltaFetchResult.successWithData(result);
                } catch (Exception parseException) {
                    log.warn("⚠️ {} JSON non parsabile tentativo {}/3: {}", label, attempt, parseException.getMessage());
                    if (attempt == 3) {
                        log.error("❌ {} fallito definitivamente", label, parseException);
                        return DeltaFetchResult.error();
                    }
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
                }
            } catch (WebClientResponseException e) {
                String responseBody = e.getResponseBodyAsString();
                if (e.getStatusCode().is2xxSuccessful() && (responseBody == null || responseBody.isBlank())) {
                    log.info("📭 {} vuoto con status {}: nessun aggiornamento", label, e.getStatusCode());
                    return DeltaFetchResult.successEmpty();
                }

                String safeBody = responseBody == null ? "" : responseBody;
                log.warn("⚠️ {} tentativo {}/3 fallito: {} | body: {}", label, attempt, e.getStatusCode(),
                         safeBody.substring(0, Math.min(300, safeBody.length())));
                if (e.getStatusCode().value() == 500 && attempt == 1) {
                    log.warn("🔁 {}: 500 ricevuto, forzo re-login prima del retry", label);
                    sessionCookie = null;
                    performLogin();
                }
                if (attempt == 3) {
                    log.error("❌ {} fallito definitivamente", label);
                    return DeltaFetchResult.error();
                }
                try {
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return DeltaFetchResult.error();
                }
            } catch (Exception e) {
                log.warn("⚠️ {} tentativo {}/3 fallito: {}", label, attempt, e.getMessage());

                if (attempt == 3) {
                    log.error("❌ {} fallito definitivamente", label, e);
                    return DeltaFetchResult.error();
                }
                // FIX: re-login anche in caso di timeout, non solo su HTML
                if (e.getMessage() == null || e.getMessage().contains("timeout") || e.getCause() instanceof io.netty.handler.timeout.ReadTimeoutException) {
                    log.warn("🔁 Timeout rilevato, forzo re-login prima del retry");
                    sessionCookie = null;
                    performLogin();
                    if (sessionCookie != null) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return DeltaFetchResult.error();
                        }
                    }
                }
                try {
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return DeltaFetchResult.error();
                }
            } finally {
                releaseSpaceTrackPermit(permitAcquired);
            }
        }
        return DeltaFetchResult.error();
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
            + "/orderby/EPOCH%20asc/limit/" + maxGpResults + "/format/tle";
        return executeRawSpaceTrackGet(path, Duration.ofMinutes(5), true, "GP recent TLE");
    }

    private String executeRawSpaceTrackGet(String path, Duration timeout, boolean retryOnHtml, String label) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            boolean permitAcquired = false;
            try {
                if (!reserveGpRequestSlot()) {
                    log.warn("⏸️ GP rate limit raggiunto, skip {}", label);
                    return null;
                }

                permitAcquired = acquireSpaceTrackPermit(label);
                if (!permitAcquired) {
                    return null;
                }

                ResponseEntity<byte[]> response = performSpaceTrackGet(path, timeout, label);
                byte[] body = response != null ? response.getBody() : null;
                String result = body != null ? new String(body, StandardCharsets.UTF_8) : null;

                if (result == null) {
                    log.warn("⚠️ {} tentativo {}/3 senza body", label, attempt);
                    if (attempt == 3) {
                        log.error("❌ {} fallito definitivamente", label);
                        return null;
                    }
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
                    continue;
                }

                if (isHtmlResponse(result)) {
                    log.error("❌ HTML response su {}", label);
                    if (retryOnHtml) {
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
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
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
                    Thread.sleep(jitteredBackoffMillis(attempt, 2000L));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } finally {
                releaseSpaceTrackPermit(permitAcquired);
            }
        }
        return null;
    }

    // Download singolo per NORAD ID
    public String downloadTleByNoradId(Long noradId) {
        log.info("📡 Singolo TLE per NORAD: {}", noradId);
        ensureLogin();
        boolean permitAcquired = false;
        try {
            if (!reserveGpRequestSlot()) {
                log.warn("⏸️ GP rate limit raggiunto, skip NORAD {}", noradId);
                return null;
            }

            permitAcquired = acquireSpaceTrackPermit("GP single TLE");
            if (!permitAcquired) {
                return null;
            }

                String path = "/basicspacedata/query/class/gp?NORAD_CAT_ID=" + noradId + "&format=tle";
                ResponseEntity<byte[]> response = performSpaceTrackGet(path, Duration.ofMinutes(2), "GP single TLE");

                byte[] body = response != null ? response.getBody() : null;
                String result = body != null ? new String(body, StandardCharsets.UTF_8) : null;
                int status = response != null ? response.getStatusCode().value() : -1;
                logSpaceTrackResponse("GP single TLE", status, result);

            if (result != null && isHtmlResponse(result)) {
                sessionCookie = null;
                performLogin();
                if (sessionCookie != null) {
                    response = performSpaceTrackGet(path, Duration.ofMinutes(2), "GP single TLE retry");
                    byte[] retryBody = response != null ? response.getBody() : null;
                    result = retryBody != null ? new String(retryBody, StandardCharsets.UTF_8) : null;
                    status = response != null ? response.getStatusCode().value() : -1;
                    logSpaceTrackResponse("GP single TLE retry", status, result);
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
        } finally {
            releaseSpaceTrackPermit(permitAcquired);
        }
    }

    // Download SATCAT info for a NORAD id
    public String downloadSatcatByNoradId(Long noradId) {
        List<Long> single = List.of(noradId);
        String res = downloadSatcatByNoradIds(single);
        return res;
    }

    public String downloadSatcatByNoradIds(List<Long> noradIds) {
        if (isSatcatRateLimited()) {
            log.warn("⏸️ SATCAT in cooldown fino a {} / {}: skip NORADs {}", satcatCooldownUntil, satcatDailyCooldownUntil, noradIds);
            return null;
        }

        ensureLogin();

        if (!reserveSatcatRequestSlot()) {
            return null;
        }

        boolean markDailyCooldown = false;

        boolean permitAcquired = false;
        try {
            permitAcquired = acquireSpaceTrackPermit("SATCAT grouped");
            if (!permitAcquired) {
                return null;
            }

            String joined = noradIds.stream().map(Object::toString).collect(Collectors.joining(","));
                String path = "/basicspacedata/query/class/satcat/NORAD_CAT_ID/" + joined + "/format/json";
                ResponseEntity<byte[]> response = performSpaceTrackGet(path, Duration.ofMinutes(2), "SATCAT grouped");

                byte[] body = response != null ? response.getBody() : null;
                String result = body != null ? new String(body, StandardCharsets.UTF_8) : null;
                int status = response != null ? response.getStatusCode().value() : -1;
                logSpaceTrackResponse("SATCAT grouped", status, result);

            if (result != null && isHtmlResponse(result)) {
                sessionCookie = null;
                performLogin();
                if (sessionCookie != null) {
                    response = performSpaceTrackGet(path, Duration.ofMinutes(2), "SATCAT grouped retry");
                    byte[] retryBody = response != null ? response.getBody() : null;
                    result = retryBody != null ? new String(retryBody, StandardCharsets.UTF_8) : null;
                    status = response != null ? response.getStatusCode().value() : -1;
                    logSpaceTrackResponse("SATCAT grouped retry", status, result);
                }
            }

            if (result != null && !result.isBlank()) {
                markDailyCooldown = true;
            }

            return result;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                satcatCooldownUntil = LocalDateTime.now().plus(SATCAT_COOLDOWN);
                log.error("❌ HTTP 429 SATCAT NORADs {}: cooldown fino a {}", noradIds, satcatCooldownUntil);
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
        } finally {
            releaseSpaceTrackPermit(permitAcquired);
            if (markDailyCooldown) {
                markSatcatDailyCooldown();
            }
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

    private synchronized boolean reserveSatcatRequestSlot() {
        ensureSatcatWindow();
        if (satcatRequestsThisWindow.get() + 1 > MAX_SATCAT_PER_HOUR) {
            log.warn("⚠️ Richiesta SATCAT eccede soglia oraria: current={} + 1 > max={}", satcatRequestsThisWindow.get(), MAX_SATCAT_PER_HOUR);
            return false;
        }
        if (satcatRequestsThisMinute.get() + 1 > MAX_SATCAT_PER_MINUTE) {
            log.warn("⚠️ Richiesta SATCAT eccede soglia per minuto: current={} + 1 > max={}", satcatRequestsThisMinute.get(), MAX_SATCAT_PER_MINUTE);
            return false;
        }
        satcatRequestsThisWindow.incrementAndGet();
        satcatRequestsThisMinute.incrementAndGet();
        return true;
    }

    private synchronized void markSatcatDailyCooldown() {
        satcatDailyCooldownUntil = LocalDateTime.now().plus(SATCAT_DAILY_COOLDOWN);
    }

    private boolean acquireSpaceTrackPermit(String label) {
        try {
            if (spaceTrackConcurrencySemaphore == null) {
                spaceTrackConcurrencySemaphore = new Semaphore(Math.max(1, maxConcurrentRequests));
            }
            if (!spaceTrackConcurrencySemaphore.tryAcquire()) {
                log.warn("⚠️ Concurrency cap Space-Track raggiunto, skip {}", label);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("⚠️ Impossibile acquisire il permit Space-Track per {}: {}", label, e.getMessage());
            return false;
        }
    }

    private void releaseSpaceTrackPermit(boolean acquired) {
        if (acquired && spaceTrackConcurrencySemaphore != null) {
            spaceTrackConcurrencySemaphore.release();
        }
    }

    private long jitteredBackoffMillis(int attempt, long baseMs) {
        long exponential = baseMs * (1L << Math.max(0, attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(0, 501);
        return exponential + jitter;
    }

    private synchronized boolean reserveGpRequestSlot() {
        if (gpMinuteWindowStart == null) {
            gpMinuteWindowStart = LocalDateTime.now();
        }
        if (Duration.between(gpMinuteWindowStart, LocalDateTime.now()).toMinutes() >= 1) {
            gpMinuteWindowStart = LocalDateTime.now();
            gpRequestsThisMinute.set(0);
        }

        if (gpRequestsThisMinute.get() + 1 > MAX_GP_PER_MINUTE) {
            log.warn("⚠️ GP rate limit per minuto superato: current={} + 1 > max={}", gpRequestsThisMinute.get(), MAX_GP_PER_MINUTE);
            return false;
        }

        gpRequestsThisMinute.incrementAndGet();
        return true;
    }

    public boolean isSatcatRateLimited() {
        return (satcatCooldownUntil != null && LocalDateTime.now().isBefore(satcatCooldownUntil))
                || (satcatDailyCooldownUntil != null && LocalDateTime.now().isBefore(satcatDailyCooldownUntil));
    }

    public LocalDateTime getSatcatCooldownUntil() {
        return satcatCooldownUntil;
    }

    public LocalDateTime getSatcatDailyCooldownUntil() {
        return satcatDailyCooldownUntil;
    }
}