CREATE TABLE question_explanation_artifacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fingerprint CHAR(64) NOT NULL,
    legacy_cache_id BIGINT NULL,
    skill VARCHAR(20) NOT NULL,
    question_type VARCHAR(40) NOT NULL,
    assessment_schema_version VARCHAR(40) NOT NULL,
    provider_model VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    response_schema_version VARCHAR(40) NOT NULL,
    explanation_language VARCHAR(16) NOT NULL,
    question_hash CHAR(64) NOT NULL,
    stimulus_hash CHAR(64) NOT NULL,
    answer_spec_hash CHAR(64) NOT NULL,
    media_bundle_hash CHAR(64) NOT NULL,
    input_contract_json JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    explanation_json JSON NULL,
    error_category VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    ready_at DATETIME NULL,
    failed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_qea_fingerprint UNIQUE (fingerprint),
    CONSTRAINT uk_qea_legacy_cache UNIQUE (legacy_cache_id),
    INDEX idx_qea_status_updated (status, updated_at),
    CONSTRAINT chk_qea_status CHECK (status IN ('PENDING', 'READY', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE question_version_explanation_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_version_id BIGINT NOT NULL,
    artifact_id BIGINT NOT NULL,
    explanation_language VARCHAR(16) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_qveb_question_language UNIQUE (question_version_id, explanation_language),
    INDEX idx_qveb_artifact (artifact_id),
    INDEX idx_qveb_fingerprint (fingerprint),
    CONSTRAINT fk_qveb_question_version
        FOREIGN KEY (question_version_id) REFERENCES practice_question_versions(id),
    CONSTRAINT fk_qveb_artifact
        FOREIGN KEY (artifact_id) REFERENCES question_explanation_artifacts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE question_explanation_generation_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    artifact_id BIGINT NOT NULL,
    source_question_version_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 4,
    next_attempt_at DATETIME NULL,
    lease_owner VARCHAR(100) NULL,
    lease_expires_at DATETIME NULL,
    error_category VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    manual_retry_count INT NOT NULL DEFAULT 0,
    last_retry_requested_by BIGINT NULL,
    last_retry_requested_at DATETIME NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_qegt_artifact UNIQUE (artifact_id),
    INDEX idx_qegt_due (status, next_attempt_at, id),
    INDEX idx_qegt_lease (status, lease_expires_at),
    CONSTRAINT fk_qegt_artifact
        FOREIGN KEY (artifact_id) REFERENCES question_explanation_artifacts(id),
    CONSTRAINT fk_qegt_source_question_version
        FOREIGN KEY (source_question_version_id) REFERENCES practice_question_versions(id),
    CONSTRAINT fk_qegt_retry_user
        FOREIGN KEY (last_retry_requested_by) REFERENCES users(id),
    CONSTRAINT chk_qegt_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO question_explanation_artifacts (
    fingerprint,
    legacy_cache_id,
    skill,
    question_type,
    assessment_schema_version,
    provider_model,
    prompt_version,
    response_schema_version,
    explanation_language,
    question_hash,
    stimulus_hash,
    answer_spec_hash,
    media_bundle_hash,
    input_contract_json,
    status,
    explanation_json,
    error_category,
    last_error_message,
    ready_at,
    failed_at,
    created_at,
    updated_at
)
SELECT
    qec.cache_key,
    qec.id,
    qec.skill_type,
    qec.question_type,
    'legacy-assessment-contract',
    qec.ai_model,
    qec.prompt_version,
    qec.schema_version,
    qec.explanation_language,
    qec.question_hash,
    COALESCE(qec.stimulus_hash, SHA2('', 256)),
    COALESCE(qec.answer_spec_hash, SHA2(COALESCE(qec.correct_answer, ''), 256)),
    SHA2('', 256),
    JSON_OBJECT(
        'source', 'question_explanation_cache',
        'legacyCacheId', qec.id,
        'boundQuestionVersionId', qec.question_version_id
    ),
    CASE
        WHEN qec.legacy_explanation_ready = 1 THEN 'READY'
        ELSE 'FAILED'
    END,
    CASE
        WHEN qec.legacy_explanation_ready = 1 THEN qec.explanation_json
        ELSE NULL
    END,
    CASE
        WHEN qec.legacy_explanation_ready = 1 THEN NULL
        ELSE 'LEGACY_EXPLANATION_INVALID'
    END,
    CASE
        WHEN qec.legacy_explanation_ready = 1 THEN NULL
        ELSE 'Legacy explanation is not a valid explanation JSON object.'
    END,
    CASE
        WHEN qec.legacy_explanation_ready = 1
            THEN COALESCE(qec.updated_at, qec.created_at, CURRENT_TIMESTAMP)
        ELSE NULL
    END,
    CASE
        WHEN qec.legacy_explanation_ready = 1 THEN NULL
        ELSE COALESCE(qec.updated_at, qec.created_at, CURRENT_TIMESTAMP)
    END,
    COALESCE(qec.created_at, CURRENT_TIMESTAMP),
    COALESCE(qec.updated_at, qec.created_at, CURRENT_TIMESTAMP)
FROM (
    SELECT validated.*,
        CASE
            WHEN JSON_TYPE(validated.safe_explanation_json) = 'OBJECT'
                 AND JSON_TYPE(JSON_EXTRACT(
                     validated.safe_explanation_json, '$.meaningVi')) = 'STRING'
                 AND NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
                     validated.safe_explanation_json, '$.meaningVi'))), '') IS NOT NULL
                 AND JSON_TYPE(JSON_EXTRACT(
                     validated.safe_explanation_json, '$.evidenceQuote')) = 'STRING'
                 AND NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
                     validated.safe_explanation_json, '$.evidenceQuote'))), '') IS NOT NULL
                 AND JSON_TYPE(JSON_EXTRACT(
                     validated.safe_explanation_json, '$.correctReasonVi')) = 'STRING'
                 AND NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
                     validated.safe_explanation_json, '$.correctReasonVi'))), '') IS NOT NULL
                 AND JSON_TYPE(JSON_EXTRACT(
                     validated.safe_explanation_json, '$.relatedTranslationVi')) = 'STRING'
                 AND JSON_TYPE(JSON_EXTRACT(
                     validated.safe_explanation_json, '$.eliminatedOptions')) = 'ARRAY'
                THEN 1
            ELSE 0
        END AS legacy_explanation_ready
    FROM (
        SELECT legacy.*,
            IF(JSON_VALID(legacy.explanation_json),
                legacy.explanation_json, JSON_OBJECT()) AS safe_explanation_json
        FROM question_explanation_cache legacy
    ) validated
) qec;

INSERT INTO question_version_explanation_bindings (
    question_version_id,
    artifact_id,
    explanation_language,
    fingerprint,
    bound_at
)
SELECT
    qec.question_version_id,
    qea.id,
    qec.explanation_language,
    qec.cache_key,
    COALESCE(qec.updated_at, qec.created_at, CURRENT_TIMESTAMP)
FROM question_explanation_cache qec
JOIN (
    SELECT question_version_id, explanation_language, MAX(id) AS newest_cache_id
    FROM question_explanation_cache
    WHERE question_version_id IS NOT NULL
    GROUP BY question_version_id, explanation_language
) newest ON newest.newest_cache_id = qec.id
JOIN question_explanation_artifacts qea ON qea.legacy_cache_id = qec.id;

DROP TABLE question_explanation_cache;
