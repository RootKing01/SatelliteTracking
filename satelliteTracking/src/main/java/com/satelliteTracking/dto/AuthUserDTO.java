package com.satelliteTracking.dto;

public record AuthUserDTO(
    Long id,
    String username,
    String email,
    String role
) {
}
