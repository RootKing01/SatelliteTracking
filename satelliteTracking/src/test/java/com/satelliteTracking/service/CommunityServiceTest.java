package com.satelliteTracking.service;

import com.satelliteTracking.dto.CommunityCommentCreateRequestDTO;
import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.model.CommunityComment;
import com.satelliteTracking.model.CommunityNotification;
import com.satelliteTracking.model.CommunityTargetType;
import com.satelliteTracking.model.CommunityThread;
import com.satelliteTracking.repository.CommunityCommentReportRepository;
import com.satelliteTracking.repository.CommunityCommentRepository;
import com.satelliteTracking.repository.CommunityNotificationRepository;
import com.satelliteTracking.repository.CommunityThreadLikeRepository;
import com.satelliteTracking.repository.CommunityThreadReadStateRepository;
import com.satelliteTracking.repository.CommunityThreadRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock private CommunityThreadRepository communityThreadRepository;
    @Mock private CommunityCommentRepository communityCommentRepository;
    @Mock private CommunityCommentReportRepository communityCommentReportRepository;
    @Mock private CommunityNotificationRepository communityNotificationRepository;
    @Mock private CommunityThreadLikeRepository communityThreadLikeRepository;
    @Mock private CommunityThreadReadStateRepository communityThreadReadStateRepository;
    @Mock private AuthService authService;
    @Mock private SatelliteRepository satelliteRepository;

    @InjectMocks private CommunityService communityService;

    @Test
    void createCommentCreatesReplyNotification() {
        AppUser author = createUser(1L, "alice");
        AppUser recipient = createUser(2L, "bob");
        CommunityThread thread = createThread(10L, author);
        CommunityComment parent = createComment(11L, thread, recipient, "parent body");

        when(authService.requireAuthenticatedUser()).thenReturn(author);
        when(communityThreadRepository.findByTargetTypeAndTargetId(CommunityTargetType.SATELLITE, "12345"))
            .thenReturn(Optional.of(thread));
        when(communityCommentRepository.findByIdAndDeletedAtIsNull(parent.getId())).thenReturn(Optional.of(parent));
        when(communityCommentRepository.save(any(CommunityComment.class))).thenAnswer(invocation -> {
            CommunityComment comment = invocation.getArgument(0);
            comment.setId(99L);
            return comment;
        });
        when(communityThreadRepository.save(any(CommunityThread.class))).thenAnswer(invocation -> invocation.getArgument(0));

        communityService.createComment(
            "SATELLITE",
            "12345",
            new CommunityCommentCreateRequestDTO("reply body", parent.getId())
        );

        ArgumentCaptor<CommunityNotification> captor = ArgumentCaptor.forClass(CommunityNotification.class);
        verify(communityNotificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipient().getId()).isEqualTo(recipient.getId());
        assertThat(captor.getValue().getThread().getId()).isEqualTo(thread.getId());
        assertThat(captor.getValue().getSourceComment().getId()).isEqualTo(99L);
    }

    @Test
    void createCommentDoesNotNotifyOnSelfReply() {
        AppUser author = createUser(1L, "alice");
        CommunityThread thread = createThread(10L, author);
        CommunityComment parent = createComment(11L, thread, author, "parent body");

        when(authService.requireAuthenticatedUser()).thenReturn(author);
        when(communityThreadRepository.findByTargetTypeAndTargetId(CommunityTargetType.SATELLITE, "12345"))
            .thenReturn(Optional.of(thread));
        when(communityCommentRepository.findByIdAndDeletedAtIsNull(parent.getId())).thenReturn(Optional.of(parent));
        when(communityCommentRepository.save(any(CommunityComment.class))).thenAnswer(invocation -> {
            CommunityComment comment = invocation.getArgument(0);
            comment.setId(99L);
            return comment;
        });
        when(communityThreadRepository.save(any(CommunityThread.class))).thenAnswer(invocation -> invocation.getArgument(0));

        communityService.createComment(
            "SATELLITE",
            "12345",
            new CommunityCommentCreateRequestDTO("reply body", parent.getId())
        );

        verify(communityNotificationRepository, never()).save(any());
    }

    private AppUser createUser(Long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setEnabled(true);
        user.setRole("USER");
        return user;
    }

    private CommunityThread createThread(Long id, AppUser creator) {
        CommunityThread thread = new CommunityThread();
        thread.setId(id);
        thread.setTargetType(CommunityTargetType.SATELLITE);
        thread.setTargetId("12345");
        thread.setTitle("Thread");
        thread.setCreatedBy(creator);
        thread.setCommentCount(0);
        return thread;
    }

    private CommunityComment createComment(Long id, CommunityThread thread, AppUser author, String body) {
        CommunityComment comment = new CommunityComment();
        comment.setId(id);
        comment.setThread(thread);
        comment.setAuthor(author);
        comment.setBody(body);
        return comment;
    }
}