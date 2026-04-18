package com.satelliteTracking.repository;

import com.satelliteTracking.model.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    List<CommunityComment> findByThreadIdOrderByCreatedAtAsc(Long threadId);

    Optional<CommunityComment> findByIdAndDeletedAtIsNull(Long id);

    Optional<CommunityComment> findTopByThreadIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long threadId);
}
