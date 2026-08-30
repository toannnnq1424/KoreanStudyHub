-- Admin bulk account import with owner-completed email activation.
--
-- Existing accounts predate this lifecycle and are backfilled as already
-- activated. Newly imported accounts deliberately keep activated_at NULL
-- until the owner consumes a single-use activation link.

ALTER TABLE users
    ADD COLUMN activated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6)
        AFTER last_login_at;

UPDATE users
SET activated_at = COALESCE(last_login_at, created_at, CURRENT_TIMESTAMP(6))
WHERE activated_at IS NULL;

CREATE TABLE account_activation_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_digest VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    UNIQUE INDEX uq_account_activation_token_digest (token_digest),
    INDEX idx_account_activation_user_unused (user_id, used_at),
    INDEX idx_account_activation_expiry (expires_at),

    CONSTRAINT fk_account_activation_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
