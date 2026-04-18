package com.satelliteTracking.dto;

public record CommunityCommentCreateRequestDTO(
    String body,
    Long parentCommentId
) {
}
