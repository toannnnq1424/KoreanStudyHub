CREATE TABLE practice_ai_provider_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    provider_family VARCHAR(40) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    credential_secret VARCHAR(4096) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_practice_ai_profile_code UNIQUE (profile_code),
    CONSTRAINT fk_practice_ai_profile_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT chk_practice_ai_profile_code
        CHECK (profile_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT chk_practice_ai_provider_family
        CHECK (provider_family IN ('OPENAI_COMPATIBLE')),
    CONSTRAINT chk_practice_ai_profile_revision CHECK (revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_ai_purpose_bindings (
    purpose_code VARCHAR(64) PRIMARY KEY,
    provider_profile_id BIGINT NOT NULL,
    model VARCHAR(150) NOT NULL,
    transport_dialect VARCHAR(64) NOT NULL,
    capability_json JSON NOT NULL,
    limits_json JSON NOT NULL,
    retention_code VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_practice_ai_binding_profile (provider_profile_id),
    CONSTRAINT fk_practice_ai_binding_profile
        FOREIGN KEY (provider_profile_id) REFERENCES practice_ai_provider_profiles(id),
    CONSTRAINT fk_practice_ai_binding_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT chk_practice_ai_binding_purpose CHECK (purpose_code IN (
        'PRACTICE_PDF_AUTHORING',
        'PRACTICE_RL_EXPLANATION',
        'PRACTICE_WRITING_EVALUATION',
        'PRACTICE_SPEAKING_EVALUATION',
        'PRACTICE_SPEAKING_STT',
        'PRACTICE_SPEAKING_TTS'
    )),
    CONSTRAINT chk_practice_ai_transport_dialect
        CHECK (transport_dialect = 'OPENAI_COMPATIBLE_V1'),
    CONSTRAINT chk_practice_ai_retention_code
        CHECK (retention_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT chk_practice_ai_binding_revision CHECK (revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_ai_capability_test_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    purpose_code VARCHAR(64) NOT NULL,
    binding_revision BIGINT NOT NULL,
    required_capability VARCHAR(255) NOT NULL,
    status VARCHAR(16) NULL,
    duration_ms BIGINT NULL,
    bounded_error_code VARCHAR(64) NULL,
    tested_by BIGINT NOT NULL,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    INDEX idx_practice_ai_capability_test_purpose
        (purpose_code, started_at),
    CONSTRAINT fk_practice_ai_capability_test_binding
        FOREIGN KEY (purpose_code) REFERENCES practice_ai_purpose_bindings(purpose_code),
    CONSTRAINT fk_practice_ai_capability_test_user
        FOREIGN KEY (tested_by) REFERENCES users(id),
    CONSTRAINT chk_practice_ai_capability_test_status
        CHECK (status IS NULL OR status IN ('PASS', 'FAIL', 'CANCELLED')),
    CONSTRAINT chk_practice_ai_capability_test_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_ai_execution_audits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    purpose_code VARCHAR(64) NOT NULL,
    binding_revision BIGINT NOT NULL,
    provider_profile_revision BIGINT NOT NULL,
    provider_family VARCHAR(40) NOT NULL,
    provider_profile_code VARCHAR(64) NOT NULL,
    model VARCHAR(150) NOT NULL,
    transport_dialect VARCHAR(64) NOT NULL,
    capability_digest CHAR(64) NOT NULL,
    limits_digest CHAR(64) NOT NULL,
    retention_code VARCHAR(64) NOT NULL,
    operation_code VARCHAR(80) NOT NULL,
    contract_identity_digest CHAR(64) NOT NULL,
    data_class VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    bounded_error_code VARCHAR(64) NULL,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    INDEX idx_practice_ai_execution_purpose (purpose_code, started_at),
    INDEX idx_practice_ai_execution_binding (purpose_code, binding_revision),
    CONSTRAINT chk_practice_ai_execution_purpose CHECK (purpose_code IN (
        'PRACTICE_PDF_AUTHORING',
        'PRACTICE_RL_EXPLANATION',
        'PRACTICE_WRITING_EVALUATION',
        'PRACTICE_SPEAKING_EVALUATION',
        'PRACTICE_SPEAKING_STT',
        'PRACTICE_SPEAKING_TTS'
    )),
    CONSTRAINT chk_practice_ai_execution_status
        CHECK (status IN ('RESOLVED', 'SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_practice_ai_execution_revisions
        CHECK (binding_revision >= 0 AND provider_profile_revision >= 0),
    CONSTRAINT chk_practice_ai_execution_capability_digest
        CHECK (capability_digest REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_practice_ai_execution_limits_digest
        CHECK (limits_digest REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_practice_ai_execution_contract_digest
        CHECK (contract_identity_digest REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
