package com.satelliteTracking.config;

import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.repository.AppUserRepository;
import com.satelliteTracking.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class AuthSessionAuthenticationFilter extends OncePerRequestFilter {

    private final AppUserRepository appUserRepository;

    public AuthSessionAuthenticationFilter(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object userIdObj = session.getAttribute(AuthService.SESSION_USER_ID);
                if (userIdObj instanceof Long userId) {
                    Optional<AppUser> userOpt = appUserRepository.findById(userId);
                    if (userOpt.isPresent() && userOpt.get().isEnabled()) {
                        AppUser user = userOpt.get();
                        UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                user.getUsername(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
                            );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        session.invalidate();
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
