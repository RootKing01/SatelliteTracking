package com.satelliteTracking.dto;

import java.time.LocalDateTime;

public record CommunityNotificationDTO(
    Long id,
    String notificationType,
    Long threadId,
    String threadTitle,
    String targetType,
    String targetId,
    Long sourceCommentId,
    String sourceCommentAuthorUsername,
    String preview,
    LocalDateTime createdAt,
    LocalDateTime readAt
) {
}