package com.ksh.features.mail.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Durable email delivery request.
 *
 * <p>The recipient and message are snapshots taken in the same transaction as
 * the originating notification. A lease prevents two application nodes from
 * deliberately delivering the same row at the same time. Delivery remains
 * at-least-once because SMTP has no portable idempotency-key contract.
 */
@Entity
@Table(name = "mail_outbox_jobs")
public class MailOutboxJob {

    public static final int DEFAULT_MAX_ATTEMPTS = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String body;

    @Column(nullable = false, length = 50)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MailOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected MailOutboxJob() {
    }

    public static MailOutboxJob pending(
            Long notificationId,
            String recipientEmail,
            String subject,
            String body,
            String source,
            LocalDateTime now) {
        Objects.requireNonNull(notificationId, "notificationId");
        Objects.requireNonNull(now, "now");

        MailOutboxJob job = new MailOutboxJob();
        job.notificationId = notificationId;
        job.recipientEmail = requireText(recipientEmail, 255, "recipientEmail");
        job.subject = requireText(subject, 500, "subject");
        job.body = Objects.requireNonNull(body, "body");
        job.source = requireText(source, 50, "source");
        job.status = MailOutboxStatus.PENDING;
        job.attemptCount = 0;
        job.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        job.availableAt = now;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public boolean isClaimable(LocalDateTime now) {
        if (now == null) {
            return false;
        }
        boolean dueReadyJob = (status == MailOutboxStatus.PENDING
                || status == MailOutboxStatus.RETRY)
                && availableAt != null
                && !availableAt.isAfter(now);
        boolean expiredLease = status == MailOutboxStatus.PROCESSING
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now);
        return dueReadyJob || expiredLease;
    }

    public boolean attemptsExhausted() {
        return attemptCount >= maxAttempts;
    }

    public void claim(String owner, LocalDateTime now, Duration leaseDuration) {
        if (!isClaimable(now) || attemptsExhausted()) {
            throw new IllegalStateException("Mail outbox job is not claimable.");
        }
        String validatedOwner = requireText(owner, 64, "owner");
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive.");
        }

        status = MailOutboxStatus.PROCESSING;
        attemptCount++;
        availableAt = now;
        leaseOwner = validatedOwner;
        leaseExpiresAt = now.plus(leaseDuration);
        lastErrorCode = null;
        updatedAt = now;
    }

    public boolean isOwnedBy(String owner) {
        return status == MailOutboxStatus.PROCESSING
                && owner != null
                && owner.equals(leaseOwner);
    }

    public void markSent(LocalDateTime now) {
        status = MailOutboxStatus.SENT;
        sentAt = Objects.requireNonNull(now, "now");
        availableAt = now;
        clearLease();
        lastErrorCode = null;
        updatedAt = now;
    }

    public void scheduleRetry(LocalDateTime now, LocalDateTime nextAttempt, String errorCode) {
        status = MailOutboxStatus.RETRY;
        availableAt = Objects.requireNonNull(nextAttempt, "nextAttempt");
        clearLease();
        lastErrorCode = requireText(errorCode, 50, "errorCode");
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void markFailed(LocalDateTime now, String errorCode) {
        status = MailOutboxStatus.FAILED;
        availableAt = Objects.requireNonNull(now, "now");
        clearLease();
        lastErrorCode = requireText(errorCode, 50, "errorCode");
        updatedAt = now;
    }

    private void clearLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getSource() {
        return source;
    }

    public MailOutboxStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public LocalDateTime getAvailableAt() {
        return availableAt;
    }

    String getLeaseOwner() {
        return leaseOwner;
    }

    public LocalDateTime getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
