-- AIM-6: exact storage-profile control plane and additive Practice identities.
-- Existing nullable rows remain authoritative in their bounded legacy-local roots.

CREATE TABLE storage_profiles (
    profile_code VARCHAR(40) PRIMARY KEY,
    backend VARCHAR(16) NOT NULL,
    account_id VARCHAR(128) NULL,
    access_key_id VARCHAR(255) NULL,
    secret_access_key VARCHAR(4096) NULL,
    bucket VARCHAR(255) NULL,
    endpoint VARCHAR(512) NULL,
    region VARCHAR(64) NULL,
    key_prefix VARCHAR(255) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_storage_profile_code CHECK (profile_code IN (
        'GENERAL_UPLOADS', 'PRACTICE_AUTHORING', 'PRACTICE_SPEAKING'
    )),
    CONSTRAINT chk_storage_profile_backend CHECK (backend IN ('LOCAL', 'R2')),
    CONSTRAINT chk_storage_profile_revision CHECK (revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- LOCAL is explicitly the development/test bootstrap. Production resolution
-- independently rejects LOCAL unless the deployment opts into it.
INSERT INTO storage_profiles
    (profile_code, backend, key_prefix, enabled)
VALUES
    ('GENERAL_UPLOADS', 'LOCAL', 'general-uploads', 1),
    ('PRACTICE_AUTHORING', 'LOCAL', 'practice-authoring', 1),
    ('PRACTICE_SPEAKING', 'LOCAL', 'practice-speaking', 1);

ALTER TABLE lecturer_assets
    ADD COLUMN storage_profile_code VARCHAR(40) NULL AFTER storage_provider,
    ADD CONSTRAINT fk_lecturer_asset_storage_profile
        FOREIGN KEY (storage_profile_code) REFERENCES storage_profiles(profile_code),
    ADD INDEX idx_lecturer_asset_profile_key (storage_profile_code, storage_key);

ALTER TABLE practice_pdf_import_sessions
    ADD COLUMN storage_profile_code VARCHAR(40) NULL AFTER stored_pdf_path,
    ADD CONSTRAINT fk_pdf_session_storage_profile
        FOREIGN KEY (storage_profile_code) REFERENCES storage_profiles(profile_code),
    ADD INDEX idx_pdf_session_profile_path (storage_profile_code, stored_pdf_path);

ALTER TABLE practice_asset_lifecycle_tasks
    ADD COLUMN storage_profile_code VARCHAR(40) NULL AFTER asset_id,
    ADD CONSTRAINT fk_practice_asset_task_storage_profile
        FOREIGN KEY (storage_profile_code) REFERENCES storage_profiles(profile_code),
    ADD INDEX idx_practice_asset_task_profile_source
        (storage_profile_code, source_storage_key, status, id);

ALTER TABLE practice_speaking_media
    ADD COLUMN storage_profile_code VARCHAR(40) NULL AFTER storage_provider,
    ADD CONSTRAINT fk_psm_storage_profile
        FOREIGN KEY (storage_profile_code) REFERENCES storage_profiles(profile_code),
    ADD CONSTRAINT uk_psm_profile_storage UNIQUE (storage_profile_code, storage_key),
    DROP CHECK chk_psm_status,
    ADD CONSTRAINT chk_psm_status CHECK (status IN (
        'UNREFERENCED_TEMPORARY', 'READY', 'SUPERSEDED',
        'DELETION_PENDING', 'DELETED'
    ));

ALTER TABLE practice_speaking_media_cleanup_tasks
    ADD COLUMN media_id BIGINT NULL AFTER cleanup_reason,
    ADD COLUMN storage_profile_code VARCHAR(40) NULL AFTER storage_provider,
    ADD CONSTRAINT fk_psm_cleanup_media
        FOREIGN KEY (media_id) REFERENCES practice_speaking_media(id),
    ADD CONSTRAINT fk_psm_cleanup_storage_profile
        FOREIGN KEY (storage_profile_code) REFERENCES storage_profiles(profile_code),
    ADD INDEX idx_psm_cleanup_media (media_id, status),
    ADD CONSTRAINT uk_psm_cleanup_profile_storage
        UNIQUE (storage_profile_code, storage_key),
    DROP CHECK chk_psm_cleanup_reason,
    ADD CONSTRAINT chk_psm_cleanup_reason CHECK (cleanup_reason IN (
        'TEMPORARY_EXPIRY', 'SUPERSEDED_RETENTION', 'LOGICAL_DELETE',
        'DISCARD_ATTEMPT', 'ACTIVATION_COMPENSATION', 'MIGRATION_SOURCE_DELETE'
    ));

-- Explicit copy/verify/update/delete seam. No migration job is seeded or run.
CREATE TABLE practice_storage_migration_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    logical_type VARCHAR(40) NOT NULL,
    logical_id BIGINT NOT NULL,
    source_profile_code VARCHAR(40) NULL,
    source_storage_key VARCHAR(512) NOT NULL,
    target_profile_code VARCHAR(40) NOT NULL,
    target_storage_key VARCHAR(512) NOT NULL,
    target_storage_provider VARCHAR(32) NULL,
    expected_size BIGINT NOT NULL,
    expected_sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    copy_attempt_count INT NOT NULL DEFAULT 0,
    cleanup_attempt_count INT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64) NULL,
    next_attempt_at DATETIME NULL,
    claim_token VARCHAR(64) NULL,
    lease_expires_at DATETIME NULL,
    verified_at DATETIME NULL,
    logical_updated_at DATETIME NULL,
    cleanup_not_before DATETIME NULL,
    completed_at DATETIME NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_practice_storage_migration_type CHECK (logical_type IN (
        'LECTURER_ASSET', 'PDF_IMPORT_SESSION', 'SPEAKING_MEDIA'
    )),
    CONSTRAINT chk_practice_storage_migration_status CHECK (status IN (
        'PLANNED', 'COPYING', 'COPIED_VERIFIED', 'LOGICAL_UPDATED',
        'CLEANUP_PENDING', 'DELETING_SOURCE', 'COMPLETED', 'FAILED'
    )),
    CONSTRAINT fk_practice_storage_migration_source_profile
        FOREIGN KEY (source_profile_code) REFERENCES storage_profiles(profile_code),
    CONSTRAINT fk_practice_storage_migration_target_profile
        FOREIGN KEY (target_profile_code) REFERENCES storage_profiles(profile_code),
    CONSTRAINT uk_practice_storage_migration_logical
        UNIQUE (logical_type, logical_id, target_profile_code),
    CONSTRAINT uk_practice_storage_migration_target
        UNIQUE (target_profile_code, target_storage_key),
    INDEX idx_practice_storage_migration_status (status, next_attempt_at, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
