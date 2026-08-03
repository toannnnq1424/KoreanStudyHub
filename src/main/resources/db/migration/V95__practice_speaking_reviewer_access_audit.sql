-- Immutable metadata-only audit for authorized reviewer dark-result access.
-- No audio, storage identity, network metadata, provider payload or score is stored.

CREATE TABLE practice_speaking_audio_reviewer_access_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key CHAR(36) NOT NULL,
    reviewer_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    media_id BIGINT NOT NULL,
    observation_key VARCHAR(80) NOT NULL,
    purpose_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    outcome_code VARCHAR(16) NOT NULL,
    occurred_at DATETIME NOT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_psarce_event_key UNIQUE (event_key),
    INDEX idx_psarce_reviewer_time (reviewer_id, occurred_at, id),
    INDEX idx_psarce_attempt_time (attempt_id, occurred_at, id),
    CONSTRAINT fk_psarce_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id),
    CONSTRAINT fk_psarce_attempt FOREIGN KEY (attempt_id) REFERENCES practice_attempts(id),
    CONSTRAINT fk_psarce_question FOREIGN KEY (question_id) REFERENCES practice_questions(id),
    CONSTRAINT fk_psarce_media FOREIGN KEY (media_id) REFERENCES practice_speaking_media(id),
    CONSTRAINT fk_psarce_observation FOREIGN KEY (observation_key)
        REFERENCES practice_speaking_direct_audio_dark_observations(observation_key),
    CONSTRAINT chk_psarce_purpose CHECK (
        purpose_code = 'PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION'),
    CONSTRAINT chk_psarce_action CHECK (
        action_code IN ('INSPECTION_METADATA','PLAYBACK_OPEN')),
    CONSTRAINT chk_psarce_outcome CHECK (outcome_code = 'AUTHORIZED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
