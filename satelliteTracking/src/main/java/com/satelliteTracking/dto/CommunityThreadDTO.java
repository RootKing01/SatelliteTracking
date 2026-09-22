package com.satelliteTracking.dto;

import java.time.LocalDateTime;

public record CommunityThreadDTO(
    Long id,
    String targetType,
    String targetId,
    String title,
    int commentCount,
    long likesCount,
    boolean likedByMe,
    LocalDateTime createdAt,
    LocalDateTime lastCommentAt
) {
}
