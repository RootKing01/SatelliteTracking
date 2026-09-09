package com.satelliteTracking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "community_notifications")
public class CommunityNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private AppUser recipient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private CommunityThread thread;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_comment_id", nullable = false)
    private CommunityComment sourceComment;

    @Column(nullable = false, length = 32)
    private String notificationType = "THREAD_REPLY";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime readAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (notificationType == null || notificationType.isBlank()) {
            notificationType = "THREAD_REPLY";
        }
    }
}