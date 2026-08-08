-- Reviewer-only, non-score-bearing acoustic observations for Speaking branch B.
-- The payload is a backend-built numeric/timestamp projection: no raw audio,
-- linguistic text, free-text provider observation, token, storage key, secret or raw
-- provider request identifier may be stored here.

CREATE TABLE practice_speaking_direct_audio_dark_observations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    observation_key VARCHAR(80) NOT NULL,
    attempt_id BIGINT NOT NULL,
    contract_version VARCHAR(64) NOT NULL,
    language_code VARCHAR(16) NOT NULL,
    evaluator_id VARCHAR(120) NOT NULL,
    model_name VARCHAR(160) NOT NULL,
    calibration_profile_id VARCHAR(160) NOT NULL,
    calibration_version VARCHAR(80) NOT NULL,
    receipt_fingerprint CHAR(64) NOT NULL,
    provider_cache_fingerprint CHAR(64) NOT NULL,
    provider_observation_total DECIMAL(5,4) NOT NULL,
    provider_confidence DECIMAL(5,4) NOT NULL,
    observation_payload JSON NOT NULL,
    retention_policy_id VARCHAR(120) NOT NULL,
    captured_at DATETIME NOT NULL,
    delete_after DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    deletion_evidence_id VARCHAR(160) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_psdado_observation_key UNIQUE (observation_key),
    INDEX idx_psdado_reviewer_window (attempt_id, deleted_at, delete_after, captured_at),
    CONSTRAINT fk_psdado_attempt FOREIGN KEY (attempt_id) REFERENCES practice_attempts(id),
    CONSTRAINT fk_psdado_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id),
    CONSTRAINT chk_psdado_contract CHECK (
        contract_version = 'ksh-speaking-direct-audio-acoustic-v1'
        AND language_code = 'ko-KR'),
    CONSTRAINT chk_psdado_retention_policy CHECK (
        retention_policy_id = 'KSH-SPEAKING-DIRECT-AUDIO-DISCLOSURE-V1'),
    CONSTRAINT chk_psdado_fingerprints CHECK (
        receipt_fingerprint REGEXP '^[0-9a-f]{64}$'
        AND provider_cache_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_psdado_ranges CHECK (
        provider_observation_total >= 0 AND provider_observation_total <= 2
        AND provider_confidence >= 0 AND provider_confidence <= 1),
    CONSTRAINT chk_psdado_retention_window CHECK (
        delete_after > captured_at
        AND delete_after <= DATE_ADD(captured_at, INTERVAL 30 DAY)),
    CONSTRAINT chk_psdado_deletion CHECK (
        (deleted_at IS NULL AND deleted_by IS NULL AND deletion_evidence_id IS NULL)
        OR
        (deleted_at IS NOT NULL AND deleted_by IS NOT NULL
            AND deletion_evidence_id IS NOT NULL AND deleted_at >= captured_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
