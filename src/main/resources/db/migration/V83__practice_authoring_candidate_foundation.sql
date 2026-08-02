-- AIM-2: persistent, reviewable Practice authoring candidates and replay-safe
-- atomic apply ledger. Importers and providers never write PracticeDraft here.

CREATE TABLE practice_authoring_candidates (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id BIGINT NOT NULL,
    source_kind VARCHAR(32) NOT NULL,
    source_contract_version VARCHAR(64) NOT NULL,
    source_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_revision VARCHAR(100) NOT NULL,
    source_name VARCHAR(255) NULL,
    source_operation VARCHAR(12) NOT NULL DEFAULT 'NONE',
    target_draft_id BIGINT NOT NULL,
    target_test_no INT NOT NULL,
    target_skill VARCHAR(16) NOT NULL,
    target_lesson_code VARCHAR(32) NOT NULL,
    base_draft_version INT NOT NULL,
    state VARCHAR(24) NOT NULL,
    normalizer_version VARCHAR(64) NOT NULL,
    validator_version VARCHAR(64) NOT NULL,
    candidate_json LONGTEXT NOT NULL,
    content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    warning_acknowledged_at DATETIME(6) NULL,
    warning_acknowledged_by BIGINT NULL,
    expires_at DATETIME(6) NOT NULL,
    applied_at DATETIME(6) NULL,
    applied_draft_version INT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_practice_authoring_candidate_idempotency (
        owner_id,
        source_kind,
        source_contract_version,
        source_digest,
        source_revision,
        source_operation,
        target_draft_id,
        target_test_no,
        target_skill,
        target_lesson_code,
        base_draft_version,
        normalizer_version
    ),
    KEY idx_practice_authoring_candidate_owner_state_expiry
        (owner_id, state, expires_at),
    KEY idx_practice_authoring_candidate_target
        (target_draft_id, base_draft_version),
    CONSTRAINT fk_practice_authoring_candidate_owner
        FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_practice_authoring_candidate_target
        FOREIGN KEY (target_draft_id) REFERENCES practice_drafts(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_practice_authoring_candidate_warning_actor
        FOREIGN KEY (warning_acknowledged_by) REFERENCES users(id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_practice_authoring_candidate_source_kind CHECK (
        source_kind IN (
            'QUICK_EXCEL', 'ADVANCED_EXCEL_V2',
            'LEGACY_EXCEL_V1', 'PDF_AI'
        )
    ),
    CONSTRAINT chk_practice_authoring_candidate_operation CHECK (
        source_operation IN ('NONE', 'EXTRACT', 'GENERATE')
    ),
    CONSTRAINT chk_practice_authoring_candidate_skill CHECK (
        target_skill IN ('READING', 'LISTENING', 'WRITING', 'SPEAKING')
    ),
    CONSTRAINT chk_practice_authoring_candidate_state CHECK (
        state IN (
            'PARSED', 'NORMALIZED', 'VALIDATED', 'REVIEWING',
            'READY_TO_APPLY', 'APPLIED', 'FAILED', 'REJECTED', 'EXPIRED'
        )
    ),
    CONSTRAINT chk_practice_authoring_candidate_digest CHECK (
        source_digest REGEXP '^[0-9a-f]{64}$'
        AND content_digest REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_practice_authoring_candidate_target CHECK (
        target_test_no > 0
        AND base_draft_version >= 0
        AND target_lesson_code REGEXP '^[RLSW][1-9][0-9]*$'
    ),
    CONSTRAINT chk_practice_authoring_candidate_expiry CHECK (
        expires_at >= TIMESTAMPADD(DAY, 7, created_at)
    ),
    CONSTRAINT chk_practice_authoring_candidate_applied CHECK (
        (state = 'APPLIED'
            AND applied_at IS NOT NULL
            AND applied_draft_version IS NOT NULL)
        OR
        (state <> 'APPLIED'
            AND applied_at IS NULL
            AND applied_draft_version IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_authoring_candidate_apply_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    candidate_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    apply_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_version BIGINT NOT NULL,
    candidate_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_draft_version INT NOT NULL,
    result VARCHAR(24) NOT NULL,
    result_code VARCHAR(100) NOT NULL,
    result_draft_version INT NULL,
    actor_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_practice_authoring_candidate_apply_request
        (candidate_id, apply_request_id),
    KEY idx_practice_authoring_candidate_apply_actor
        (actor_id, created_at),
    CONSTRAINT fk_practice_authoring_candidate_apply_candidate
        FOREIGN KEY (candidate_id) REFERENCES practice_authoring_candidates(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_practice_authoring_candidate_apply_actor
        FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_practice_authoring_candidate_apply_result CHECK (
        result IN ('DRAFT_APPLIED', 'CONFLICT', 'REJECTED')
    ),
    CONSTRAINT chk_practice_authoring_candidate_apply_digest CHECK (
        candidate_digest REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_practice_authoring_candidate_apply_version CHECK (
        candidate_version >= 0 AND base_draft_version >= 0
    ),
    CONSTRAINT chk_practice_authoring_candidate_apply_draft CHECK (
        (result = 'DRAFT_APPLIED' AND result_draft_version IS NOT NULL)
        OR
        (result <> 'DRAFT_APPLIED' AND result_draft_version IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
