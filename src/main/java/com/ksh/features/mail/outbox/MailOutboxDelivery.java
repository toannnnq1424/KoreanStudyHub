package com.ksh.features.mail.outbox;

/**
 * Immutable transport payload copied from a claimed outbox row.
 */
public record MailOutboxDelivery(
        Long jobId,
        String recipientEmail,
        String subject,
        String body) {
}
