package com.satelliteTracking.dto;

public record AuthRegisterRequestDTO(
    String username,
    String email,
    String password
) {
}
