package com.satelliteTracking.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.satelliteTracking.dto.AuthLoginRequestDTO;
import com.satelliteTracking.dto.AuthRegisterRequestDTO;
import com.satelliteTracking.dto.AuthResponseDTO;
import com.satelliteTracking.service.AuthService;
import com.satelliteTracking.service.JwtService;

import jakarta.servlet.http.HttpServletRequest;

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
    public ResponseEntity<AuthResponseDTO> register(
        @RequestBody AuthRegisterRequestDTO request,
        HttpServletRequest httpRequest) {

        AuthResponseDTO response = authService.register(request);

        // Il JWT viene inserito nel cookie HttpOnly.
        ResponseCookie cookie = buildAuthCookie(response.token(), httpRequest);

    // Il JWT non viene restituito nel body JSON.
    AuthResponseDTO safeResponse = withoutToken(response);

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(safeResponse);

    }

    @PostMapping("/login")
public ResponseEntity<AuthResponseDTO> login(
        @RequestBody AuthLoginRequestDTO request,
        HttpServletRequest httpRequest) {

    AuthResponseDTO response = authService.login(request);

    // Il JWT viene inserito nel cookie HttpOnly.
    ResponseCookie cookie = buildAuthCookie(response.token(), httpRequest);

    // Il JWT non viene restituito nel body JSON.
    AuthResponseDTO safeResponse = withoutToken(response);

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(safeResponse);
}

    @GetMapping("/me")
    public ResponseEntity<AuthResponseDTO> me() {
        return ResponseEntity.ok(authService.currentUser());
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponseDTO> logout(HttpServletRequest httpRequest) {
        ResponseCookie cookie = clearAuthCookie(httpRequest);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(authService.logout());
    }

    private ResponseCookie buildAuthCookie(String token, HttpServletRequest request) {
        return ResponseCookie.from(jwtCookieName, token == null ? "" : token)
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path("/")
            .sameSite(jwtCookieSameSite)
            .maxAge(Duration.ofMillis(jwtService.getJwtExpirationMs()))
            .build();
    }

    private ResponseCookie clearAuthCookie(HttpServletRequest request) {
        return ResponseCookie.from(jwtCookieName, "")
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path("/")
            .sameSite(jwtCookieSameSite)
            .maxAge(Duration.ZERO)
            .build();
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && forwardedProto.equalsIgnoreCase("https")) {
            return true;
        }

        return false;
    }

    private AuthResponseDTO withoutToken(AuthResponseDTO response) {
    return new AuthResponseDTO(
        response.authenticated(),
        response.message(),
        response.user(),
        null
        );
    }
}
