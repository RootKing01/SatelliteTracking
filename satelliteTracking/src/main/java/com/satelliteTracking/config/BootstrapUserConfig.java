package com.satelliteTracking.config;

import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapUserConfig {

    @Bean
    public CommandLineRunner ensureBootstrapUser(AppUserRepository appUserRepository,
                                                 PasswordEncoder passwordEncoder,
                                                 @Value("${app.auth.bootstrap.enabled:true}") boolean bootstrapEnabled,
                                                 @Value("${app.auth.bootstrap.username:demo}") String username,
                                                 @Value("${app.auth.bootstrap.email:demo@satellitetracker.local}") String email,
                                                 @Value("${app.auth.bootstrap.password:Demo123!}") String password) {
        return args -> {
            if (!bootstrapEnabled) {
                return;
            }

            if (appUserRepository.existsByUsernameIgnoreCase(username)
                || appUserRepository.existsByEmailIgnoreCase(email)) {
                return;
            }

            AppUser user = new AppUser();
            user.setUsername(username.trim());
            user.setEmail(email.trim().toLowerCase());
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole("USER");
            user.setEnabled(true);
            appUserRepository.save(user);
        };
    }
}
