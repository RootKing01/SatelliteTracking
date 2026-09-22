package com.satelliteTracking.config;

import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.repository.AppUserRepository;
import com.satelliteTracking.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;

    @Value("${app.security.jwt.cookie-name:st_auth}")
    private String jwtCookieName;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository appUserRepository) {
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = resolveJwtToken(request);
            if (token != null && !token.isEmpty()) {
                try {
                    Claims claims = jwtService.parseClaims(token);
                    String username = claims.getSubject();

                    if (username != null && !username.isBlank()) {
                        Optional<AppUser> userOpt = appUserRepository.findByUsernameIgnoreCase(username);
                        if (userOpt.isPresent() && userOpt.get().isEnabled()) {
                            AppUser user = userOpt.get();
                            UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                    user.getUsername(),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
                                );
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            log.info("utente loggato: {}", user.getUsername());
                        }
                    }
                } catch (Exception ignored) {
                    // Invalid token -> proceed unauthenticated.
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (jwtCookieName.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }

        return null;
    }
}
