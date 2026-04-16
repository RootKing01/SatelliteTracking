package com.satelliteTracking.dto;

public record AuthResponseDTO(
    boolean authenticated,
    String message,
    AuthUserDTO user
) {
}
