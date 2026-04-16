package com.satelliteTracking.service;

import com.satelliteTracking.dto.AuthLoginRequestDTO;
import com.satelliteTracking.dto.AuthRegisterRequestDTO;
import com.satelliteTracking.dto.AuthResponseDTO;
import com.satelliteTracking.dto.AuthUserDTO;
import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.repository.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    public static final String SESSION_USER_ID = "AUTH_USER_ID";
    public static final String SESSION_USERNAME = "AUTH_USERNAME";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponseDTO register(AuthRegisterRequestDTO request, HttpSession session) {
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
        attachUserToSession(saved, session);

        return new AuthResponseDTO(true, "Registrazione completata", toUserDTO(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(AuthLoginRequestDTO request, HttpSession session) {
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

        attachUserToSession(user, session);
        return new AuthResponseDTO(true, "Accesso eseguito", toUserDTO(user));
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO currentUser(HttpSession session) {
        Object userIdObj = session.getAttribute(SESSION_USER_ID);
        if (!(userIdObj instanceof Long userId)) {
            return new AuthResponseDTO(false, "Sessione non autenticata", null);
        }

        return appUserRepository.findById(userId)
            .filter(AppUser::isEnabled)
            .map(user -> new AuthResponseDTO(true, "Sessione attiva", toUserDTO(user)))
            .orElseGet(() -> new AuthResponseDTO(false, "Sessione non autenticata", null));
    }

    public AuthResponseDTO logout(HttpSession session) {
        session.invalidate();
        return new AuthResponseDTO(false, "Logout eseguito", null);
    }

    @Transactional(readOnly = true)
    public AppUser requireAuthenticatedUser(HttpSession session) {
        Object userIdObj = session.getAttribute(SESSION_USER_ID);
        if (!(userIdObj instanceof Long userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non autenticata");
        }

        return appUserRepository.findById(userId)
            .filter(AppUser::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non autenticata"));
    }

    private Optional<AppUser> findByUsernameOrEmail(String usernameOrEmail) {
        String normalized = usernameOrEmail.trim();
        if (normalized.contains("@")) {
            return appUserRepository.findByEmailIgnoreCase(normalized);
        }
        return appUserRepository.findByUsernameIgnoreCase(normalized);
    }

    private void attachUserToSession(AppUser user, HttpSession session) {
        session.setAttribute(SESSION_USER_ID, user.getId());
        session.setAttribute(SESSION_USERNAME, user.getUsername());
        session.setMaxInactiveInterval(60 * 60 * 8);
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
