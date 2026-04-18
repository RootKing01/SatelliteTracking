package com.satelliteTracking.repository;

import com.satelliteTracking.model.CommunityThreadLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityThreadLikeRepository extends JpaRepository<CommunityThreadLike, Long> {

    long countByThreadId(Long threadId);

    Optional<CommunityThreadLike> findByThreadIdAndUserId(Long threadId, Long userId);
}
