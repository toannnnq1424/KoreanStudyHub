-- Post-13H Practice integrity gate.
-- Forward-only attempt deadline/autosave evidence and durable subjective
-- evaluation jobs. Existing learner rows and immutable versions are retained.

ALTER TABLE practice_attempts
    ADD COLUMN deadline_at DATETIME NULL AFTER started_at,
    ADD COLUMN last_saved_at DATETIME NULL AFTER deadline_at,
    ADD COLUMN deadline_reconcile_attempts INT NOT NULL DEFAULT 0
        AFTER last_saved_at,
    ADD COLUMN deadline_reconcile_next_at DATETIME NULL
        AFTER deadline_reconcile_attempts,
    ADD COLUMN deadline_reconcile_error_code VARCHAR(100) NULL
        AFTER deadline_reconcile_next_at,
    ADD COLUMN deadline_reconcile_quarantined_at DATETIME NULL
        AFTER deadline_reconcile_error_code,
    DROP CHECK chk_pa_analysis,
    ADD CONSTRAINT chk_pa_analysis CHECK (
        analysis_status IN (
            'NOT_REQUESTED', 'QUEUED', 'PROCESSING',
            'SUCCEEDED', 'FAILED', 'UNAVAILABLE'
        )
    ),
    ADD CONSTRAINT chk_pa_deadline_reconcile_attempts CHECK (
        deadline_reconcile_attempts BETWEEN 0 AND 5
    ),
    ADD CONSTRAINT chk_pa_deadline_reconcile_quarantine CHECK (
        deadline_reconcile_quarantined_at IS NULL
        OR (
            deadline_reconcile_attempts = 5
            AND deadline_reconcile_next_at IS NULL
        )
    );

UPDATE practice_attempts a
JOIN practice_section_versions sv
  ON sv.id = a.section_version_id
 AND sv.section_id = a.section_id
SET a.deadline_at = TIMESTAMPADD(
        MINUTE,
        CASE
            WHEN sv.duration_minutes IS NULL OR sv.duration_minutes <= 0 THEN 40
            ELSE sv.duration_minutes
        END,
        a.started_at)
WHERE a.deadline_at IS NULL;

UPDATE practice_attempts
SET deadline_at = TIMESTAMPADD(MINUTE, 40, started_at)
WHERE deadline_at IS NULL;

ALTER TABLE practice_attempts
    MODIFY COLUMN deadline_at DATETIME NOT NULL;

CREATE INDEX idx_practice_attempts_user_resume_deadline
    ON practice_attempts (user_id, status, deadline_at, activity_at DESC, id DESC);

CREATE INDEX idx_practice_attempts_deadline_reconcile
    ON practice_attempts (
        status,
        deadline_reconcile_quarantined_at,
        deadline_reconcile_next_at,
        deadline_at,
        id
    );

CREATE TABLE practice_attempt_evaluation_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    operation VARCHAR(40) NOT NULL,
    target_question_id BIGINT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    evaluation_contract_identity VARCHAR(500) NOT NULL,
    job_status VARCHAR(24) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_attempt_at DATETIME NULL,
    lease_owner VARCHAR(100) NULL,
    lease_expires_at DATETIME NULL,
    expires_at DATETIME NOT NULL,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(100) NULL,
    last_error_message VARCHAR(500) NULL,
    requested_by BIGINT NOT NULL,
    manual_retry_count INT NOT NULL DEFAULT 0,
    last_retry_requested_at DATETIME NULL,
    result_json JSON NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_practice_attempt_evaluation_job_attempt (attempt_id),
    INDEX idx_practice_attempt_evaluation_job_due
        (job_status, next_attempt_at, lease_expires_at, id),
    CONSTRAINT fk_practice_attempt_evaluation_job_attempt
        FOREIGN KEY (attempt_id) REFERENCES practice_attempts(id) ON DELETE CASCADE,
    CONSTRAINT fk_practice_attempt_evaluation_job_requester
        FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT chk_practice_attempt_evaluation_job_operation CHECK (
        operation IN ('SUBMIT', 'FULL_REEVALUATE', 'QUESTION_REEVALUATE')
    ),
    CONSTRAINT chk_practice_attempt_evaluation_job_status CHECK (
        job_status IN (
            'QUEUED', 'PROCESSING', 'RETRY_WAIT',
            'SUCCEEDED', 'FAILED', 'UNAVAILABLE'
        )
    ),
    CONSTRAINT chk_practice_attempt_evaluation_job_attempts CHECK (
        attempt_count >= 0 AND max_attempts BETWEEN 1 AND 10
    ),
    CONSTRAINT chk_practice_attempt_evaluation_job_manual_retries CHECK (
        manual_retry_count BETWEEN 0 AND 2
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
