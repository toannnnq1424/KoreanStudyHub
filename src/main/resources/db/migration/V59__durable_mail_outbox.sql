-- Durable, retryable email outbox for notification-triggered mail.
--
-- Notification creation and outbox insertion share one database transaction.
-- Delivery is intentionally at-least-once: SMTP does not expose a portable
-- idempotency key, so a process crash after SMTP accepts a message but before
-- this row is marked SENT can result in a duplicate delivery.

CREATE TABLE mail_outbox_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body MEDIUMTEXT NOT NULL,
    source VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 8,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_owner VARCHAR(64) NULL,
    lease_expires_at DATETIME(6) NULL,
    last_error_code VARCHAR(50) NULL,
    sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,

    UNIQUE INDEX uq_mail_outbox_notification (notification_id),
    INDEX idx_mail_outbox_due (status, available_at, id),
    INDEX idx_mail_outbox_expired_lease (status, lease_expires_at, id),

    CONSTRAINT chk_mail_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'RETRY', 'SENT', 'FAILED')
    ),
    CONSTRAINT chk_mail_outbox_attempts CHECK (
        attempt_count >= 0 AND max_attempts > 0
    ),
    CONSTRAINT fk_mail_outbox_notification
        FOREIGN KEY (notification_id) REFERENCES notifications(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
