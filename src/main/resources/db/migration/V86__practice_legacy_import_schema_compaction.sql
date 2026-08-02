-- CLEAN_CUT_4: compact the schema after the C3 runtime stopped mapping and
-- querying the legacy PDF workspace. V1-V85 remain immutable.
--
-- This is intentionally a clean-cut migration. It never copies rows or scans
-- object storage. The preflight fails before any DDL when a disposable catalog
-- still contains legacy runtime state or nullable storage identities.

DROP PROCEDURE IF EXISTS practice_c4_schema_compaction_preflight;

DELIMITER $$

CREATE PROCEDURE practice_c4_schema_compaction_preflight()
BEGIN
    IF (SELECT COUNT(*) FROM practice_pdf_page_extractions) <> 0
       OR (SELECT COUNT(*) FROM practice_pdf_region_annotations) <> 0
       OR (SELECT COUNT(*) FROM practice_pdf_import_section_drafts) <> 0
       OR (SELECT COUNT(*) FROM practice_pdf_import_group_drafts) <> 0
       OR (SELECT COUNT(*) FROM practice_ai_request_audits) <> 0
       OR (SELECT COUNT(*) FROM practice_pdf_import_sessions) <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'C4_PDF_WORKSPACE_ROWS_MUST_BE_EMPTY';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM lecturer_assets
        WHERE source_import_session_id IS NOT NULL
           OR source_region_id IS NOT NULL
           OR source_page_number IS NOT NULL
           OR crop_x IS NOT NULL
           OR crop_y IS NOT NULL
           OR crop_width IS NOT NULL
           OR crop_height IS NOT NULL
           OR source_type = 'PDF_REGION'
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'C4_PDF_PROVENANCE_MUST_BE_EMPTY';
    END IF;

    IF EXISTS (SELECT 1 FROM lecturer_assets WHERE storage_profile_code IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM practice_speaking_media WHERE storage_profile_code IS NULL LIMIT 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'C4_RETAINED_STORAGE_PROFILE_REQUIRED';
    END IF;

    IF (SELECT COUNT(*) FROM practice_asset_lifecycle_tasks) <> 0
       OR (SELECT COUNT(*) FROM practice_speaking_media_cleanup_tasks) <> 0
       OR (SELECT COUNT(*) FROM practice_storage_migration_jobs) <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'C4_STORAGE_WORK_QUEUES_MUST_BE_EMPTY';
    END IF;
END$$

DELIMITER ;

CALL practice_c4_schema_compaction_preflight();
DROP PROCEDURE practice_c4_schema_compaction_preflight;

-- Remove the only FK into the workspace root before dropping leaf tables.
ALTER TABLE practice_pdf_page_extractions
    DROP FOREIGN KEY fk_page_extract_session,
    DROP INDEX idx_page_extract_session_page;
DROP TABLE practice_pdf_page_extractions;

ALTER TABLE practice_pdf_region_annotations
    DROP INDEX idx_pdf_region_session_page,
    DROP INDEX idx_pdf_region_session_type,
    DROP INDEX idx_pdf_region_session_order;
DROP TABLE practice_pdf_region_annotations;

ALTER TABLE practice_pdf_import_section_drafts
    DROP INDEX idx_pdf_section_draft_session;
DROP TABLE practice_pdf_import_section_drafts;

ALTER TABLE practice_pdf_import_group_drafts
    DROP INDEX idx_pdf_group_draft_session;
DROP TABLE practice_pdf_import_group_drafts;

ALTER TABLE practice_ai_request_audits
    DROP INDEX idx_ai_audit_session;
DROP TABLE practice_ai_request_audits;

ALTER TABLE practice_pdf_import_sessions
    DROP FOREIGN KEY fk_pdf_session_storage_profile,
    DROP INDEX idx_pdf_session_uploader,
    DROP INDEX idx_pdf_session_target,
    DROP INDEX idx_pdf_session_generation_lease,
    DROP INDEX idx_pdf_session_profile_path;
DROP TABLE practice_pdf_import_sessions;

-- Canonical assets remain. Only the retired crop/session provenance is removed.
ALTER TABLE lecturer_assets
    DROP INDEX idx_lecturer_assets_session,
    DROP COLUMN source_import_session_id,
    DROP COLUMN source_region_id,
    DROP COLUMN source_page_number,
    DROP COLUMN crop_x,
    DROP COLUMN crop_y,
    DROP COLUMN crop_width,
    DROP COLUMN crop_height,
    MODIFY source_type VARCHAR(64) NOT NULL DEFAULT 'MANUAL_UPLOAD',
    MODIFY storage_profile_code VARCHAR(40) NOT NULL;

-- C4 removes provider/key-only identity. Exact Practice profile identity is
-- mandatory; the provider columns stay as read/provider evidence.
ALTER TABLE practice_asset_lifecycle_tasks
    MODIFY storage_profile_code VARCHAR(40) NOT NULL;

ALTER TABLE practice_speaking_media
    DROP INDEX uk_psm_storage,
    MODIFY storage_profile_code VARCHAR(40) NOT NULL;

ALTER TABLE practice_speaking_media_cleanup_tasks
    DROP INDEX uk_psm_cleanup_storage,
    MODIFY storage_profile_code VARCHAR(40) NOT NULL;
