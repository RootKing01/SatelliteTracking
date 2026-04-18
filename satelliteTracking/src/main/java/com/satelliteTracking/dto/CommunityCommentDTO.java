package com.satelliteTracking.dto;

import java.time.LocalDateTime;

public record CommunityCommentDTO(
    Long id,
    Long threadId,
    Long parentCommentId,
    Long authorId,
    String authorUsername,
    String body,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    boolean deleted
) {
}
