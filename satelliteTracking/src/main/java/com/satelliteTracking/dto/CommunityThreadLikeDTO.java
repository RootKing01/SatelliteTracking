package com.satelliteTracking.dto;

public record CommunityThreadLikeDTO(
    Long threadId,
    long likesCount,
    boolean likedByMe
) {
}
