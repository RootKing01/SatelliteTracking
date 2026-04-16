package com.satelliteTracking.controller;

import com.satelliteTracking.dto.AuthLoginRequestDTO;
import com.satelliteTracking.dto.AuthRegisterRequestDTO;
import com.satelliteTracking.dto.AuthResponseDTO;
import com.satelliteTracking.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody AuthRegisterRequestDTO request,
                                                    HttpSession session) {
        return ResponseEntity.ok(authService.register(request, session));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthLoginRequestDTO request,
                                                 HttpSession session) {
        return ResponseEntity.ok(authService.login(request, session));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponseDTO> me(HttpSession session) {
        return ResponseEntity.ok(authService.currentUser(session));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponseDTO> logout(HttpSession session) {
        return ResponseEntity.ok(authService.logout(session));
    }
}
