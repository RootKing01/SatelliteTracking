package com.satelliteTracking.dto;

public record AuthLoginRequestDTO(
    String usernameOrEmail,
    String password
) {
}
