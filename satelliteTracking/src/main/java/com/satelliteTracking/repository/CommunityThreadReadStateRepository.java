package com.satelliteTracking.repository;

import com.satelliteTracking.model.CommunityThreadReadState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityThreadReadStateRepository extends JpaRepository<CommunityThreadReadState, Long> {

    Optional<CommunityThreadReadState> findByUserIdAndThreadId(Long userId, Long threadId);
}