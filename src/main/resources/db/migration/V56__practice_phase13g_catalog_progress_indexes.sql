-- Phase 13G forward-only query support.
-- Existing migrations and retained Practice data remain unchanged.

ALTER TABLE practice_attempts
    ADD COLUMN activity_at DATETIME
        GENERATED ALWAYS AS (
            COALESCE(submitted_at, updated_at, created_at)
        ) STORED;

CREATE INDEX idx_practice_sets_catalog_page
    ON practice_sets (status, is_deleted, created_at, id);

CREATE INDEX idx_practice_sets_catalog_class_page
    ON practice_sets (status, is_deleted, scope, class_id, created_at, id);

CREATE INDEX idx_practice_sets_catalog_owner_page
    ON practice_sets (status, is_deleted, created_by, created_at, id);

CREATE INDEX idx_practice_questions_set_writing_task
    ON practice_questions (set_id, writing_task_type);

CREATE INDEX idx_practice_attempts_user_writing_activity
    ON practice_attempts (
        user_id, skill, activity_at DESC, id DESC, status
    );

CREATE INDEX idx_practice_attempts_user_set_activity
    ON practice_attempts (
        user_id, set_id, activity_at DESC, id DESC, status
    );

CREATE INDEX idx_practice_attempts_user_section_status
    ON practice_attempts (user_id, section_id, status);
