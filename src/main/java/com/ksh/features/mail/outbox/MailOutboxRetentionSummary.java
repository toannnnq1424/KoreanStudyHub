package com.ksh.features.mail.outbox;

import java.time.LocalDateTime;

/**
 * Bounded retention outcome containing counts and cutoffs only.
 */
public record MailOutboxRetentionSummary(
        LocalDateTime observedAt,
        LocalDateTime sentCutoff,
        LocalDateTime failedCutoff,
        int batchLimit,
        int sentDeleted,
        int failedDeleted,
        MailOutboxOperationalSnapshot snapshotAfter) {

    public int totalDeleted() {
        return sentDeleted + failedDeleted;
    }
}
