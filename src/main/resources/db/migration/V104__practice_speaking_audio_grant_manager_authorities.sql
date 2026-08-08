-- Append-only authority assignments for branch-B reviewer grant managers.
-- No development user is seeded and no broad ADMIN/LECTURER role is implied.

CREATE TABLE practice_speaking_audio_grant_manager_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(80) NOT NULL,
    subject_user_id BIGINT NOT NULL,
    authority_code VARCHAR(40) NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    evidence_id VARCHAR(160) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_psagme_event_key UNIQUE (event_key),
    CONSTRAINT uk_psagme_evidence_id UNIQUE (evidence_id),
    INDEX idx_psagme_current
        (subject_user_id, authority_code, occurred_at, id),
    CONSTRAINT fk_psagme_subject FOREIGN KEY (subject_user_id) REFERENCES users(id),
    CONSTRAINT fk_psagme_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT chk_psagme_authority CHECK (authority_code IN (
        'ACADEMIC_LEADER', 'PRIVACY_RELEASE_OWNER')),
    CONSTRAINT chk_psagme_event CHECK (event_type IN ('ASSIGNED','REVOKED')),
    CONSTRAINT chk_psagme_event_key CHECK (
        event_key REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
