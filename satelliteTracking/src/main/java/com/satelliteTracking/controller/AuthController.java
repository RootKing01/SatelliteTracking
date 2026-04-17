package com.satelliteTracking.controller;

import com.satelliteTracking.dto.AuthLoginRequestDTO;
import com.satelliteTracking.dto.AuthRegisterRequestDTO;
import com.satelliteTracking.dto.AuthResponseDTO;
import com.satelliteTracking.service.AuthService;
import com.satelliteTracking.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @Value("${app.security.jwt.cookie-name:st_auth}")
    private String jwtCookieName;

    @Value("${app.security.jwt.cookie-secure:false}")
    private boolean jwtCookieSecure;

    @Value("${app.security.jwt.cookie-same-site:Lax}")
    private String jwtCookieSameSite;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody AuthRegisterRequestDTO request) {
        AuthResponseDTO response = authService.register(request);
        ResponseCookie cookie = buildAuthCookie(response.token());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(withoutToken(response));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthLoginRequestDTO request) {
        AuthResponseDTO response = authService.login(request);
        ResponseCookie cookie = buildAuthCookie(response.token());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(withoutToken(response));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponseDTO> me() {
        return ResponseEntity.ok(authService.currentUser());
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponseDTO> logout() {
        ResponseCookie cookie = clearAuthCookie();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(authService.logout());
    }

    private AuthResponseDTO withoutToken(AuthResponseDTO response) {
        return new AuthResponseDTO(response.authenticated(), response.message(), response.user(), null);
    }

    private ResponseCookie buildAuthCookie(String token) {
        return ResponseCookie.from(jwtCookieName, token == null ? "" : token)
            .httpOnly(true)
            .secure(jwtCookieSecure)
            .path("/")
            .sameSite(jwtCookieSameSite)
            .maxAge(Duration.ofMillis(jwtService.getJwtExpirationMs()))
            .build();
    }

    private ResponseCookie clearAuthCookie() {
        return ResponseCookie.from(jwtCookieName, "")
            .httpOnly(true)
            .secure(jwtCookieSecure)
            .path("/")
            .sameSite(jwtCookieSameSite)
            .maxAge(Duration.ZERO)
            .build();
    }
}
