CREATE TABLE assessment_exam_templates (
    code VARCHAR(80) PRIMARY KEY,
    program_version_id BIGINT NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    config_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aet_program_version
        FOREIGN KEY (program_version_id) REFERENCES assessment_program_versions(id),
    CONSTRAINT uk_aet_program_category UNIQUE (program_version_id, category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO assessment_exam_templates
    (code, program_version_id, display_name, category_code, enabled, config_json)
SELECT 'TOPIK_I', id, 'TOPIK I', 'TOPIK_I', 1,
       '{"schemaVersion":"assessment-template-v1","skills":{"LISTENING":{"durationMinutes":40,"defaultPoints":2,"pointsEditable":true,"questionTypes":["SINGLE_CHOICE"]},"READING":{"durationMinutes":60,"defaultPoints":2,"pointsEditable":true,"questionTypes":["SINGLE_CHOICE"]}}}'
FROM assessment_program_versions
WHERE program_code = 'TOPIK' AND version_number = 1;

INSERT INTO assessment_exam_templates
    (code, program_version_id, display_name, category_code, enabled, config_json)
SELECT 'TOPIK_II', id, 'TOPIK II', 'TOPIK_II', 1,
       '{"schemaVersion":"assessment-template-v1","skills":{"LISTENING":{"durationMinutes":60,"defaultPoints":2,"pointsEditable":true,"questionTypes":["SINGLE_CHOICE"]},"READING":{"durationMinutes":70,"defaultPoints":2,"pointsEditable":true,"questionTypes":["SINGLE_CHOICE"]},"WRITING":{"durationMinutes":50,"defaultPoints":25,"pointsEditable":true,"questionTypes":["ESSAY"]},"SPEAKING":{"durationMinutes":30,"defaultPoints":100,"pointsEditable":true,"questionTypes":["SPEAKING"]}}}'
FROM assessment_program_versions
WHERE program_code = 'TOPIK' AND version_number = 1;

INSERT INTO assessment_exam_templates
    (code, program_version_id, display_name, category_code, enabled, config_json)
SELECT 'CUSTOM_FLEXIBLE', id, 'Bài luyện tập tùy chỉnh', 'CUSTOM', 1,
       '{"schemaVersion":"assessment-template-v1","skills":{"LISTENING":{"durationMinutes":40,"defaultPoints":1,"pointsEditable":true,"questionTypes":["SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE_NOT_GIVEN","FILL_BLANK","MATCHING"],"scoringPolicies":{"MULTIPLE_CHOICE":["ALL_OR_NOTHING","PARTIAL_BY_CORRECT_OPTION_WITH_WRONG_ZERO"]}},"READING":{"durationMinutes":40,"defaultPoints":1,"pointsEditable":true,"questionTypes":["SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE_NOT_GIVEN","FILL_BLANK","MATCHING"],"scoringPolicies":{"MULTIPLE_CHOICE":["ALL_OR_NOTHING","PARTIAL_BY_CORRECT_OPTION_WITH_WRONG_ZERO"]}},"WRITING":{"durationMinutes":50,"defaultPoints":100,"pointsEditable":true,"questionTypes":["ESSAY"]},"SPEAKING":{"durationMinutes":30,"defaultPoints":100,"pointsEditable":true,"questionTypes":["SPEAKING"]}}}'
FROM assessment_program_versions
WHERE program_code = 'CUSTOM' AND version_number = 1;

UPDATE assessment_question_type_policies p
JOIN assessment_program_versions v ON v.id = p.program_version_id
SET p.enabled = 1
WHERE v.program_code = 'CUSTOM'
  AND v.version_number = 1
  AND p.skill_code IN ('READING', 'LISTENING')
  AND p.canonical_question_type IN (
      'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE_NOT_GIVEN', 'FILL_BLANK', 'MATCHING'
  );

INSERT INTO assessment_scoring_profiles (code, version_number, config_json, enabled) VALUES
    ('CUSTOM_ESSAY', 1, '{"policyCode":"PROFILE_BASED","compatibilityAdapter":"WRITING_GENERAL_V1"}', 1),
    ('CUSTOM_SPEAKING', 1, '{"policyCode":"PROFILE_BASED","compatibilityAdapter":"SPEAKING_EXISTING_V1"}', 1);

INSERT INTO assessment_prompt_profiles
    (code, version_number, skill_code, task_type, compatibility_adapter, system_rules, enabled)
VALUES
    ('CUSTOM_ESSAY', 1, 'WRITING', 'GENERAL', 'WRITING_EXISTING_V1', NULL, 1),
    ('CUSTOM_SPEAKING', 1, 'SPEAKING', 'DEFAULT', 'SPEAKING_EXISTING_V1', NULL, 1);

INSERT INTO assessment_rubric_profiles
    (code, version_number, skill_code, task_type, config_json, enabled)
VALUES
    ('CUSTOM_ESSAY', 1, 'WRITING', 'GENERAL', '{"compatibilityAdapter":"WRITING_GENERAL_V1"}', 1),
    ('CUSTOM_SPEAKING', 1, 'SPEAKING', 'DEFAULT', '{"compatibilityAdapter":"SPEAKING_EXISTING_V1"}', 1);

INSERT INTO assessment_question_type_policies
    (program_version_id, skill_code, canonical_question_type, enabled,
     default_scoring_policy_code, scoring_profile_id, prompt_profile_id, rubric_profile_id)
SELECT v.id, 'WRITING', 'ESSAY', 1, 'PROFILE_BASED', s.id, p.id, r.id
FROM assessment_program_versions v
JOIN assessment_scoring_profiles s ON s.code = 'CUSTOM_ESSAY' AND s.version_number = 1
JOIN assessment_prompt_profiles p ON p.code = 'CUSTOM_ESSAY' AND p.version_number = 1
JOIN assessment_rubric_profiles r ON r.code = 'CUSTOM_ESSAY' AND r.version_number = 1
WHERE v.program_code = 'CUSTOM' AND v.version_number = 1;

INSERT INTO assessment_question_type_policies
    (program_version_id, skill_code, canonical_question_type, enabled,
     default_scoring_policy_code, scoring_profile_id, prompt_profile_id, rubric_profile_id)
SELECT v.id, 'SPEAKING', 'SPEAKING', 1, 'PROFILE_BASED', s.id, p.id, r.id
FROM assessment_program_versions v
JOIN assessment_scoring_profiles s ON s.code = 'CUSTOM_SPEAKING' AND s.version_number = 1
JOIN assessment_prompt_profiles p ON p.code = 'CUSTOM_SPEAKING' AND p.version_number = 1
JOIN assessment_rubric_profiles r ON r.code = 'CUSTOM_SPEAKING' AND r.version_number = 1
WHERE v.program_code = 'CUSTOM' AND v.version_number = 1;

ALTER TABLE practice_drafts
    ADD COLUMN draft_schema_version VARCHAR(40) NULL AFTER creation_method,
    ADD COLUMN assessment_program_code VARCHAR(40) NULL AFTER draft_schema_version,
    ADD COLUMN assessment_program_version_id BIGINT NULL AFTER assessment_program_code,
    ADD COLUMN exam_template_code VARCHAR(80) NULL AFTER assessment_program_version_id,
    ADD INDEX idx_pd_program_template (assessment_program_code, exam_template_code),
    ADD CONSTRAINT fk_pd_program FOREIGN KEY (assessment_program_code) REFERENCES assessment_programs(code),
    ADD CONSTRAINT fk_pd_program_version FOREIGN KEY (assessment_program_version_id) REFERENCES assessment_program_versions(id),
    ADD CONSTRAINT fk_pd_exam_template FOREIGN KEY (exam_template_code) REFERENCES assessment_exam_templates(code);

UPDATE practice_drafts d
JOIN assessment_programs p
  ON p.code = CASE WHEN d.category LIKE 'TOPIK%' THEN 'TOPIK' ELSE 'CUSTOM' END
SET d.draft_schema_version = 'practice-draft-v2',
    d.assessment_program_code = p.code,
    d.assessment_program_version_id = p.active_version_id,
    d.exam_template_code = CASE
        WHEN d.category = 'TOPIK_I' THEN 'TOPIK_I'
        WHEN d.category LIKE 'TOPIK%' THEN 'TOPIK_II'
        ELSE 'CUSTOM_FLEXIBLE'
    END;

ALTER TABLE practice_sets
    ADD COLUMN assessment_program_version_id BIGINT NULL AFTER assessment_program_code,
    ADD COLUMN exam_template_code VARCHAR(80) NULL AFTER assessment_program_version_id,
    ADD INDEX idx_ps_exam_template (exam_template_code),
    ADD CONSTRAINT fk_ps_program_version FOREIGN KEY (assessment_program_version_id) REFERENCES assessment_program_versions(id),
    ADD CONSTRAINT fk_ps_exam_template FOREIGN KEY (exam_template_code) REFERENCES assessment_exam_templates(code);

UPDATE practice_sets s
JOIN assessment_programs p ON p.code = s.assessment_program_code
SET s.assessment_program_version_id = p.active_version_id,
    s.exam_template_code = CASE
        WHEN s.topik_level = 'TOPIK_I' THEN 'TOPIK_I'
        WHEN s.assessment_program_code = 'TOPIK' THEN 'TOPIK_II'
        ELSE 'CUSTOM_FLEXIBLE'
    END;

ALTER TABLE practice_set_versions
    ADD COLUMN assessment_program_version_id BIGINT NULL AFTER assessment_program_code,
    ADD COLUMN exam_template_code VARCHAR(80) NULL AFTER assessment_program_version_id,
    ADD INDEX idx_psv_exam_template (exam_template_code),
    ADD CONSTRAINT fk_psv_program_version FOREIGN KEY (assessment_program_version_id) REFERENCES assessment_program_versions(id),
    ADD CONSTRAINT fk_psv_exam_template FOREIGN KEY (exam_template_code) REFERENCES assessment_exam_templates(code);

UPDATE practice_set_versions v
JOIN practice_sets s ON s.id = v.set_id
SET v.assessment_program_version_id = s.assessment_program_version_id,
    v.exam_template_code = s.exam_template_code;

ALTER TABLE practice_question_groups
    ADD COLUMN stimulus_type VARCHAR(40) NULL AFTER instruction,
    ADD COLUMN passage_text LONGTEXT NULL AFTER stimulus_type,
    ADD COLUMN transcript_text LONGTEXT NULL AFTER passage_text,
    ADD COLUMN image_url VARCHAR(500) NULL AFTER transcript_text,
    ADD COLUMN stimulus_provenance_json JSON NULL AFTER image_url;

UPDATE practice_question_groups g
JOIN practice_sections s ON s.id = g.section_id
SET g.stimulus_type = CASE
        WHEN s.skill = 'READING' AND NULLIF(TRIM(g.instruction), '') IS NOT NULL THEN 'READING_PASSAGE'
        WHEN s.skill = 'LISTENING' THEN 'LISTENING_AUDIO'
        ELSE 'NONE'
    END,
    g.passage_text = CASE WHEN s.skill = 'READING' THEN g.instruction ELSE NULL END,
    g.transcript_text = CASE WHEN s.skill = 'LISTENING' THEN g.instruction ELSE NULL END,
    g.stimulus_provenance_json = JSON_OBJECT('source', 'LEGACY_GROUP_INSTRUCTION', 'approved', 0);

ALTER TABLE practice_question_group_versions
    ADD COLUMN stimulus_type VARCHAR(40) NULL AFTER instruction,
    ADD COLUMN passage_text LONGTEXT NULL AFTER stimulus_type,
    ADD COLUMN transcript_text LONGTEXT NULL AFTER passage_text,
    ADD COLUMN image_url VARCHAR(500) NULL AFTER transcript_text,
    ADD COLUMN stimulus_provenance_json JSON NULL AFTER image_url;

UPDATE practice_question_group_versions gv
JOIN practice_question_groups g ON g.id = gv.group_id
SET gv.stimulus_type = g.stimulus_type,
    gv.passage_text = g.passage_text,
    gv.transcript_text = g.transcript_text,
    gv.image_url = g.image_url,
    gv.stimulus_provenance_json = g.stimulus_provenance_json;

ALTER TABLE practice_attempts
    ADD COLUMN score_unit VARCHAR(30) NULL AFTER total_points,
    ADD COLUMN earned_points DECIMAL(8,2) NULL AFTER score_unit,
    ADD COLUMN score_percentage DECIMAL(6,2) NULL AFTER earned_points;

UPDATE practice_attempts
SET score_unit = CASE
        WHEN skill IN ('WRITING', 'SPEAKING') THEN 'PERCENTAGE'
        ELSE 'EARNED_POINTS'
    END,
    earned_points = CASE
        WHEN score IS NULL THEN NULL
        WHEN skill IN ('WRITING', 'SPEAKING') AND total_points > 0
            THEN ROUND(LEAST(100, GREATEST(0, score)) * total_points / 100, 2)
        WHEN total_points > 0 THEN LEAST(total_points, GREATEST(0, score))
        ELSE GREATEST(0, score)
    END,
    score_percentage = CASE
        WHEN score IS NULL THEN NULL
        WHEN skill IN ('WRITING', 'SPEAKING') THEN LEAST(100, GREATEST(0, score))
        WHEN total_points > 0 THEN LEAST(100, ROUND(GREATEST(0, score) * 100 / total_points, 2))
        ELSE 0
    END;

-- Multi-test authoring and policy limits are part of the same unreleased
-- Phase 11 contract. Version rows retain source IDs as trace data without
-- preventing replacement of a live test graph.
ALTER TABLE practice_test_versions
    DROP FOREIGN KEY fk_ptv_test;

UPDATE practice_drafts
SET draft_schema_version = 'practice-draft-v3'
WHERE draft_schema_version IS NULL OR draft_schema_version IN ('practice-draft-v1', 'practice-draft-v2');

UPDATE assessment_exam_templates
SET config_json = JSON_SET(
        config_json,
        '$.maxTests', 10,
        '$.skills.LISTENING.maxQuestions', 50,
        '$.skills.LISTENING.excelImportEnabled', TRUE,
        '$.skills.LISTENING.questionRules.SINGLE_CHOICE.minOptions', 4,
        '$.skills.LISTENING.questionRules.SINGLE_CHOICE.maxOptions', 4,
        '$.skills.READING.maxQuestions', 50,
        '$.skills.READING.excelImportEnabled', TRUE,
        '$.skills.READING.questionRules.SINGLE_CHOICE.minOptions', 4,
        '$.skills.READING.questionRules.SINGLE_CHOICE.maxOptions', 4
    )
WHERE code IN ('TOPIK_I', 'TOPIK_II');

UPDATE assessment_exam_templates
SET config_json = JSON_SET(
        config_json,
        '$.skills.WRITING.maxQuestions', 4,
        '$.skills.WRITING.excelImportEnabled', TRUE,
        '$.skills.SPEAKING.maxQuestions', 4,
        '$.skills.SPEAKING.excelImportEnabled', TRUE
    )
WHERE code = 'TOPIK_II';

UPDATE assessment_exam_templates
SET config_json = JSON_SET(
        config_json,
        '$.maxTests', 20,
        '$.skills.LISTENING.maxQuestions', 200,
        '$.skills.LISTENING.excelImportEnabled', TRUE,
        '$.skills.LISTENING.questionRules.SINGLE_CHOICE.minOptions', 2,
        '$.skills.LISTENING.questionRules.SINGLE_CHOICE.maxOptions', 8,
        '$.skills.LISTENING.questionRules.MULTIPLE_CHOICE.minOptions', 2,
        '$.skills.LISTENING.questionRules.MULTIPLE_CHOICE.maxOptions', 8,
        '$.skills.READING.maxQuestions', 200,
        '$.skills.READING.excelImportEnabled', TRUE,
        '$.skills.READING.questionRules.SINGLE_CHOICE.minOptions', 2,
        '$.skills.READING.questionRules.SINGLE_CHOICE.maxOptions', 8,
        '$.skills.READING.questionRules.MULTIPLE_CHOICE.minOptions', 2,
        '$.skills.READING.questionRules.MULTIPLE_CHOICE.maxOptions', 8,
        '$.skills.WRITING.maxQuestions', 50,
        '$.skills.WRITING.excelImportEnabled', TRUE,
        '$.skills.SPEAKING.maxQuestions', 50,
        '$.skills.SPEAKING.excelImportEnabled', TRUE
    )
WHERE code = 'CUSTOM_FLEXIBLE';
