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

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SpaceTrackService {

    private static final Logger log = LoggerFactory.getLogger(SpaceTrackService.class);

    private final WebClient webClient;

    @Value("${spacetrack.username}")
    private String username;

    @Value("${spacetrack.password}")
    private String password;

    private String sessionCookie;

    public SpaceTrackService(WebClient.Builder builder) {
        log.info("🔧 Inizializzazione SpaceTrackService con timeout 5 minuti");
        
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(5));

        this.webClient = builder
                .baseUrl("https://www.space-track.org")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    // =========================
    // LOGIN
    // =========================
    @PostConstruct
    public void login() {
        try {
            log.info("🔐 Tentativo login Space-Track...");
            log.debug("   Username: {}", username);

            List<String> cookies = webClient.post()
                    .uri("/ajaxauth/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue("identity=" + username + "&password=" + password)
                    .exchangeToMono(resp -> {
                        List<String> setCookie = resp.headers().header(HttpHeaders.SET_COOKIE);
                        return resp.bodyToMono(String.class)
                                .map(body -> setCookie);
                    })
                    .block();

            if (cookies != null && !cookies.isEmpty()) {
                sessionCookie = cookies.get(0).split(";")[0];
                log.info("✅ Space-Track login SUCCESSO");
                log.debug("   Cookie sessione: {}...", sessionCookie.substring(0, Math.min(30, sessionCookie.length())));
            } else {
                log.error("❌ Login Space-Track FALLITO - nessun cookie ricevuto");
            }

        } catch (Exception e) {
            log.error("❌ Errore critico durante login SpaceTrack: {}", e.getMessage(), e);
        }
    }

    // =========================
    // ENSURE LOGIN
    // =========================
    private void ensureLogin() {
        if (sessionCookie == null) {
            log.warn("⚠️ Cookie sessione non presente, eseguo nuovo login...");
            login();
        } else {
            log.debug("✓ Cookie sessione presente, procedo con la richiesta");
        }
    }

    // =========================
    // DELTA FETCH (per aggiornamenti incrementali)
    // =========================
    public String downloadDeltaTle(String lastEpoch) {

        log.info("📡 Richiesta DELTA TLE da epoch: {}", lastEpoch);
        ensureLogin();

        try {
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/basicspacedata/query/class/gp")
                            .queryParam("EPOCH", ">" + lastEpoch)
                            .queryParam("DECAY_DATE", "null-val")
                            .queryParam("orderby", "NORAD_CAT_ID,EPOCH desc")
                            .queryParam("format", "tle")
                            .build()
                    )
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMinutes(5));

            if (result != null && !result.isBlank()) {
                int lines = result.split("\n").length;
                log.info("✅ Delta TLE scaricato: {} bytes, ~{} satelliti", result.length(), lines / 2);
            } else {
                log.warn("⚠️ Nessun aggiornamento TLE disponibile dopo epoch {}", lastEpoch);
            }
            
            return result;

        } catch (WebClientResponseException e) {
            log.error("❌ HTTP error durante delta fetch → Status: {} | Body: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString().substring(0, Math.min(200, e.getResponseBodyAsString().length())));
            return null;
        } catch (Exception e) {
            log.error("❌ Errore inatteso durante delta fetch: {}", e.getMessage(), e);
            return null;
        }
    }

    // =========================
    // SINGLE TLE (SAFE)
    // =========================
    public String downloadTleByNoradId(Long noradId) {

        log.info("📡 Richiesta singolo TLE per NORAD_CAT_ID: {}", noradId);
        ensureLogin();

        try {
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/basicspacedata/query/class/tle_latest")
                            .queryParam("NORAD_CAT_ID", noradId)
                            .queryParam("format", "tle")
                            .build()
                    )
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (result != null && !result.isBlank()) {
                log.info("✅ TLE scaricato con successo per NORAD {}: {} bytes", noradId, result.length());
            } else {
                log.warn("⚠️ Nessun TLE trovato per NORAD {}", noradId);
            }
            
            return result;

        } catch (WebClientResponseException e) {
            log.error("❌ HTTP error per NORAD {} → Status: {} | Body: {}",
                    noradId,
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            return null;
        }
    }

    // =========================
    // BULK (CORRETTO: CHUNKED SINGLE QUERIES)
    // =========================
    public String downloadTleBatch(List<Long> noradIds) {

        log.info("📦 Richiesta batch TLE per {} satelliti", noradIds.size());
        log.debug("   NORAD IDs: {}", noradIds.stream().limit(10).map(String::valueOf).collect(Collectors.joining(", ")) + 
                (noradIds.size() > 10 ? "..." : ""));
        
        ensureLogin();

        try {
            String ids = noradIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/basicspacedata/query/class/tle_latest")
                            .queryParam("NORAD_CAT_ID", ids)
                            .queryParam("format", "tle")
                            .build()
                    )
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (result != null && !result.isBlank()) {
                int lines = result.split("\n").length;
                log.info("✅ Batch scaricato: {} bytes, ~{} TLE", result.length(), lines / 2);
            } else {
                log.warn("⚠️ Batch vuoto per {} IDs richiesti", noradIds.size());
            }
            
            return result;

        } catch (WebClientResponseException e) {
            log.error("❌ SpaceTrack batch error → Status: {} | Body: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            return null;
        }
    }

    // =========================
    // DOWNLOAD IN CHUNKS - FULL DATASET
    // =========================
    public String downloadAllLatestTle() {

        log.info("═════════════════════════════════════════════════════════");
        log.info("🚀 INIZIO DOWNLOAD COMPLETO TLE DA SPACE-TRACK");
        log.info("   Metodo: Download incrementale a chunk");
        log.info("═════════════════════════════════════════════════════════");
        
        ensureLogin();

        try {
            log.info("📥 Fase 1/2: Download dati in chunk da Space-Track...");
            
            StringBuilder allData = new StringBuilder();
            int offset = 0;
            int limit = 1000; // Chunk size
            int totalDownloaded = 0;
            int chunkNumber = 0;

            while (true) {
                chunkNumber++;
                log.info("📦 Chunk #{}: Richiesta offset={}, limit={}", chunkNumber, offset, limit);
                
                long startTime = System.currentTimeMillis();
                
                String chunk = webClient.get()
                        .uri("/basicspacedata/query/class/gp/limit/" + limit + "/offset/" + offset + "/orderby/NORAD_CAT_ID,EPOCH desc/format/tle")
                        .header(HttpHeaders.COOKIE, sessionCookie)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofMinutes(2));

                long duration = System.currentTimeMillis() - startTime;

                if (chunk == null || chunk.isBlank()) {
                    log.info("✅ Chunk #{}: Vuoto - fine download raggiunta (tempo: {}ms)", chunkNumber, duration);
                    log.info("   Nessun altro dato disponibile da offset {}", offset);
                    break;
                }

                // Conta quante entry TLE (ogni TLE = 2 linee)
                int lines = chunk.split("\n").length;
                int entries = lines / 2;
                
                allData.append(chunk);
                totalDownloaded += entries;
                
                log.info("✅ Chunk #{}: Ricevuto {} entries, {} bytes in {}ms", 
                        chunkNumber, entries, chunk.length(), duration);
                log.info("   Progresso totale: {} entries scaricate", totalDownloaded);

                // Se abbiamo ricevuto meno del limite, abbiamo finito
                if (entries < limit) {
                    log.info("✅ Chunk #{}: Ultimo chunk (size {} < limit {})", chunkNumber, entries, limit);
                    break;
                }

                offset += limit;

                // Safety: max 100 chunks (100k satelliti)
                if (offset > 100000) {
                    log.warn("⚠️ Raggiunto limite sicurezza (offset={}), interrompo download", offset);
                    break;
                }

                // Rate limiting: piccola pausa tra le richieste
                log.debug("   Pausa 100ms prima del prossimo chunk...");
                Thread.sleep(100);
            }

            String result = allData.toString();
            
            if (result.isBlank()) {
                log.error("❌ ERRORE: Download completato ma nessun dato ricevuto!");
                log.error("   Chunks processati: {}", chunkNumber);
                return null;
            }

            log.info("═════════════════════════════════════════════════════════");
            log.info("✅✅✅ DOWNLOAD SPACE-TRACK COMPLETATO CON SUCCESSO!");
            log.info("   Total entries: {}", totalDownloaded);
            log.info("   Total bytes: {}", result.length());
            log.info("   Total chunks: {}", chunkNumber);
            log.info("═════════════════════════════════════════════════════════");
            
            return result;

        } catch (WebClientResponseException e) {
            log.error("═════════════════════════════════════════════════════════");
            log.error("❌ ERRORE HTTP DA SPACE-TRACK");
            log.error("   Status code: {}", e.getStatusCode());
            log.error("   Response body (prime 500 char): {}", 
                    e.getResponseBodyAsString().substring(0, Math.min(500, e.getResponseBodyAsString().length())));
            log.error("═════════════════════════════════════════════════════════");
            return null;
        } catch (Exception e) {
            log.error("═════════════════════════════════════════════════════════");
            log.error("❌ ERRORE INATTESO DURANTE DOWNLOAD SPACE-TRACK");
            log.error("   Tipo errore: {}", e.getClass().getSimpleName());
            log.error("   Messaggio: {}", e.getMessage());
            log.error("═════════════════════════════════════════════════════════", e);
            return null;
        }
    }
}