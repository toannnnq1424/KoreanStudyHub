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

/**
 * JPA entity mapped to the {@code user_verification_tokens} table.
 *
 * <p>Represents a single-use account-activation token. A token is valid only
 * while it has neither been consumed nor passed its expiry (7 days after
 * issuance).
 *
 * <p><b>Why this is a separate table from {@code password_reset_tokens}.</b>
 * Neither table carries a type discriminator, so a shared table would let a
 * token issued for one purpose satisfy the other's lookup. Keeping them apart
 * makes that confusion impossible by construction, and the two genuinely have
 * different lifetimes (7 days versus 1 hour).
 */
@Entity
@Table(name = "user_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 128, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * Creates a new activation token for the given account.
     *
     * @param user      the account whose owner must activate it
     * @param token     the raw token string (must be cryptographically random)
     * @param expiresAt the point in time after which this token is no longer valid
     */
    public UserVerificationToken(User user, String token, LocalDateTime expiresAt) {
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    // ── Business logic ─────────────────────────────────────

    /**
     * Returns {@code true} if this token has already been consumed.
     *
     * @return {@code true} when {@code usedAt} is non-null
     */
    public boolean isUsed() { return usedAt != null; }

    /**
     * Returns {@code true} if the current time is past the token's expiry timestamp.
     *
     * @return {@code true} when the token has expired
     */
    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }

    /**
     * Returns {@code true} if this token can still activate its account.
     *
     * @return {@code true} when the token is both unused and within its validity window
     */
    public boolean isValid() { return !isUsed() && !isExpired(); }

    /**
     * Marks this token as consumed by recording the current timestamp in {@code usedAt}.
     *
     * <p>Called immediately after a successful activation so the same link
     * cannot activate anything again.
     */
    public void markUsed() {
        this.usedAt = LocalDateTime.now();
    }
}