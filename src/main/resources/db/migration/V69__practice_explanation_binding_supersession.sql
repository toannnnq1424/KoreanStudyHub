ALTER TABLE question_version_explanation_bindings
    ADD COLUMN binding_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        AFTER fingerprint,
    ADD COLUMN superseded_at DATETIME NULL
        AFTER bound_at,
    ADD COLUMN active_explanation_language VARCHAR(16)
        GENERATED ALWAYS AS (
            CASE
                WHEN binding_status = 'ACTIVE' THEN explanation_language
                ELSE NULL
            END
        ) STORED,
    DROP INDEX uk_qveb_question_language,
    ADD CONSTRAINT uk_qveb_active_question_language
        UNIQUE (question_version_id, active_explanation_language),
    ADD INDEX idx_qveb_question_language_history
        (question_version_id, explanation_language, binding_status, id),
    ADD CONSTRAINT chk_qveb_binding_status
        CHECK (binding_status IN ('ACTIVE', 'SUPERSEDED'));
