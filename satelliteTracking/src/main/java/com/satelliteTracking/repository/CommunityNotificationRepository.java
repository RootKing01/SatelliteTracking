package com.satelliteTracking.repository;

import com.satelliteTracking.model.CommunityNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityNotificationRepository extends JpaRepository<CommunityNotification, Long> {

    List<CommunityNotification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    Optional<CommunityNotification> findByIdAndRecipientId(Long id, Long recipientId);

    List<CommunityNotification> findByRecipientIdAndThreadIdAndReadAtIsNullOrderByCreatedAtAsc(Long recipientId, Long threadId);
}