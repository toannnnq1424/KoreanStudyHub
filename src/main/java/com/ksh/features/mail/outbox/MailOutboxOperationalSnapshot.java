package com.ksh.features.mail.outbox;

import java.time.LocalDateTime;

/**
 * Non-PII operational read model for the durable mail outbox.
 *
 * <p>No recipient, subject, body, notification id or error detail is exposed.
 */
public record MailOutboxOperationalSnapshot(
        LocalDateTime observedAt,
        long pending,
        long processing,
        long retry,
        long sent,
        long failed,
        long readyClaimable,
        long expiredProcessingLeases,
        long oldestClaimableAgeSeconds) {

    public long totalJobs() {
        return pending + processing + retry + sent + failed;
    }

    public long totalClaimable() {
        return readyClaimable + expiredProcessingLeases;
    }
}
