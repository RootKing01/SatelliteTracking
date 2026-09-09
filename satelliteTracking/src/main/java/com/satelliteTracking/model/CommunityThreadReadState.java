package com.satelliteTracking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "community_thread_read_states",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_community_thread_read_state_user_thread", columnNames = {"user_id", "thread_id"})
    }
)
public class CommunityThreadReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private CommunityThread thread;

    @Column
    private Long lastReadCommentId;

    @Column
    private LocalDateTime lastReadAt;
}