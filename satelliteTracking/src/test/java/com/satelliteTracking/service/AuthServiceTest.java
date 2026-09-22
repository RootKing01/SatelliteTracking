package com.satelliteTracking.service;

import com.satelliteTracking.dto.AuthLoginRequestDTO;
import com.satelliteTracking.dto.AuthRegisterRequestDTO;
import com.satelliteTracking.dto.AuthResponseDTO;
import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({AuthService.class, JwtService.class, AuthServiceTest.TestAuthConfig.class})
@ActiveProfiles("test")
public class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository userRepository;

    @BeforeEach
    public void setUp() {
        userRepository.deleteAll();
    }

    @Test
    public void testRegisterUserSuccess() {
        // Arrange
        AuthRegisterRequestDTO request = new AuthRegisterRequestDTO(
            "testuser",
            "test@example.com",
            "password123"
        );

        // Act
        AuthResponseDTO response = authService.register(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.authenticated());
        assertEquals("test@example.com", response.user().email());
        assertNotNull(response.token());

        // Verify user persisted
        AppUser savedUser = userRepository.findByEmailIgnoreCase("test@example.com").orElse(null);
        assertNotNull(savedUser);
        assertEquals("testuser", savedUser.getUsername());
    }

    @Test
    public void testRegisterUserWithShortPassword() {
        // Arrange
        AuthRegisterRequestDTO request = new AuthRegisterRequestDTO(
            "testuser",
            "test@example.com",
            "short"
        );

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> authService.register(request));
        assertTrue(exception.getMessage().contains("Password troppo corta"));
    }

    @Test
    public void testRegisterDuplicateEmail() {
        // Arrange
        AuthRegisterRequestDTO request1 = new AuthRegisterRequestDTO(
            "user1",
            "duplicate@example.com",
            "password123"
        );
        AuthRegisterRequestDTO request2 = new AuthRegisterRequestDTO(
            "user2",
            "duplicate@example.com",
            "password123"
        );

        // Act
        authService.register(request1);

        // Act & Assert - second registration should fail
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> authService.register(request2));
        assertTrue(exception.getMessage().contains("Email gia in uso"));
    }

    @Test
    public void testLoginSuccess() {
        // Arrange
        AuthRegisterRequestDTO registerRequest = new AuthRegisterRequestDTO(
            "loginuser",
            "login@example.com",
            "password123"
        );
        authService.register(registerRequest);

        AuthLoginRequestDTO loginRequest = new AuthLoginRequestDTO(
            "login@example.com",
            "password123"
        );

        // Act
        AuthResponseDTO response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertTrue(response.authenticated());
        assertEquals("login@example.com", response.user().email());
        assertNotNull(response.token());
    }

    @Test
    public void testLoginInvalidPassword() {
        // Arrange
        AuthRegisterRequestDTO registerRequest = new AuthRegisterRequestDTO(
            "user",
            "user@example.com",
            "correctpassword123"
        );
        authService.register(registerRequest);

        AuthLoginRequestDTO loginRequest = new AuthLoginRequestDTO(
            "user@example.com",
            "wrongpassword"
        );

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> authService.login(loginRequest));
        assertTrue(exception.getMessage().contains("Credenziali non valide"));
    }

    @Test
    public void testLoginUserNotFound() {
        // Arrange
        AuthLoginRequestDTO loginRequest = new AuthLoginRequestDTO(
            "nonexistent@example.com",
            "password123"
        );

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> authService.login(loginRequest));
        assertTrue(exception.getMessage().contains("Credenziali non valide"));
    }

    /**
     * Test configuration to provide beans required for AuthService
     */
    @org.springframework.boot.test.context.TestConfiguration
    public static class TestAuthConfig {
        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
