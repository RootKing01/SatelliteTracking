package com.satelliteTracking.service;

import com.satelliteTracking.dto.CommunityCommentCreateRequestDTO;
import com.satelliteTracking.dto.CommunityCommentDTO;
import com.satelliteTracking.dto.CommunityCommentReportRequestDTO;
import com.satelliteTracking.dto.CommunityCommentUpdateRequestDTO;
import com.satelliteTracking.dto.CommunityNotificationDTO;
import com.satelliteTracking.dto.CommunityFeedItemDTO;
import com.satelliteTracking.dto.CommunityThreadCreateRequestDTO;
import com.satelliteTracking.dto.CommunityThreadLikeDTO;
import com.satelliteTracking.dto.CommunityThreadDTO;
import com.satelliteTracking.dto.CommunityThreadReadStateDTO;
import com.satelliteTracking.dto.CommunityThreadReadStateUpdateRequestDTO;
import com.satelliteTracking.dto.CommunityThreadWithCommentsDTO;
import com.satelliteTracking.model.AppUser;
import com.satelliteTracking.model.CommunityComment;
import com.satelliteTracking.model.CommunityCommentReport;
import com.satelliteTracking.model.CommunityNotification;
import com.satelliteTracking.model.CommunityTargetType;
import com.satelliteTracking.model.CommunityThread;
import com.satelliteTracking.model.CommunityThreadLike;
import com.satelliteTracking.model.CommunityThreadReadState;
import com.satelliteTracking.repository.CommunityCommentReportRepository;
import com.satelliteTracking.repository.CommunityCommentRepository;
import com.satelliteTracking.repository.CommunityNotificationRepository;
import com.satelliteTracking.repository.CommunityThreadLikeRepository;
import com.satelliteTracking.repository.CommunityThreadRepository;
import com.satelliteTracking.repository.CommunityThreadReadStateRepository;
import org.springframework.data.domain.PageRequest;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.model.Satellite;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CommunityService {

    private static final int MAX_COMMENT_LENGTH = 2000;
    private static final int MAX_REPORT_REASON_LENGTH = 300;
    private static final int MAX_THREAD_TITLE_LENGTH = 160;

    private final CommunityThreadRepository communityThreadRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityCommentReportRepository communityCommentReportRepository;
    private final CommunityNotificationRepository communityNotificationRepository;
    private final CommunityThreadLikeRepository communityThreadLikeRepository;
    private final CommunityThreadReadStateRepository communityThreadReadStateRepository;
    private final AuthService authService;
    private final SatelliteRepository satelliteRepository;

    public CommunityService(CommunityThreadRepository communityThreadRepository,
                            CommunityCommentRepository communityCommentRepository,
                            CommunityCommentReportRepository communityCommentReportRepository,
                            CommunityNotificationRepository communityNotificationRepository,
                            CommunityThreadLikeRepository communityThreadLikeRepository,
                            CommunityThreadReadStateRepository communityThreadReadStateRepository,
                            AuthService authService,
                            SatelliteRepository satelliteRepository) {
        this.communityThreadRepository = communityThreadRepository;
        this.communityCommentRepository = communityCommentRepository;
        this.communityCommentReportRepository = communityCommentReportRepository;
        this.communityNotificationRepository = communityNotificationRepository;
        this.communityThreadLikeRepository = communityThreadLikeRepository;
        this.communityThreadReadStateRepository = communityThreadReadStateRepository;
        this.authService = authService;
        this.satelliteRepository = satelliteRepository;
    }

    @Transactional
    public CommunityThreadWithCommentsDTO getThreadWithComments(String targetTypeRaw, String targetIdRaw) {
        AppUser user = authService.getAuthenticatedUserOrNull();
        CommunityTargetType targetType = parseTargetType(targetTypeRaw);
        String targetId = normalizeRequired(targetIdRaw, "targetId", 128);

        CommunityThread thread = communityThreadRepository
            .findByTargetTypeAndTargetId(targetType, targetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread non trovato"));
        List<CommunityCommentDTO> comments = communityCommentRepository
            .findByThreadIdOrderByCreatedAtAsc(thread.getId())
            .stream()
            .map(this::toCommentDTO)
            .toList();

        return new CommunityThreadWithCommentsDTO(toThreadDTO(thread, user), comments, toReadStateDTO(thread, user));
    }

    @Transactional
    public CommunityThreadWithCommentsDTO ensureThreadWithComments(String targetTypeRaw, String targetIdRaw) {
        // Ensure (create if missing) requires authentication
        AppUser user = authService.requireAuthenticatedUser();
        CommunityTargetType targetType = parseTargetType(targetTypeRaw);
        String targetId = normalizeRequired(targetIdRaw, "targetId", 128);

        CommunityThread thread = communityThreadRepository
            .findByTargetTypeAndTargetId(targetType, targetId)
            .orElseGet(() -> getOrCreateThread(targetType, targetId, user, buildDefaultThreadTitle(targetType, targetId)));

        List<CommunityCommentDTO> comments = communityCommentRepository
            .findByThreadIdOrderByCreatedAtAsc(thread.getId())
            .stream()
            .map(this::toCommentDTO)
            .toList();

        return new CommunityThreadWithCommentsDTO(toThreadDTO(thread, user), comments, toReadStateDTO(thread, user));
    }

    @Transactional
    public CommunityThreadWithCommentsDTO createGeneralThread(CommunityThreadCreateRequestDTO request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Richiesta thread non valida");
        }

        AppUser user = authService.requireAuthenticatedUser();
        String title = normalizeRequired(request.title(), "title", MAX_THREAD_TITLE_LENGTH);
        String body = normalizeRequired(request.body(), "body", MAX_COMMENT_LENGTH);

        CommunityThread thread = new CommunityThread();
        thread.setTargetType(CommunityTargetType.GENERAL);
        thread.setTargetId(UUID.randomUUID().toString());
        thread.setTitle(title);
        thread.setCreatedBy(user);
        CommunityThread savedThread = communityThreadRepository.save(thread);

        CommunityComment firstComment = new CommunityComment();
        firstComment.setThread(savedThread);
        firstComment.setAuthor(user);
        firstComment.setBody(body);
        CommunityComment savedComment = communityCommentRepository.save(firstComment);

        savedThread.setCommentCount(1);
        savedThread.setLastCommentAt(savedComment.getCreatedAt());
        communityThreadRepository.save(savedThread);

        return new CommunityThreadWithCommentsDTO(
            toThreadDTO(savedThread, user),
            List.of(toCommentDTO(savedComment)),
            toReadStateDTO(savedThread, user)
        );
    }

    @Transactional
    public CommunityCommentDTO createComment(String targetTypeRaw,
                                             String targetIdRaw,
                                             CommunityCommentCreateRequestDTO request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Richiesta commento non valida");
        }

        AppUser user = authService.requireAuthenticatedUser();
        CommunityTargetType targetType = parseTargetType(targetTypeRaw);
        String targetId = normalizeRequired(targetIdRaw, "targetId", 128);
        String body = normalizeRequired(request.body(), "body", MAX_COMMENT_LENGTH);

        CommunityThread thread = getOrCreateThread(targetType, targetId, user, buildDefaultThreadTitle(targetType, targetId));

        CommunityComment parentComment = null;
        if (request.parentCommentId() != null) {
            parentComment = communityCommentRepository.findByIdAndDeletedAtIsNull(request.parentCommentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commento padre non trovato"));
            if (!parentComment.getThread().getId().equals(thread.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commento padre non coerente con il thread");
            }
        }

        CommunityComment comment = new CommunityComment();
        comment.setThread(thread);
        comment.setParentComment(parentComment);
        comment.setAuthor(user);
        comment.setBody(body);

        CommunityComment saved = communityCommentRepository.save(comment);

        if (parentComment != null && !parentComment.getAuthor().getId().equals(user.getId())) {
            CommunityNotification notification = new CommunityNotification();
            notification.setRecipient(parentComment.getAuthor());
            notification.setThread(thread);
            notification.setSourceComment(saved);
            notification.setNotificationType("THREAD_REPLY");
            communityNotificationRepository.save(notification);
        }

        thread.setCommentCount(thread.getCommentCount() + 1);
        thread.setLastCommentAt(saved.getCreatedAt());
        communityThreadRepository.save(thread);

        return toCommentDTO(saved);
    }

    @Transactional
    public CommunityCommentDTO updateComment(Long commentId, CommunityCommentUpdateRequestDTO request) {
        if (commentId == null || request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Richiesta aggiornamento non valida");
        }

        AppUser user = authService.requireAuthenticatedUser();
        String body = normalizeRequired(request.body(), "body", MAX_COMMENT_LENGTH);

        CommunityComment comment = communityCommentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commento non trovato"));

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non puoi modificare questo commento");
        }

        comment.setBody(body);
        CommunityComment saved = communityCommentRepository.save(comment);
        return toCommentDTO(saved);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        if (commentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id commento non valido");
        }

        AppUser user = authService.requireAuthenticatedUser();
        CommunityComment comment = communityCommentRepository.findById(commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commento non trovato"));

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non puoi eliminare questo commento");
        }

        if (comment.getDeletedAt() != null) {
            return;
        }

        comment.setDeletedAt(LocalDateTime.now());
        comment.setBody("[commento rimosso]");
        communityCommentRepository.save(comment);

        CommunityThread thread = comment.getThread();
        thread.setCommentCount(Math.max(0, thread.getCommentCount() - 1));
        communityThreadRepository.save(thread);
    }

    @Transactional
    public void reportComment(Long commentId, CommunityCommentReportRequestDTO request) {
        if (commentId == null || request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Richiesta segnalazione non valida");
        }

        AppUser user = authService.requireAuthenticatedUser();
        String reason = normalizeRequired(request.reason(), "reason", MAX_REPORT_REASON_LENGTH);

        CommunityComment comment = communityCommentRepository.findById(commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commento non trovato"));

        CommunityCommentReport report = new CommunityCommentReport();
        report.setComment(comment);
        report.setReporter(user);
        report.setReason(reason);
        communityCommentReportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<CommunityFeedItemDTO> getFeed(int limit) {
        AppUser user = authService.getAuthenticatedUserOrNull();
        int normalizedLimit = Math.max(1, Math.min(limit, 50));

        return communityThreadRepository
            .findByStatusOrderByLastCommentAtDesc("ACTIVE", PageRequest.of(0, normalizedLimit))
            .stream()
            .map(thread -> toFeedItemDTO(thread, user))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunityFeedItemDTO> getFeaturedThreads(int limit) {
        AppUser user = authService.getAuthenticatedUserOrNull();
        int normalizedLimit = Math.max(1, Math.min(limit, 20));
        // Fetch a reasonably large page then compute a score based on likes and comments.
        // Prefer threads with positive engagement (score>0). If none have positive
        // engagement, fall back to top N by last comment date so the UI isn't empty.
        List<CommunityThread> candidates = communityThreadRepository
            .findByStatusOrderByLastCommentAtDesc("ACTIVE", PageRequest.of(0, 100));

        // Compute score and sort by score desc, then by last comment desc.
        var scored = candidates.stream()
            .map(t -> new Object() {
                final CommunityThread thread = t;
                final long likes = communityThreadLikeRepository.countByThreadId(t.getId());
                final long score = likes * 2 + t.getCommentCount(); // weight likes higher
            })
            .sorted((a, b) -> {
                int cmp = Long.compare(b.score, a.score);
                if (cmp != 0) return cmp;
                java.time.LocalDateTime aLast = a.thread.getLastCommentAt();
                java.time.LocalDateTime bLast = b.thread.getLastCommentAt();
                if (aLast == null && bLast == null) return 0;
                if (aLast == null) return 1;
                if (bLast == null) return -1;
                return bLast.compareTo(aLast);
            })
            .toList();

        // Filter only positively engaged threads for featured list
        List<CommunityThread> positiveFeatured = scored.stream()
            .filter(x -> x.score > 0)
            .map(x -> x.thread)
            .limit(normalizedLimit)
            .toList();

        if (!positiveFeatured.isEmpty()) {
            return positiveFeatured.stream().map(thread -> toFeedItemDTO(thread, user)).toList();
        }

        // Fallback: return top N threads by last comment date
        return scored.stream()
            .map(x -> x.thread)
            .limit(normalizedLimit)
            .map(thread -> toFeedItemDTO(thread, user))
            .toList();
    }

    @Transactional
    public CommunityThreadLikeDTO toggleThreadLike(Long threadId) {
        if (threadId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id thread non valido");
        }

        AppUser user = authService.requireAuthenticatedUser();
        CommunityThread thread = communityThreadRepository.findById(threadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread non trovato"));

        boolean likedByMe;
        CommunityThreadLike existingLike = communityThreadLikeRepository.findByThreadIdAndUserId(threadId, user.getId())
            .orElse(null);

        if (existingLike == null) {
            CommunityThreadLike like = new CommunityThreadLike();
            like.setThread(thread);
            like.setUser(user);
            communityThreadLikeRepository.save(like);
            likedByMe = true;
        } else {
            communityThreadLikeRepository.delete(existingLike);
            likedByMe = false;
        }

        long likesCount = communityThreadLikeRepository.countByThreadId(threadId);
        return new CommunityThreadLikeDTO(threadId, likesCount, likedByMe);
    }

    @Transactional(readOnly = true)
    public List<CommunityNotificationDTO> getNotifications(int limit) {
        AppUser user = authService.requireAuthenticatedUser();
        int normalizedLimit = Math.max(1, Math.min(limit, 50));

        return communityNotificationRepository
            .findByRecipientIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, normalizedLimit))
            .stream()
            .map(this::toNotificationDTO)
            .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadNotificationCount() {
        AppUser user = authService.requireAuthenticatedUser();
        return communityNotificationRepository.countByRecipientIdAndReadAtIsNull(user.getId());
    }

    @Transactional
    public CommunityNotificationDTO markNotificationAsRead(Long notificationId) {
        if (notificationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id notifica non valido");
        }

        AppUser user = authService.requireAuthenticatedUser();
        CommunityNotification notification = communityNotificationRepository
            .findByIdAndRecipientId(notificationId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notifica non trovata"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            communityNotificationRepository.save(notification);
        }

        return toNotificationDTO(notification);
    }

    @Transactional(readOnly = true)
    public CommunityThreadReadStateDTO getThreadReadState(Long threadId) {
        if (threadId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id thread non valido");
        }

        AppUser user = authService.requireAuthenticatedUser();
        CommunityThread thread = communityThreadRepository.findById(threadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread non trovato"));

        CommunityThreadReadState readState = communityThreadReadStateRepository
            .findByUserIdAndThreadId(user.getId(), thread.getId())
            .orElse(null);

        long unreadReplyCount = communityNotificationRepository
            .findByRecipientIdAndThreadIdAndReadAtIsNullOrderByCreatedAtAsc(user.getId(), thread.getId())
            .size();

        return new CommunityThreadReadStateDTO(
            thread.getId(),
            readState != null ? readState.getLastReadCommentId() : null,
            readState != null ? readState.getLastReadAt() : null,
            unreadReplyCount
        );
    }

    @Transactional
    public CommunityThreadReadStateDTO updateThreadReadState(Long threadId, CommunityThreadReadStateUpdateRequestDTO request) {
        if (threadId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id thread non valido");
        }

        AppUser user = authService.requireAuthenticatedUser();
        CommunityThread thread = communityThreadRepository.findById(threadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread non trovato"));

        Long lastReadCommentId = request != null ? request.lastReadCommentId() : null;
        if (lastReadCommentId != null) {
            CommunityComment lastReadComment = communityCommentRepository.findByIdAndDeletedAtIsNull(lastReadCommentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commento di lettura non trovato"));
            if (!lastReadComment.getThread().getId().equals(thread.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commento di lettura non coerente con il thread");
            }
        }

        CommunityThreadReadState readState = communityThreadReadStateRepository
            .findByUserIdAndThreadId(user.getId(), thread.getId())
            .orElseGet(() -> {
                CommunityThreadReadState state = new CommunityThreadReadState();
                state.setUser(user);
                state.setThread(thread);
                return state;
            });

        if (lastReadCommentId != null) {
            readState.setLastReadCommentId(lastReadCommentId);
        }
        readState.setLastReadAt(LocalDateTime.now());
        communityThreadReadStateRepository.save(readState);

        if (lastReadCommentId != null) {
            List<CommunityNotification> unreadNotifications = communityNotificationRepository
                .findByRecipientIdAndThreadIdAndReadAtIsNullOrderByCreatedAtAsc(user.getId(), thread.getId());
            for (CommunityNotification notification : unreadNotifications) {
                Long sourceCommentId = notification.getSourceComment() != null ? notification.getSourceComment().getId() : null;
                if (sourceCommentId != null && sourceCommentId <= lastReadCommentId) {
                    notification.setReadAt(LocalDateTime.now());
                }
            }
            communityNotificationRepository.saveAll(unreadNotifications);
        }

        long unreadReplyCount = communityNotificationRepository
            .findByRecipientIdAndThreadIdAndReadAtIsNullOrderByCreatedAtAsc(user.getId(), thread.getId())
            .size();

        return new CommunityThreadReadStateDTO(thread.getId(), readState.getLastReadCommentId(), readState.getLastReadAt(), unreadReplyCount);
    }

    private CommunityThread getOrCreateThread(CommunityTargetType targetType,
                                              String targetId,
                                              AppUser user,
                                              String defaultTitle) {
        return communityThreadRepository.findByTargetTypeAndTargetId(targetType, targetId)
            .orElseGet(() -> {
                CommunityThread thread = new CommunityThread();
                thread.setTargetType(targetType);
                thread.setTargetId(targetId);
                thread.setTitle(defaultTitle);
                thread.setCreatedBy(user);
                return communityThreadRepository.save(thread);
            });
    }

    private CommunityTargetType parseTargetType(String rawValue) {
        String normalized = normalizeRequired(rawValue, "targetType", 24).toUpperCase();
        try {
            return CommunityTargetType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target type non supportato");
        }
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo obbligatorio: " + fieldName);
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " supera la lunghezza massima");
        }
        return normalized;
    }

    private CommunityCommentDTO toCommentDTO(CommunityComment comment) {
        return new CommunityCommentDTO(
            comment.getId(),
            comment.getThread().getId(),
            comment.getParentComment() == null ? null : comment.getParentComment().getId(),
            comment.getAuthor().getId(),
            comment.getAuthor().getUsername(),
            comment.getBody(),
            comment.getCreatedAt(),
            comment.getUpdatedAt(),
            comment.getDeletedAt() != null
        );
    }

    private CommunityNotificationDTO toNotificationDTO(CommunityNotification notification) {
        CommunityComment sourceComment = notification.getSourceComment();
        CommunityThread thread = notification.getThread();

        return new CommunityNotificationDTO(
            notification.getId(),
            notification.getNotificationType(),
            thread.getId(),
            thread.getTitle(),
            thread.getTargetType().name(),
            thread.getTargetId(),
            sourceComment != null ? sourceComment.getId() : null,
            sourceComment != null ? sourceComment.getAuthor().getUsername() : null,
            sourceComment != null ? buildPreview(sourceComment.getBody()) : "",
            notification.getCreatedAt(),
            notification.getReadAt()
        );
    }

    private CommunityThreadReadStateDTO toReadStateDTO(CommunityThread thread, AppUser currentUser) {
        if (currentUser == null) {
            return null;
        }

        CommunityThreadReadState readState = communityThreadReadStateRepository
            .findByUserIdAndThreadId(currentUser.getId(), thread.getId())
            .orElse(null);

        long unreadReplyCount = communityNotificationRepository
            .findByRecipientIdAndThreadIdAndReadAtIsNullOrderByCreatedAtAsc(currentUser.getId(), thread.getId())
            .size();

        return new CommunityThreadReadStateDTO(
            thread.getId(),
            readState != null ? readState.getLastReadCommentId() : null,
            readState != null ? readState.getLastReadAt() : null,
            unreadReplyCount
        );
    }

    private CommunityThreadDTO toThreadDTO(CommunityThread thread, AppUser currentUser) {
        long likesCount = communityThreadLikeRepository.countByThreadId(thread.getId());
        boolean likedByMe = currentUser != null && communityThreadLikeRepository
            .findByThreadIdAndUserId(thread.getId(), currentUser.getId())
            .isPresent();

        return new CommunityThreadDTO(
            thread.getId(),
            thread.getTargetType().name(),
            thread.getTargetId(),
            thread.getTitle(),
            thread.getCommentCount(),
            likesCount,
            likedByMe,
            thread.getCreatedAt(),
            thread.getLastCommentAt()
        );
    }

    private CommunityFeedItemDTO toFeedItemDTO(CommunityThread thread, AppUser currentUser) {
        String preview = communityCommentRepository
            .findTopByThreadIdAndDeletedAtIsNullOrderByCreatedAtDesc(thread.getId())
            .map(comment -> buildPreview(comment.getBody()))
            .orElse("Nessun commento visibile");

        long likesCount = communityThreadLikeRepository.countByThreadId(thread.getId());
        boolean likedByMe = currentUser != null && communityThreadLikeRepository
            .findByThreadIdAndUserId(thread.getId(), currentUser.getId())
            .isPresent();

        return new CommunityFeedItemDTO(
            thread.getId(),
            thread.getTargetType().name(),
            thread.getTargetId(),
            thread.getTitle(),
            thread.getCommentCount(),
            likesCount,
            likedByMe,
            thread.getLastCommentAt(),
            preview
        );
    }

    private String buildDefaultThreadTitle(CommunityTargetType targetType, String targetId) {
        return switch (targetType) {
            case SATELLITE -> {
                // Prova a recuperare il nome del satellite dal repository
                String name = satelliteRepository.findByNoradCatId(parseLongSafe(targetId))
                        .map(Satellite::getObjectName)
                        .orElse(null);
                if (name != null && !name.isBlank()) {
                    yield name;
                } else {
                    yield "Satellite #" + targetId;
                }
            }
            case SIGHTING -> "Avvistamento #" + targetId;
            case PASS -> "Passaggio #" + targetId;
            case GENERAL -> "Discussione generale";
        };
    }

    private Long parseLongSafe(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildPreview(String body) {
        String normalized = body == null ? "" : body.trim();
        if (normalized.length() <= 90) {
            return normalized;
        }
        return normalized.substring(0, 87) + "...";
    }
}
