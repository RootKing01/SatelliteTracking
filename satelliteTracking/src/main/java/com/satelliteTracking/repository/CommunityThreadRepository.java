package com.satelliteTracking.repository;

import com.satelliteTracking.model.CommunityTargetType;
import com.satelliteTracking.model.CommunityThread;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityThreadRepository extends JpaRepository<CommunityThread, Long> {

    Optional<CommunityThread> findByTargetTypeAndTargetId(CommunityTargetType targetType, String targetId);

    List<CommunityThread> findByLastCommentAtIsNotNullOrderByLastCommentAtDesc(Pageable pageable);

    List<CommunityThread> findByStatusOrderByLastCommentAtDesc(String status, Pageable pageable);
}
