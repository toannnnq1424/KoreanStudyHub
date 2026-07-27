-- Phase 13C3 lecturer Speaking prompt-authoring foundation.
-- Forward-only and additive: published question-content-v1 rows and attempts
-- are deliberately not rewritten.
-- 13C3-01 static entity mapping reopened this still-unexecuted migration only
-- where the accepted shape could not represent two locked invariants:
-- (1) a queued TTS source must bind its exact artifact before output exists,
-- while the ready output remains artifact-bound; and
-- (2) lecturer transcript correction/confirmation is source-specific, so a
-- source selects an immutable revision without rewriting a reusable artifact.
-- The accepted owner/operation plus exact STT input-audio-asset composites for
-- both mutable sources and immutable version contexts remain restored intact.
-- 13C3-04 reopens this still-unexecuted file only for two proven lifecycle
-- blockers: task creator source identity must be detachable before an exact
-- source delete while retaining its composite owner FK, and the applied V34
-- asset-cleanup table needs a durable worker claim token plus bounded exact
-- storage-key indexes so cleanup claims and new asset registration serialize.

-- V34 is applied and immutable. V45 is unexecuted and adds the durable claim
-- identity and lock access paths required before a lifecycle worker performs
-- storage I/O.
ALTER TABLE practice_asset_lifecycle_tasks
    ADD COLUMN claim_token VARCHAR(64) NULL AFTER next_attempt_at,
    ADD INDEX idx_practice_asset_task_source_active
        (source_storage_key, status, id);

ALTER TABLE lecturer_assets
    ADD CONSTRAINT uk_lecturer_asset_owner_identity
        UNIQUE (id, owner_lecturer_id),
    ADD INDEX idx_lecturer_assets_storage_key (storage_key, id);

ALTER TABLE practice_drafts
    ADD CONSTRAINT uk_practice_draft_owner_identity
        UNIQUE (id, owner_id);

CREATE TABLE practice_speaking_prompt_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    question_client_id VARCHAR(100) NOT NULL,
    owner_lecturer_id BIGINT NOT NULL,
    input_type VARCHAR(32) NOT NULL,
    tts_enabled TINYINT(1) NOT NULL DEFAULT 0,
    manual_text_sha256 CHAR(64) NULL,
    original_audio_asset_id BIGINT NULL,
    generated_audio_asset_id BIGINT NULL,
    active_audio_asset_id BIGINT NULL,
    current_stt_artifact_id BIGINT NULL,
    current_tts_artifact_id BIGINT NULL,
    current_transcript_revision_id BIGINT NULL,
    current_stt_operation VARCHAR(16) NOT NULL DEFAULT 'stt',
    current_tts_operation VARCHAR(16) NOT NULL DEFAULT 'tts',
    transcript_status VARCHAR(32) NOT NULL DEFAULT 'idle',
    audio_sync_status VARCHAR(32) NOT NULL DEFAULT 'idle',
    lecturer_transcript_confirmed_at DATETIME NULL,
    source_revision BIGINT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_speaking_prompt_source_draft_question
        UNIQUE (draft_id, question_client_id),
    CONSTRAINT uk_speaking_prompt_source_owner_identity
        UNIQUE (id, owner_lecturer_id),
    INDEX idx_speaking_prompt_source_owner (owner_lecturer_id, draft_id),
    INDEX idx_speaking_prompt_source_original_asset (original_audio_asset_id),
    INDEX idx_speaking_prompt_source_generated_asset (generated_audio_asset_id),
    INDEX idx_speaking_prompt_source_active_asset (active_audio_asset_id),
    CONSTRAINT fk_speaking_prompt_source_draft
        FOREIGN KEY (draft_id, owner_lecturer_id)
        REFERENCES practice_drafts(id, owner_id),
    CONSTRAINT fk_speaking_prompt_source_owner
        FOREIGN KEY (owner_lecturer_id) REFERENCES users(id),
    CONSTRAINT fk_speaking_prompt_source_original_asset
        FOREIGN KEY (original_audio_asset_id, owner_lecturer_id)
        REFERENCES lecturer_assets(id, owner_lecturer_id),
    CONSTRAINT fk_speaking_prompt_source_generated_asset
        FOREIGN KEY (generated_audio_asset_id, owner_lecturer_id)
        REFERENCES lecturer_assets(id, owner_lecturer_id),
    CONSTRAINT fk_speaking_prompt_source_active_asset
        FOREIGN KEY (active_audio_asset_id, owner_lecturer_id)
        REFERENCES lecturer_assets(id, owner_lecturer_id),
    CONSTRAINT fk_speaking_prompt_source_created_by
        FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_speaking_prompt_source_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT chk_speaking_prompt_source_input
        CHECK (input_type IN ('audio_upload', 'manual_text')),
    CONSTRAINT chk_speaking_prompt_source_tts_enabled
        CHECK (tts_enabled IN (0, 1)),
    CONSTRAINT chk_speaking_prompt_source_text_identity CHECK (
        (input_type = 'audio_upload'
            AND tts_enabled = 0
            AND (manual_text_sha256 IS NULL
                OR manual_text_sha256 REGEXP '^[0-9a-f]{64}$'))
        OR
        (input_type = 'manual_text'
            AND manual_text_sha256 IS NOT NULL
            AND manual_text_sha256 REGEXP '^[0-9a-f]{64}$')
    ),
    CONSTRAINT chk_speaking_prompt_source_artifact_operations
        CHECK (current_stt_operation = 'stt' AND current_tts_operation = 'tts'),
    CONSTRAINT chk_speaking_prompt_source_current_artifact_assets CHECK (
        (current_stt_artifact_id IS NULL OR original_audio_asset_id IS NOT NULL)
        AND
        (current_transcript_revision_id IS NULL
            OR current_stt_artifact_id IS NOT NULL)
    ),
    CONSTRAINT chk_speaking_prompt_source_mode_assets CHECK (
        (input_type = 'audio_upload'
            AND (active_audio_asset_id IS NULL
                OR (original_audio_asset_id IS NOT NULL
                    AND active_audio_asset_id = original_audio_asset_id)))
        OR
        (input_type = 'manual_text'
            AND (
                (tts_enabled = 0
                    AND active_audio_asset_id IS NULL)
                OR
                (tts_enabled = 1
                    AND (active_audio_asset_id IS NULL
                        OR (generated_audio_asset_id IS NOT NULL
                            AND active_audio_asset_id = generated_audio_asset_id)))
            ))
    ),
    CONSTRAINT chk_speaking_prompt_source_revision CHECK (source_revision >= 0),
    CONSTRAINT chk_speaking_prompt_source_lock CHECK (lock_version >= 0),
    CONSTRAINT chk_speaking_prompt_source_transcript_status CHECK (
        transcript_status IN (
            'idle', 'queued', 'processing', 'ready', 'needs_review', 'stale',
            'failed_retryable', 'failed_final', 'superseded', 'cancelled')
    ),
    CONSTRAINT chk_speaking_prompt_source_audio_status CHECK (
        audio_sync_status IN (
            'idle', 'queued', 'processing', 'ready', 'needs_review', 'stale',
            'failed_retryable', 'failed_final', 'superseded', 'cancelled')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_speaking_prompt_ai_artifacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_lecturer_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    operation_fingerprint CHAR(64) NOT NULL,
    input_source_revision BIGINT NOT NULL,
    input_sha256 CHAR(64) NOT NULL,
    input_audio_asset_id BIGINT NULL,
    provider_code VARCHAR(64) NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    language_tag VARCHAR(32) NOT NULL,
    voice_code VARCHAR(128) NULL,
    speed DECIMAL(5,2) NULL,
    output_format VARCHAR(32) NULL,
    contract_version VARCHAR(64) NOT NULL,
    purpose_code VARCHAR(64) NOT NULL,
    retention_code VARCHAR(64) NOT NULL,
    provider_request_reference VARCHAR(255) NULL,
    provider_transcript_text MEDIUMTEXT NULL,
    current_context_text MEDIUMTEXT NULL,
    current_context_sha256 CHAR(64) NULL,
    generated_audio_asset_id BIGINT NULL,
    confidence DECIMAL(6,5) NULL,
    artifact_status VARCHAR(32) NOT NULL,
    public_error_category VARCHAR(64) NULL,
    ready_at DATETIME NULL,
    failed_at DATETIME NULL,
    superseded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_speaking_prompt_artifact_owner_fingerprint
        UNIQUE (owner_lecturer_id, operation, operation_fingerprint),
    CONSTRAINT uk_speaking_prompt_artifact_owner_operation_identity
        UNIQUE (id, owner_lecturer_id, operation),
    CONSTRAINT uk_speaking_prompt_artifact_task_identity
        UNIQUE (id, owner_lecturer_id, operation, operation_fingerprint),
    CONSTRAINT uk_speaking_prompt_artifact_input_asset_identity
        UNIQUE (id, owner_lecturer_id, operation, input_audio_asset_id),
    CONSTRAINT uk_speaking_prompt_artifact_output_asset_identity
        UNIQUE (id, owner_lecturer_id, operation, generated_audio_asset_id),
    INDEX idx_speaking_prompt_artifact_status
        (operation, artifact_status, updated_at),
    INDEX idx_speaking_prompt_artifact_input_asset (input_audio_asset_id),
    INDEX idx_speaking_prompt_artifact_generated_asset (generated_audio_asset_id),
    CONSTRAINT fk_speaking_prompt_artifact_owner
        FOREIGN KEY (owner_lecturer_id) REFERENCES users(id),
    CONSTRAINT fk_speaking_prompt_artifact_input_asset
        FOREIGN KEY (input_audio_asset_id, owner_lecturer_id)
        REFERENCES lecturer_assets(id, owner_lecturer_id),
    CONSTRAINT fk_speaking_prompt_artifact_generated_asset
        FOREIGN KEY (generated_audio_asset_id, owner_lecturer_id)
        REFERENCES lecturer_assets(id, owner_lecturer_id),
    CONSTRAINT chk_speaking_prompt_artifact_operation
        CHECK (operation IN ('stt', 'tts')),
    CONSTRAINT chk_speaking_prompt_artifact_revision CHECK (input_source_revision >= 0),
    CONSTRAINT chk_speaking_prompt_artifact_fingerprint
        CHECK (operation_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_speaking_prompt_artifact_input_sha
        CHECK (input_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_speaking_prompt_artifact_context_sha
        CHECK (current_context_sha256 IS NULL
            OR current_context_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_speaking_prompt_artifact_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT chk_speaking_prompt_artifact_status CHECK (
        artifact_status IN (
            'idle', 'queued', 'processing', 'ready', 'needs_review', 'stale',
            'failed_retryable', 'failed_final', 'superseded', 'cancelled')
    ),
    CONSTRAINT chk_speaking_prompt_artifact_shape CHECK (
        (operation = 'stt'
            AND input_audio_asset_id IS NOT NULL
            AND generated_audio_asset_id IS NULL
            AND voice_code IS NULL
            AND speed IS NULL
            AND output_format IS NULL)
        OR
        (operation = 'tts'
            AND input_audio_asset_id IS NULL
            AND provider_transcript_text IS NULL
            AND current_context_text IS NULL
            AND current_context_sha256 IS NULL
            AND confidence IS NULL
            AND voice_code IS NOT NULL
            AND speed IS NOT NULL
            AND speed BETWEEN 0.25 AND 4.00
            AND output_format IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE practice_speaking_prompt_sources
    ADD INDEX idx_speaking_prompt_source_stt_artifact (current_stt_artifact_id),
    ADD INDEX idx_speaking_prompt_source_tts_artifact (current_tts_artifact_id),
    ADD CONSTRAINT fk_speaking_prompt_source_stt_artifact
        FOREIGN KEY (
            current_stt_artifact_id,
            owner_lecturer_id,
            current_stt_operation,
            original_audio_asset_id)
        REFERENCES practice_speaking_prompt_ai_artifacts(
            id,
            owner_lecturer_id,
            operation,
            input_audio_asset_id),
    ADD CONSTRAINT fk_speaking_prompt_source_tts_artifact_identity
        FOREIGN KEY (
            current_tts_artifact_id,
            owner_lecturer_id,
            current_tts_operation)
        REFERENCES practice_speaking_prompt_ai_artifacts(
            id,
            owner_lecturer_id,
            operation),
    ADD CONSTRAINT fk_speaking_prompt_source_tts_artifact_output
        FOREIGN KEY (
            current_tts_artifact_id,
            owner_lecturer_id,
            current_tts_operation,
            generated_audio_asset_id)
        REFERENCES practice_speaking_prompt_ai_artifacts(
            id,
            owner_lecturer_id,
            operation,
            generated_audio_asset_id);

CREATE TABLE practice_speaking_prompt_transcript_revisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    artifact_id BIGINT NOT NULL,
    owner_lecturer_id BIGINT NOT NULL,
    artifact_operation VARCHAR(16) NOT NULL DEFAULT 'stt',
    revision_number INT NOT NULL,
    revision_source VARCHAR(32) NOT NULL,
    context_text MEDIUMTEXT NOT NULL,
    context_sha256 CHAR(64) NOT NULL,
    edited_by BIGINT NULL,
    confirmed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_speaking_prompt_transcript_revision
        UNIQUE (artifact_id, revision_number),
    CONSTRAINT uk_speaking_prompt_transcript_source_identity
        UNIQUE (id, artifact_id, owner_lecturer_id),
    INDEX idx_speaking_prompt_transcript_editor (edited_by, created_at),
    CONSTRAINT fk_speaking_prompt_transcript_artifact
        FOREIGN KEY (artifact_id, owner_lecturer_id, artifact_operation)
        REFERENCES practice_speaking_prompt_ai_artifacts(
            id,
            owner_lecturer_id,
            operation),
    CONSTRAINT fk_speaking_prompt_transcript_owner
        FOREIGN KEY (owner_lecturer_id) REFERENCES users(id),
    CONSTRAINT fk_speaking_prompt_transcript_editor
        FOREIGN KEY (edited_by) REFERENCES users(id),
    CONSTRAINT chk_speaking_prompt_transcript_revision_number
        CHECK (revision_number >= 1),
    CONSTRAINT chk_speaking_prompt_transcript_operation
        CHECK (artifact_operation = 'stt'),
    CONSTRAINT chk_speaking_prompt_transcript_sha
        CHECK (context_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_speaking_prompt_transcript_source CHECK (
        (revision_source = 'provider' AND edited_by IS NULL)
        OR
        (revision_source = 'lecturer_edit' AND edited_by IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE practice_speaking_prompt_sources
    ADD INDEX idx_speaking_prompt_source_transcript_revision
        (current_transcript_revision_id),
    ADD CONSTRAINT fk_speaking_prompt_source_transcript_revision
        FOREIGN KEY (
            current_transcript_revision_id,
            current_stt_artifact_id,
            owner_lecturer_id)
        REFERENCES practice_speaking_prompt_transcript_revisions(
            id,
            artifact_id,
            owner_lecturer_id);

CREATE TABLE practice_speaking_prompt_ai_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    artifact_id BIGINT NOT NULL,
    -- Historical creator identity may be detached when its draft/question is
    -- deleted. Owner/artifact/fingerprint identity remains immutable and a
    -- shared task continues routing through current artifact attachments.
    source_id BIGINT NULL,
    owner_lecturer_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    source_input_type VARCHAR(32) NOT NULL,
    operation_fingerprint CHAR(64) NOT NULL,
    expected_source_revision BIGINT NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 4,
    next_attempt_at DATETIME NULL,
    lease_owner VARCHAR(100) NULL,
    lease_expires_at DATETIME NULL,
    retryable TINYINT(1) NOT NULL DEFAULT 0,
    public_error_category VARCHAR(64) NULL,
    requested_by BIGINT NOT NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active_fingerprint_key VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE
                WHEN task_status IN ('queued', 'processing', 'retry_wait')
                THEN CONCAT(owner_lecturer_id, ':', operation, ':', operation_fingerprint)
                ELSE NULL
            END
        ) STORED,
    CONSTRAINT uk_speaking_prompt_task_active_fingerprint
        UNIQUE (active_fingerprint_key),
    INDEX idx_speaking_prompt_task_due (task_status, next_attempt_at, id),
    INDEX idx_speaking_prompt_task_lease (task_status, lease_expires_at),
    INDEX idx_speaking_prompt_task_source_revision
        (source_id, expected_source_revision),
    INDEX idx_speaking_prompt_task_artifact (artifact_id),
    CONSTRAINT fk_speaking_prompt_task_artifact
        FOREIGN KEY (
            artifact_id,
            owner_lecturer_id,
            operation,
            operation_fingerprint)
        REFERENCES practice_speaking_prompt_ai_artifacts(
            id,
            owner_lecturer_id,
            operation,
            operation_fingerprint),
    CONSTRAINT fk_speaking_prompt_task_source
        FOREIGN KEY (source_id, owner_lecturer_id)
        REFERENCES practice_speaking_prompt_sources(
            id,
            owner_lecturer_id),
    CONSTRAINT fk_speaking_prompt_task_owner
        FOREIGN KEY (owner_lecturer_id) REFERENCES users(id),
    CONSTRAINT fk_speaking_prompt_task_requested_by
        FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT chk_speaking_prompt_task_operation
        CHECK (operation IN ('stt', 'tts')),
    CONSTRAINT chk_speaking_prompt_task_source_operation CHECK (
        (source_input_type = 'audio_upload' AND operation = 'stt')
        OR
        (source_input_type = 'manual_text' AND operation = 'tts')
    ),
    CONSTRAINT chk_speaking_prompt_task_fingerprint
        CHECK (operation_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_speaking_prompt_task_revision CHECK (expected_source_revision >= 0),
    CONSTRAINT chk_speaking_prompt_task_attempts
        CHECK (attempt_count >= 0
            AND max_attempts BETWEEN 1 AND 10
            AND attempt_count <= max_attempts),
    CONSTRAINT chk_speaking_prompt_task_retryable
        CHECK (retryable IN (0, 1)),
    CONSTRAINT chk_speaking_prompt_task_status CHECK (
        task_status IN (
            'queued', 'processing', 'retry_wait', 'succeeded', 'failed',
            'superseded', 'cancelled')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_speaking_prompt_version_contexts (
    question_version_id BIGINT PRIMARY KEY,
    owner_lecturer_id BIGINT NOT NULL,
    input_type VARCHAR(32) NOT NULL,
    delivery_mode VARCHAR(32) NOT NULL,
    audio_origin VARCHAR(32) NOT NULL,
    prompt_context_source VARCHAR(32) NOT NULL,
    prompt_context_text MEDIUMTEXT NOT NULL,
    prompt_context_sha256 CHAR(64) NOT NULL,
    prompt_context_fingerprint CHAR(64) NOT NULL,
    original_audio_asset_id BIGINT NULL,
    active_audio_asset_id BIGINT NULL,
    stt_artifact_id BIGINT NULL,
    tts_artifact_id BIGINT NULL,
    stt_operation VARCHAR(16) NOT NULL DEFAULT 'stt',
    tts_operation VARCHAR(16) NOT NULL DEFAULT 'tts',
    stt_provider_code VARCHAR(64) NULL,
    stt_model_code VARCHAR(128) NULL,
    stt_contract_version VARCHAR(64) NULL,
    stt_purpose_code VARCHAR(64) NULL,
    stt_retention_code VARCHAR(64) NULL,
    tts_provider_code VARCHAR(64) NULL,
    tts_model_code VARCHAR(128) NULL,
    tts_contract_version VARCHAR(64) NULL,
    tts_purpose_code VARCHAR(64) NULL,
    tts_retention_code VARCHAR(64) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_speaking_prompt_context_fingerprint (prompt_context_fingerprint),
    INDEX idx_speaking_prompt_context_original_asset (original_audio_asset_id),
    INDEX idx_speaking_prompt_context_active_asset (active_audio_asset_id),
    INDEX idx_speaking_prompt_context_stt_artifact (stt_artifact_id),
    INDEX idx_speaking_prompt_context_tts_artifact (tts_artifact_id),
    CONSTRAINT fk_speaking_prompt_context_question_version
        FOREIGN KEY (question_version_id) REFERENCES practice_question_versions(id),
    CONSTRAINT fk_speaking_prompt_context_owner
        FOREIGN KEY (owner_lecturer_id) REFERENCES users(id),
    CONSTRAINT fk_speaking_prompt_context_original_asset
        FOREIGN KEY (original_audio_asset_id, owner_lecturer_id)
        REFERENCES lecturer_assets(id, owner_lecturer_id),
    CONSTRAINT fk_speaking_prompt_context_active_asset
        FOREIGN KEY (active_audio_asset_id, owner_lecturer_id)
        REFERENCES lecturer_assets(id, owner_lecturer_id),
    CONSTRAINT fk_speaking_prompt_context_stt_artifact
        FOREIGN KEY (
            stt_artifact_id,
            owner_lecturer_id,
            stt_operation,
            original_audio_asset_id)
        REFERENCES practice_speaking_prompt_ai_artifacts(
            id,
            owner_lecturer_id,
            operation,
            input_audio_asset_id),
    CONSTRAINT fk_speaking_prompt_context_tts_artifact
        FOREIGN KEY (
            tts_artifact_id,
            owner_lecturer_id,
            tts_operation,
            active_audio_asset_id)
        REFERENCES practice_speaking_prompt_ai_artifacts(
            id,
            owner_lecturer_id,
            operation,
            generated_audio_asset_id),
    CONSTRAINT fk_speaking_prompt_context_created_by
        FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_speaking_prompt_context_sha
        CHECK (prompt_context_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_speaking_prompt_context_fingerprint
        CHECK (prompt_context_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_speaking_prompt_context_artifact_operations
        CHECK (stt_operation = 'stt' AND tts_operation = 'tts'),
    CONSTRAINT chk_speaking_prompt_context_shape CHECK (
        (input_type = 'audio_upload'
            AND delivery_mode = 'audio_only'
            AND audio_origin = 'teacher_upload'
            AND prompt_context_source = 'stt_transcript'
            AND original_audio_asset_id IS NOT NULL
            AND active_audio_asset_id IS NOT NULL
            AND active_audio_asset_id = original_audio_asset_id
            AND stt_artifact_id IS NOT NULL
            AND tts_artifact_id IS NULL
            AND stt_provider_code IS NOT NULL
            AND stt_model_code IS NOT NULL
            AND stt_contract_version IS NOT NULL
            AND stt_purpose_code IS NOT NULL
            AND stt_retention_code IS NOT NULL
            AND tts_provider_code IS NULL
            AND tts_model_code IS NULL
            AND tts_contract_version IS NULL
            AND tts_purpose_code IS NULL
            AND tts_retention_code IS NULL)
        OR
        (input_type = 'manual_text'
            AND delivery_mode = 'text_only'
            AND audio_origin = 'none'
            AND prompt_context_source = 'manual_text'
            AND original_audio_asset_id IS NULL
            AND active_audio_asset_id IS NULL
            AND stt_artifact_id IS NULL
            AND tts_artifact_id IS NULL
            AND stt_provider_code IS NULL
            AND stt_model_code IS NULL
            AND stt_contract_version IS NULL
            AND stt_purpose_code IS NULL
            AND stt_retention_code IS NULL
            AND tts_provider_code IS NULL
            AND tts_model_code IS NULL
            AND tts_contract_version IS NULL
            AND tts_purpose_code IS NULL
            AND tts_retention_code IS NULL)
        OR
        (input_type = 'manual_text'
            AND delivery_mode = 'text_and_audio'
            AND audio_origin = 'ai_tts'
            AND prompt_context_source = 'manual_text'
            AND original_audio_asset_id IS NULL
            AND active_audio_asset_id IS NOT NULL
            AND stt_artifact_id IS NULL
            AND tts_artifact_id IS NOT NULL
            AND stt_provider_code IS NULL
            AND stt_model_code IS NULL
            AND stt_contract_version IS NULL
            AND stt_purpose_code IS NULL
            AND stt_retention_code IS NULL
            AND tts_provider_code IS NOT NULL
            AND tts_model_code IS NOT NULL
            AND tts_contract_version IS NOT NULL
            AND tts_purpose_code IS NOT NULL
            AND tts_retention_code IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
