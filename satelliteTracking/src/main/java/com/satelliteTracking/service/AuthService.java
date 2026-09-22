package com.satelliteTracking.service;

import com.satelliteTracking.dto.AuthLoginRequestDTO;
import com.satelliteTracking.dto.AuthRegisterRequestDTO;
import com.satelliteTracking.dto.AuthResponseDTO;
import com.satelliteTracking.dto.AuthUserDTO;
import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponseDTO register(AuthRegisterRequestDTO request) {
        String username = normalizeRequired(request.username(), "username");
        String email = normalizeRequired(request.email(), "email").toLowerCase(Locale.ROOT);
        String password = normalizeRequired(request.password(), "password");

        if (username.length() < 3 || username.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username deve avere tra 3 e 64 caratteri");
        }
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password troppo corta (min 8 caratteri)");
        }
        if (!email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email non valida");
        }

        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username gia in uso");
        }
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email gia in uso");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setEnabled(true);

        AppUser saved = appUserRepository.save(user);
        String token = jwtService.generateToken(saved);

        return new AuthResponseDTO(true, "Registrazione completata", toUserDTO(saved), token);
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(AuthLoginRequestDTO request) {
        String usernameOrEmail = normalizeRequired(request.usernameOrEmail(), "usernameOrEmail");
        String password = normalizeRequired(request.password(), "password");

        Optional<AppUser> userOpt = findByUsernameOrEmail(usernameOrEmail);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenziali non valide");
        }

        AppUser user = userOpt.get();
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Utente disabilitato");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenziali non valide");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponseDTO(true, "Accesso eseguito", toUserDTO(user), token);
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthResponseDTO(false, "Sessione non autenticata", null, null);
        }

        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return new AuthResponseDTO(false, "Sessione non autenticata", null, null);
        }

        return appUserRepository.findByUsernameIgnoreCase(username)
            .filter(AppUser::isEnabled)
            .map(user -> new AuthResponseDTO(true, "Sessione attiva", toUserDTO(user), null))
            .orElseGet(() -> new AuthResponseDTO(false, "Sessione non autenticata", null, null));
    }

    public AuthResponseDTO logout() {
        return new AuthResponseDTO(false, "Logout eseguito lato client", null, null);
    }

    @Transactional(readOnly = true)
    public AppUser requireAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non autenticata");
        }

        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non autenticata");
        }

        return appUserRepository.findByUsernameIgnoreCase(username)
            .filter(AppUser::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non autenticata"));
    }

    @Transactional(readOnly = true)
    public AppUser getAuthenticatedUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return null;
        }

        return appUserRepository.findByUsernameIgnoreCase(username)
            .filter(AppUser::isEnabled)
            .orElse(null);
    }

    private Optional<AppUser> findByUsernameOrEmail(String usernameOrEmail) {
        String normalized = usernameOrEmail.trim();
        if (normalized.contains("@")) {
            return appUserRepository.findByEmailIgnoreCase(normalized);
        }
        return appUserRepository.findByUsernameIgnoreCase(normalized);
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo obbligatorio: " + fieldName);
        }
        return normalized;
    }

    private AuthUserDTO toUserDTO(AppUser user) {
        return new AuthUserDTO(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
