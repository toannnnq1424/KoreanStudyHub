package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/** Single-use, purpose-specific token for activating an imported account. */
@Entity
@Table(name = "account_activation_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_digest", nullable = false, length = 64, unique = true)
    private String tokenDigest;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public AccountActivationToken(User user, String tokenDigest, LocalDateTime expiresAt) {
        this.user = Objects.requireNonNull(user, "user");
        this.tokenDigest = requireText(tokenDigest, "tokenDigest");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isValidAt(LocalDateTime now) {
        return usedAt == null && now != null && !now.isAfter(expiresAt);
    }

    public void markUsed(LocalDateTime now) {
        if (usedAt == null) {
            usedAt = Objects.requireNonNull(now, "now");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
