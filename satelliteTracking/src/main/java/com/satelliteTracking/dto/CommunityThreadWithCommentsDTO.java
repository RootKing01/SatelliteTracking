package com.satelliteTracking.dto;

import java.util.List;

public record CommunityThreadWithCommentsDTO(
    CommunityThreadDTO thread,
    List<CommunityCommentDTO> comments
) {
}
