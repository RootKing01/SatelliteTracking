package com.satelliteTracking.controller;

import com.satelliteTracking.dto.CommunityCommentCreateRequestDTO;
import com.satelliteTracking.dto.CommunityCommentDTO;
import com.satelliteTracking.dto.CommunityCommentReportRequestDTO;
import com.satelliteTracking.dto.CommunityCommentUpdateRequestDTO;
import com.satelliteTracking.dto.CommunityFeedItemDTO;
import com.satelliteTracking.dto.CommunityThreadCreateRequestDTO;
import com.satelliteTracking.dto.CommunityThreadLikeDTO;
import com.satelliteTracking.dto.CommunityThreadWithCommentsDTO;
import com.satelliteTracking.service.CommunityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @PostMapping("/threads")
    public ResponseEntity<CommunityThreadWithCommentsDTO> createThread(
        @RequestBody CommunityThreadCreateRequestDTO request
    ) {
        return ResponseEntity.ok(communityService.createGeneralThread(request));
    }

    @GetMapping("/threads/{targetType}/{targetId}")
    public ResponseEntity<CommunityThreadWithCommentsDTO> getThread(
        @PathVariable String targetType,
        @PathVariable String targetId
    ) {
        return ResponseEntity.ok(communityService.getThreadWithComments(targetType, targetId));
    }

    @PostMapping("/threads/{targetType}/{targetId}/comments")
    public ResponseEntity<CommunityCommentDTO> createComment(
        @PathVariable String targetType,
        @PathVariable String targetId,
        @RequestBody CommunityCommentCreateRequestDTO request
    ) {
        return ResponseEntity.ok(communityService.createComment(targetType, targetId, request));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<CommunityCommentDTO> updateComment(
        @PathVariable Long commentId,
        @RequestBody CommunityCommentUpdateRequestDTO request
    ) {
        return ResponseEntity.ok(communityService.updateComment(commentId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, String>> deleteComment(@PathVariable Long commentId) {
        communityService.deleteComment(commentId);
        return ResponseEntity.ok(Map.of("message", "Commento rimosso"));
    }

    @PostMapping("/comments/{commentId}/reports")
    public ResponseEntity<Map<String, String>> reportComment(
        @PathVariable Long commentId,
        @RequestBody CommunityCommentReportRequestDTO request
    ) {
        communityService.reportComment(commentId, request);
        return ResponseEntity.ok(Map.of("message", "Segnalazione inviata"));
    }

    @GetMapping("/feed")
    public ResponseEntity<List<CommunityFeedItemDTO>> getFeed(
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(communityService.getFeed(limit));
    }

    @GetMapping("/threads/featured")
    public ResponseEntity<List<CommunityFeedItemDTO>> getFeaturedThreads(
        @RequestParam(defaultValue = "8") int limit
    ) {
        return ResponseEntity.ok(communityService.getFeaturedThreads(limit));
    }

    @PostMapping("/threads/{threadId}/likes")
    public ResponseEntity<CommunityThreadLikeDTO> toggleThreadLike(@PathVariable Long threadId) {
        return ResponseEntity.ok(communityService.toggleThreadLike(threadId));
    }
}
