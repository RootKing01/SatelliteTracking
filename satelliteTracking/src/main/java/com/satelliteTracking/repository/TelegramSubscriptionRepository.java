package com.satelliteTracking.repository;

import com.satelliteTracking.model.TelegramSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramSubscriptionRepository extends JpaRepository<TelegramSubscription, Long> {
    Optional<TelegramSubscription> findByChatId(Long chatId);
    
    Optional<TelegramSubscription> findByUserIdentifier(String userIdentifier);
    
    List<TelegramSubscription> findByNotificationsEnabledTrue();
}
