package com.ksh.KoreanStudyHub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "avatar", length = 255)
    private String avatar;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "provider", length = 20)
    private String provider = "LOCAL";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "failed_attempt_count")
    private Integer failedAttemptCount = 0;

    @Column(name = "lock_time")
    private LocalDateTime lockTime;

    public Integer getFailedAttemptCount() {
        return failedAttemptCount == null ? 0 : failedAttemptCount;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
