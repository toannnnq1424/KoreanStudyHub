-- KSH — durable short-lived previews for atomic, multi-node AI confirmation.
SET NAMES utf8mb4;

CREATE TABLE ai_question_draft_sessions (
    id CHAR(36) PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    test_id BIGINT NOT NULL,
    questions_json MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_ai_qdraft_actor_test (actor_id, test_id),
    INDEX idx_ai_qdraft_expiry (expires_at),
    CONSTRAINT chk_ai_qdraft_status CHECK (status IN ('PENDING', 'CONSUMED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
