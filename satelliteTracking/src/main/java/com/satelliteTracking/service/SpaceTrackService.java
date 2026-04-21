package com.satelliteTracking.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SpaceTrackService {
    private static final Logger log = LoggerFactory.getLogger(SpaceTrackService.class);

    private WebClient authWebClient;
    private WebClient dataWebClient;

    @Value("${spacetrack.username}")
    private String username;

    @Value("${spacetrack.password}")
    private String password;

    private final AtomicReference<List<String>> sessionCookies = new AtomicReference<>();

    public SpaceTrackService() {
        log.info("SpaceTrackService bean created (initialization deferred to @PostConstruct)");
    }

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing SpaceTrackService with username: {}", username);

            if (username == null || username.isEmpty()) {
                throw new IllegalStateException("SPACETRACK_USERNAME is not set!");
            }
            if (password == null || password.isEmpty()) {
                throw new IllegalStateException("SPACETRACK_PASSWORD is not set!");
            }

            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                    .build();

            this.authWebClient = WebClient.builder()
                    .baseUrl("https://www.space-track.org")
                    .exchangeStrategies(strategies)
                    .defaultHeader("User-Agent", "SatelliteTracker/1.0")
                    .build();

            this.dataWebClient = WebClient.builder()
                    .baseUrl("https://www.space-track.org")
                    .exchangeStrategies(strategies)
                    .defaultHeader("User-Agent", "SatelliteTracker/1.0")
                    .build();

            log.info("SpaceTrackService initialized successfully");

        } catch (Exception e) {
            log.error("FAILED to initialize SpaceTrackService", e);
            throw new RuntimeException(e);
        }
    }

    private Mono<Void> login() {
        return authWebClient.post()
                .uri("/ajaxauth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("identity=" + username + "&password=" + password)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        List<String> cookies = response.cookies()
                                .values()
                                .stream()
                                .flatMap(List::stream)
                                .map(ResponseCookie::toString)
                                .toList();

                        sessionCookies.set(cookies);
                        return response.bodyToMono(Void.class);
                    }
                    return response.createException().flatMap(Mono::error);
                });
    }

    private <T> Mono<T> authenticatedRequest(java.util.function.Function<WebClient, Mono<T>> requestFunc) {
        if (sessionCookies.get() == null || sessionCookies.get().isEmpty()) {
            return login().then(Mono.defer(() -> executeRequest(requestFunc)));
        }

        return executeRequest(requestFunc)
                .onErrorResume(e -> login().then(Mono.defer(() -> executeRequest(requestFunc))));
    }

    private <T> Mono<T> executeRequest(java.util.function.Function<WebClient, Mono<T>> requestFunc) {
        WebClient clientWithCookies = dataWebClient.mutate()
                .defaultHeader(HttpHeaders.COOKIE, String.join("; ", sessionCookies.get()))
                .build();

        return requestFunc.apply(clientWithCookies);
    }

    public Mono<String> downloadTleByNoradId(Long noradId) {
    
        return downloadTleByNoradIds(List.of(noradId));
    }

    public Mono<String> downloadTleByNoradIds(List<Long> noradIds) {
    log.info("Scaricando TLE per {} satelliti", noradIds.size());

    String ids = noradIds.stream()
            .map(String::valueOf)
            .reduce((a, b) -> a + "," + b)
            .orElse("");

    return authenticatedRequest(client ->
            client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/basicspacedata/query/class/tle_latest")
                            .queryParam("NORAD_CAT_ID", ids)
                            .queryParam("ORDINAL", "1")
                            .queryParam("format", "tle")
                            .build()
                    )
                    .accept(MediaType.TEXT_PLAIN)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMinutes(2))
                    .doOnSuccess(r -> log.info("TLE batch scaricati correttamente"))
                    .doOnError(e -> log.error("Errore batch TLE", e))
            );
    }

    public Mono<String> downloadTleLatest() {
        return authenticatedRequest(client ->
                client.get()
                        .uri("/basicspacedata/query/class/tle_latest/ORDINAL/1/format/tle")
                        .accept(MediaType.TEXT_PLAIN)
                        .retrieve()
                        .bodyToMono(String.class)
        );
    }
}