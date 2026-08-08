-- Speaking branch B authorization persistence. No audio bytes, storage keys,
-- provider request IDs or secrets belong in either table.

CREATE TABLE practice_speaking_audio_consent_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(80) NOT NULL,
    consent_chain_key VARCHAR(80) NOT NULL,
    learner_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    purpose_code VARCHAR(64) NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    disclosure_version VARCHAR(80) NOT NULL,
    evidence_id VARCHAR(160) NOT NULL,
    occurred_at DATETIME NOT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_psace_event_key UNIQUE (event_key),
    CONSTRAINT uk_psace_evidence_id UNIQUE (evidence_id),
    CONSTRAINT uk_psace_chain_event UNIQUE (consent_chain_key, event_type),
    INDEX idx_psace_authority
        (learner_id, attempt_id, purpose_code, occurred_at, id),
    CONSTRAINT fk_psace_learner FOREIGN KEY (learner_id) REFERENCES users(id),
    CONSTRAINT fk_psace_attempt FOREIGN KEY (attempt_id) REFERENCES practice_attempts(id),
    CONSTRAINT chk_psace_purpose CHECK (
        purpose_code = 'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'),
    CONSTRAINT chk_psace_event CHECK (event_type IN ('GRANTED','WITHDRAWN')),
    CONSTRAINT chk_psace_event_key CHECK (
        event_key REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$'),
    CONSTRAINT chk_psace_chain_key CHECK (
        consent_chain_key REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_speaking_audio_reviewer_grants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    grant_key VARCHAR(80) NOT NULL,
    attempt_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    granted_by BIGINT NOT NULL,
    purpose_code VARCHAR(64) NOT NULL,
    evidence_id VARCHAR(160) NOT NULL,
    granted_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    revoked_by BIGINT NULL,
    revoke_evidence_id VARCHAR(160) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_psarg_grant_key UNIQUE (grant_key),
    CONSTRAINT uk_psarg_evidence_id UNIQUE (evidence_id),
    INDEX idx_psarg_authority
        (reviewer_id, attempt_id, purpose_code, expires_at, revoked_at),
    CONSTRAINT fk_psarg_attempt FOREIGN KEY (attempt_id) REFERENCES practice_attempts(id),
    CONSTRAINT fk_psarg_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id),
    CONSTRAINT fk_psarg_granted_by FOREIGN KEY (granted_by) REFERENCES users(id),
    CONSTRAINT fk_psarg_revoked_by FOREIGN KEY (revoked_by) REFERENCES users(id),
    CONSTRAINT chk_psarg_purpose CHECK (
        purpose_code = 'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'),
    CONSTRAINT chk_psarg_expiry CHECK (expires_at > granted_at),
    CONSTRAINT chk_psarg_revocation CHECK (
        (revoked_at IS NULL AND revoked_by IS NULL AND revoke_evidence_id IS NULL)
        OR
        (revoked_at IS NOT NULL AND revoked_by IS NOT NULL
            AND revoke_evidence_id IS NOT NULL AND revoked_at >= granted_at)),
    CONSTRAINT chk_psarg_grant_key CHECK (
        grant_key REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
