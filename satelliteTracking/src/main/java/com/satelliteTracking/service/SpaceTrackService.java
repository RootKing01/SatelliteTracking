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

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(5));

        this.webClient = builder
                .baseUrl("https://www.space-track.org")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @PostConstruct
    public void login() {
        try {
            log.info("🔐 Space-Track login...");

            var response = webClient.post()
                    .uri("/ajaxauth/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue("identity=" + username + "&password=" + password)
                    .exchangeToMono(resp -> {
                        var cookies = resp.headers().header(HttpHeaders.SET_COOKIE);
                        return resp.bodyToMono(String.class)
                                .map(body -> cookies);
                    })
                    .block();

            if (response != null && !response.isEmpty()) {
                sessionCookie = response.get(0).split(";")[0];
                log.info("✅ Login OK");
            } else {
                log.error("❌ Login failed");
            }

        } catch (Exception e) {
            log.error("❌ SpaceTrack login error", e);
        }
    }

    private void ensureLogin() {
        if (sessionCookie == null) login();
    }

    // =========================
    // 🔥 DELTA FETCH (CORE FIX)
    // =========================
    public String downloadDeltaTle(String lastEpoch) {

        ensureLogin();

        try {
            log.info("📡 SpaceTrack delta fetch since epoch={}", lastEpoch);

            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/basicspacedata/query/class/gp/format/tle")
                            .queryParam("decay_date", "null-val")
                            .queryParam("epoch", ">" + lastEpoch)
                            .build()
                    )
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMinutes(5));

        } catch (WebClientResponseException e) {
            log.error("❌ SpaceTrack error {} | body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());

            return null;
        }
    }
}