DELETE FROM question_explanation_cache;

ALTER TABLE question_explanation_cache
    DROP INDEX uq_explanation;

ALTER TABLE question_explanation_cache
    ADD COLUMN cache_key CHAR(64) NOT NULL AFTER id,
    ADD COLUMN prompt_version VARCHAR(32) NOT NULL AFTER ai_model,
    ADD COLUMN schema_version VARCHAR(32) NOT NULL AFTER prompt_version,
    ADD COLUMN explanation_language VARCHAR(16) NOT NULL AFTER schema_version,
    MODIFY ai_model VARCHAR(100) NOT NULL;

ALTER TABLE question_explanation_cache
    ADD UNIQUE KEY uq_question_explanation_cache_key (cache_key),
    ADD INDEX idx_qec_question_id (question_id),
    ADD INDEX idx_qec_skill_type (skill_type);
