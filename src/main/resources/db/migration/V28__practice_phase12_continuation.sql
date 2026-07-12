-- Phase 12 continuation: bind stable scenario roots to a program and every
-- immutable scenario version to the exact program policy version it validates.
ALTER TABLE assessment_programs
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER active_version_id;

ALTER TABLE assessment_exam_templates
    ADD COLUMN program_code VARCHAR(40) NULL AFTER code;

UPDATE assessment_exam_templates t
JOIN assessment_program_versions v ON v.id = t.program_version_id
SET t.program_code = v.program_code;

ALTER TABLE assessment_exam_templates
    ADD INDEX idx_aet_program_version (program_version_id);

ALTER TABLE assessment_exam_templates
    MODIFY program_code VARCHAR(40) NOT NULL,
    DROP INDEX uk_aet_program_category,
    ADD CONSTRAINT uk_aet_program_category UNIQUE (program_code, category_code),
    ADD INDEX idx_aet_program_enabled (program_code, enabled, display_name),
    ADD CONSTRAINT fk_aet_program
        FOREIGN KEY (program_code) REFERENCES assessment_programs(code);

ALTER TABLE assessment_exam_template_versions
    ADD COLUMN program_version_id BIGINT NULL AFTER template_code;

UPDATE assessment_exam_template_versions v
JOIN assessment_exam_templates t ON t.code = v.template_code
SET v.program_version_id = t.program_version_id;

ALTER TABLE assessment_exam_template_versions
    MODIFY program_version_id BIGINT NOT NULL,
    ADD INDEX idx_aetv_program_template
        (program_version_id, template_code, status, version_number),
    ADD CONSTRAINT fk_aetv_program_version
        FOREIGN KEY (program_version_id) REFERENCES assessment_program_versions(id);
