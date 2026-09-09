package com.satelliteTracking.dto;

import java.time.LocalDateTime;

public record CommunityThreadReadStateDTO(
    Long threadId,
    Long lastReadCommentId,
    LocalDateTime lastReadAt,
    long unreadReplyCount
) {
}