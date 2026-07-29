package com.ksh.features.mail.outbox;

/**
 * Persistent lifecycle states for a durable mail outbox job.
 */
public enum MailOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SENT,
    FAILED
}
