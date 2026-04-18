package com.satelliteTracking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "community_threads",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_community_thread_target", columnNames = {"target_type", "target_id"})
    }
)
public class CommunityThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 24)
    private CommunityTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 128)
    private String targetId;

    @Column(nullable = false, length = 160)
    private String title;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private AppUser createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastCommentAt;

    @Column(nullable = false)
    private int commentCount = 0;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (title == null || title.isBlank()) {
            title = targetType + " #" + targetId;
        }
    }
}
