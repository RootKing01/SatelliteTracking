package com.satelliteTracking.dto;

import java.time.LocalDateTime;

public record CommunityFeedItemDTO(
    Long threadId,
    String targetType,
    String targetId,
    String title,
    int commentCount,
    long likesCount,
    boolean likedByMe,
    LocalDateTime lastCommentAt,
    String lastCommentPreview
) {
}
