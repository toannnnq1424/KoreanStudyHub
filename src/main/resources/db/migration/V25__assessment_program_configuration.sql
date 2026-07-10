-- Immutable snapshots retain source IDs for traceability, but must not keep
-- foreign keys that prevent the live authoring graph from being replaced.
ALTER TABLE practice_section_versions
    DROP FOREIGN KEY fk_pscv_section;

ALTER TABLE practice_question_group_versions
    DROP FOREIGN KEY fk_pqgv_group;

ALTER TABLE practice_question_versions
    DROP FOREIGN KEY fk_pqv_question;

CREATE TABLE assessment_programs (
    code VARCHAR(40) PRIMARY KEY,
    active_version_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assessment_program_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    program_code VARCHAR(40) NOT NULL,
    version_number INT NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    default_language VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_apv_program_version UNIQUE (program_code, version_number),
    CONSTRAINT fk_apv_program FOREIGN KEY (program_code) REFERENCES assessment_programs(code),
    CONSTRAINT chk_apv_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE assessment_programs
    ADD CONSTRAINT fk_ap_active_version FOREIGN KEY (active_version_id) REFERENCES assessment_program_versions(id);

CREATE TABLE assessment_skills (
    code VARCHAR(30) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assessment_program_skill_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    program_version_id BIGINT NOT NULL,
    skill_code VARCHAR(30) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    delivery_mode VARCHAR(40) NOT NULL,
    CONSTRAINT uk_aps_program_skill UNIQUE (program_version_id, skill_code),
    CONSTRAINT fk_aps_program_version FOREIGN KEY (program_version_id) REFERENCES assessment_program_versions(id),
    CONSTRAINT fk_aps_skill FOREIGN KEY (skill_code) REFERENCES assessment_skills(code),
    CONSTRAINT chk_aps_delivery_mode CHECK (delivery_mode IN ('SKILL_SPECIFIC','FULL_TEST','BOTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assessment_scoring_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    version_number INT NOT NULL,
    config_json JSON NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ascp_code_version UNIQUE (code, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assessment_prompt_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    version_number INT NOT NULL,
    skill_code VARCHAR(30) NOT NULL,
    task_type VARCHAR(40) NULL,
    compatibility_adapter VARCHAR(100) NULL,
    system_rules TEXT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_code_version UNIQUE (code, version_number),
    CONSTRAINT fk_app_skill FOREIGN KEY (skill_code) REFERENCES assessment_skills(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assessment_rubric_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    version_number INT NOT NULL,
    skill_code VARCHAR(30) NOT NULL,
    task_type VARCHAR(40) NULL,
    config_json JSON NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_arp_code_version UNIQUE (code, version_number),
    CONSTRAINT fk_arp_skill FOREIGN KEY (skill_code) REFERENCES assessment_skills(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assessment_question_type_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    program_version_id BIGINT NOT NULL,
    skill_code VARCHAR(30) NOT NULL,
    canonical_question_type VARCHAR(40) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    default_scoring_policy_code VARCHAR(80) NOT NULL,
    scoring_profile_id BIGINT NULL,
    prompt_profile_id BIGINT NULL,
    rubric_profile_id BIGINT NULL,
    CONSTRAINT uk_aqtp_program_skill_type UNIQUE (program_version_id, skill_code, canonical_question_type),
    CONSTRAINT fk_aqtp_program_version FOREIGN KEY (program_version_id) REFERENCES assessment_program_versions(id),
    CONSTRAINT fk_aqtp_skill FOREIGN KEY (skill_code) REFERENCES assessment_skills(code),
    CONSTRAINT fk_aqtp_scoring_profile FOREIGN KEY (scoring_profile_id) REFERENCES assessment_scoring_profiles(id),
    CONSTRAINT fk_aqtp_prompt_profile FOREIGN KEY (prompt_profile_id) REFERENCES assessment_prompt_profiles(id),
    CONSTRAINT fk_aqtp_rubric_profile FOREIGN KEY (rubric_profile_id) REFERENCES assessment_rubric_profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO assessment_programs (code) VALUES ('TOPIK'), ('CUSTOM');

INSERT INTO assessment_program_versions
    (program_code, version_number, display_name, status, default_language)
VALUES
    ('TOPIK', 1, 'Test of Proficiency in Korean', 'ACTIVE', 'ko'),
    ('CUSTOM', 1, 'Custom Korean Assessment', 'ACTIVE', 'ko');

UPDATE assessment_programs p
JOIN assessment_program_versions v ON v.program_code = p.code AND v.version_number = 1
SET p.active_version_id = v.id;

INSERT INTO assessment_skills (code, display_name) VALUES
    ('READING', 'Reading'),
    ('LISTENING', 'Listening'),
    ('WRITING', 'Writing'),
    ('SPEAKING', 'Speaking');

INSERT INTO assessment_program_skill_policies
    (program_version_id, skill_code, enabled, delivery_mode)
SELECT v.id, s.code, 1, 'SKILL_SPECIFIC'
FROM assessment_program_versions v
JOIN assessment_skills s
WHERE v.version_number = 1 AND v.program_code IN ('TOPIK', 'CUSTOM');

INSERT INTO assessment_scoring_profiles (code, version_number, config_json, enabled) VALUES
    ('TOPIK_SINGLE_CHOICE', 1, '{"policyCode":"ALL_OR_NOTHING","compatibilityAdapter":"LEGACY_MCQ"}', 1),
    ('TOPIK_WRITING_Q51', 1, '{"policyCode":"PROFILE_BASED","compatibilityAdapter":"WRITING_Q51"}', 1),
    ('TOPIK_WRITING_Q52', 1, '{"policyCode":"PROFILE_BASED","compatibilityAdapter":"WRITING_Q52"}', 1),
    ('TOPIK_WRITING_Q53', 1, '{"policyCode":"PROFILE_BASED","compatibilityAdapter":"WRITING_Q53"}', 1),
    ('TOPIK_WRITING_Q54', 1, '{"policyCode":"PROFILE_BASED","compatibilityAdapter":"WRITING_Q54"}', 1),
    ('TOPIK_SPEAKING', 1, '{"policyCode":"PROFILE_BASED","compatibilityAdapter":"SPEAKING_EXISTING_V1"}', 1);

INSERT INTO assessment_prompt_profiles
    (code, version_number, skill_code, task_type, compatibility_adapter, system_rules, enabled)
VALUES
    ('TOPIK_WRITING_Q51', 1, 'WRITING', 'Q51', 'WRITING_EXISTING_V1', NULL, 1),
    ('TOPIK_WRITING_Q52', 1, 'WRITING', 'Q52', 'WRITING_EXISTING_V1', NULL, 1),
    ('TOPIK_WRITING_Q53', 1, 'WRITING', 'Q53', 'WRITING_EXISTING_V1', NULL, 1),
    ('TOPIK_WRITING_Q54', 1, 'WRITING', 'Q54', 'WRITING_EXISTING_V1', NULL, 1),
    ('TOPIK_SPEAKING', 1, 'SPEAKING', 'DEFAULT', 'SPEAKING_EXISTING_V1', NULL, 1);

INSERT INTO assessment_rubric_profiles
    (code, version_number, skill_code, task_type, config_json, enabled)
VALUES
    ('TOPIK_WRITING_Q51', 1, 'WRITING', 'Q51', '{"compatibilityAdapter":"WRITING_Q51"}', 1),
    ('TOPIK_WRITING_Q52', 1, 'WRITING', 'Q52', '{"compatibilityAdapter":"WRITING_Q52"}', 1),
    ('TOPIK_WRITING_Q53', 1, 'WRITING', 'Q53', '{"compatibilityAdapter":"WRITING_Q53"}', 1),
    ('TOPIK_WRITING_Q54', 1, 'WRITING', 'Q54', '{"compatibilityAdapter":"WRITING_Q54"}', 1),
    ('TOPIK_SPEAKING', 1, 'SPEAKING', 'DEFAULT', '{"compatibilityAdapter":"SPEAKING_EXISTING_V1"}', 1);

INSERT INTO assessment_question_type_policies
    (program_version_id, skill_code, canonical_question_type, enabled,
     default_scoring_policy_code, scoring_profile_id, prompt_profile_id, rubric_profile_id)
SELECT v.id, 'READING', 'SINGLE_CHOICE', 1, 'ALL_OR_NOTHING', s.id, NULL, NULL
FROM assessment_program_versions v
JOIN assessment_scoring_profiles s ON s.code = 'TOPIK_SINGLE_CHOICE' AND s.version_number = 1
WHERE v.program_code = 'TOPIK' AND v.version_number = 1;

INSERT INTO assessment_question_type_policies
    (program_version_id, skill_code, canonical_question_type, enabled,
     default_scoring_policy_code, scoring_profile_id, prompt_profile_id, rubric_profile_id)
SELECT v.id, 'LISTENING', 'SINGLE_CHOICE', 1, 'ALL_OR_NOTHING', s.id, NULL, NULL
FROM assessment_program_versions v
JOIN assessment_scoring_profiles s ON s.code = 'TOPIK_SINGLE_CHOICE' AND s.version_number = 1
WHERE v.program_code = 'TOPIK' AND v.version_number = 1;

INSERT INTO assessment_question_type_policies
    (program_version_id, skill_code, canonical_question_type, enabled,
     default_scoring_policy_code, scoring_profile_id, prompt_profile_id, rubric_profile_id)
SELECT v.id, 'WRITING', 'ESSAY', 1, 'PROFILE_BASED', s.id, p.id, r.id
FROM assessment_program_versions v
JOIN assessment_scoring_profiles s ON s.code = 'TOPIK_WRITING_Q51' AND s.version_number = 1
JOIN assessment_prompt_profiles p ON p.code = 'TOPIK_WRITING_Q51' AND p.version_number = 1
JOIN assessment_rubric_profiles r ON r.code = 'TOPIK_WRITING_Q51' AND r.version_number = 1
WHERE v.program_code = 'TOPIK' AND v.version_number = 1;

INSERT INTO assessment_question_type_policies
    (program_version_id, skill_code, canonical_question_type, enabled,
     default_scoring_policy_code, scoring_profile_id, prompt_profile_id, rubric_profile_id)
SELECT v.id, 'SPEAKING', 'SPEAKING', 1, 'PROFILE_BASED', s.id, p.id, r.id
FROM assessment_program_versions v
JOIN assessment_scoring_profiles s ON s.code = 'TOPIK_SPEAKING' AND s.version_number = 1
JOIN assessment_prompt_profiles p ON p.code = 'TOPIK_SPEAKING' AND p.version_number = 1
JOIN assessment_rubric_profiles r ON r.code = 'TOPIK_SPEAKING' AND r.version_number = 1
WHERE v.program_code = 'TOPIK' AND v.version_number = 1;

INSERT INTO assessment_question_type_policies
    (program_version_id, skill_code, canonical_question_type, enabled, default_scoring_policy_code)
SELECT v.id, skill_type.skill_code, question_type.type_code, 0, question_type.policy_code
FROM assessment_program_versions v
CROSS JOIN (
    SELECT 'READING' AS skill_code UNION ALL
    SELECT 'LISTENING'
) skill_type
CROSS JOIN (
    SELECT 'SINGLE_CHOICE' AS type_code, 'ALL_OR_NOTHING' AS policy_code UNION ALL
    SELECT 'MULTIPLE_CHOICE', 'ALL_OR_NOTHING' UNION ALL
    SELECT 'TRUE_FALSE_NOT_GIVEN', 'ALL_OR_NOTHING' UNION ALL
    SELECT 'FILL_BLANK', 'NORMALIZED_EXACT' UNION ALL
    SELECT 'MATCHING', 'PER_PAIR'
) question_type
WHERE v.program_code = 'CUSTOM' AND v.version_number = 1;

ALTER TABLE practice_sets
    ADD COLUMN assessment_program_code VARCHAR(40) NULL AFTER topik_level;

UPDATE practice_sets
SET assessment_program_code = CASE
    WHEN topik_level LIKE 'TOPIK%' THEN 'TOPIK'
    ELSE 'CUSTOM'
END;

ALTER TABLE practice_sets
    MODIFY assessment_program_code VARCHAR(40) NOT NULL,
    ADD INDEX idx_ps_assessment_program (assessment_program_code),
    ADD CONSTRAINT fk_ps_assessment_program FOREIGN KEY (assessment_program_code) REFERENCES assessment_programs(code);

ALTER TABLE practice_set_versions
    ADD COLUMN assessment_program_code VARCHAR(40) NULL AFTER topik_level;

UPDATE practice_set_versions v
JOIN practice_sets s ON s.id = v.set_id
SET v.assessment_program_code = s.assessment_program_code;

ALTER TABLE practice_set_versions
    MODIFY assessment_program_code VARCHAR(40) NOT NULL,
    ADD INDEX idx_psv_assessment_program (assessment_program_code),
    ADD CONSTRAINT fk_psv_assessment_program FOREIGN KEY (assessment_program_code) REFERENCES assessment_programs(code);

ALTER TABLE practice_questions
    DROP CHECK chk_pq_type,
    MODIFY question_type VARCHAR(40) NOT NULL,
    ADD COLUMN canonical_question_type VARCHAR(40) NULL AFTER question_type,
    ADD COLUMN question_content_json JSON NULL AFTER options_json,
    ADD COLUMN answer_spec_json JSON NULL AFTER answer_key,
    ADD COLUMN scoring_policy_code VARCHAR(80) NULL AFTER answer_spec_json,
    ADD COLUMN scoring_profile_code VARCHAR(100) NULL AFTER scoring_policy_code,
    ADD COLUMN scoring_profile_version INT NULL AFTER scoring_profile_code,
    ADD COLUMN prompt_profile_code VARCHAR(100) NULL AFTER scoring_profile_version,
    ADD COLUMN prompt_profile_version INT NULL AFTER prompt_profile_code,
    ADD COLUMN rubric_profile_code VARCHAR(100) NULL AFTER prompt_profile_version,
    ADD COLUMN rubric_profile_version INT NULL AFTER rubric_profile_code,
    ADD CONSTRAINT chk_pq_type CHECK (question_type IN (
        'MCQ','SINGLE_CHOICE','MULTIPLE_CHOICE','SHORT_TEXT','ESSAY','SPEAKING',
        'TRUE_FALSE_NOT_GIVEN','MATCHING','MATCHING_INFORMATION','FILL_BLANK','GAP_FILL',
        'ORDERING','TEXT_COMPLETION'
    ));

UPDATE practice_questions
SET canonical_question_type = CASE question_type
        WHEN 'MCQ' THEN 'SINGLE_CHOICE'
        WHEN 'SINGLE_CHOICE' THEN 'SINGLE_CHOICE'
        WHEN 'MULTIPLE_CHOICE' THEN 'MULTIPLE_CHOICE'
        WHEN 'TRUE_FALSE_NOT_GIVEN' THEN 'TRUE_FALSE_NOT_GIVEN'
        WHEN 'MATCHING_INFORMATION' THEN 'MATCHING'
        WHEN 'MATCHING' THEN 'MATCHING'
        WHEN 'FILL_BLANK' THEN 'FILL_BLANK'
        WHEN 'GAP_FILL' THEN 'FILL_BLANK'
        WHEN 'ESSAY' THEN 'ESSAY'
        WHEN 'SPEAKING' THEN 'SPEAKING'
        ELSE NULL
    END,
    scoring_policy_code = CASE question_type
        WHEN 'MCQ' THEN 'ALL_OR_NOTHING'
        WHEN 'SINGLE_CHOICE' THEN 'ALL_OR_NOTHING'
        WHEN 'MULTIPLE_CHOICE' THEN 'ALL_OR_NOTHING'
        WHEN 'TRUE_FALSE_NOT_GIVEN' THEN 'ALL_OR_NOTHING'
        WHEN 'FILL_BLANK' THEN 'NORMALIZED_EXACT'
        WHEN 'GAP_FILL' THEN 'NORMALIZED_EXACT'
        WHEN 'ESSAY' THEN 'PROFILE_BASED'
        WHEN 'SPEAKING' THEN 'PROFILE_BASED'
        ELSE NULL
    END;

ALTER TABLE practice_question_versions
    MODIFY question_type VARCHAR(40) NOT NULL,
    ADD COLUMN canonical_question_type VARCHAR(40) NULL AFTER question_type,
    ADD COLUMN question_content_json JSON NULL AFTER options_json,
    ADD COLUMN answer_spec_json JSON NULL AFTER answer_key,
    ADD COLUMN scoring_policy_code VARCHAR(80) NULL AFTER answer_spec_json,
    ADD COLUMN scoring_profile_code VARCHAR(100) NULL AFTER scoring_policy_code,
    ADD COLUMN scoring_profile_version INT NULL AFTER scoring_profile_code,
    ADD COLUMN prompt_profile_code VARCHAR(100) NULL AFTER scoring_profile_version,
    ADD COLUMN prompt_profile_version INT NULL AFTER prompt_profile_code,
    ADD COLUMN rubric_profile_code VARCHAR(100) NULL AFTER prompt_profile_version,
    ADD COLUMN rubric_profile_version INT NULL AFTER rubric_profile_code;

UPDATE practice_question_versions
SET canonical_question_type = CASE question_type
        WHEN 'MCQ' THEN 'SINGLE_CHOICE'
        WHEN 'SINGLE_CHOICE' THEN 'SINGLE_CHOICE'
        WHEN 'MULTIPLE_CHOICE' THEN 'MULTIPLE_CHOICE'
        WHEN 'TRUE_FALSE_NOT_GIVEN' THEN 'TRUE_FALSE_NOT_GIVEN'
        WHEN 'MATCHING_INFORMATION' THEN 'MATCHING'
        WHEN 'MATCHING' THEN 'MATCHING'
        WHEN 'FILL_BLANK' THEN 'FILL_BLANK'
        WHEN 'GAP_FILL' THEN 'FILL_BLANK'
        WHEN 'ESSAY' THEN 'ESSAY'
        WHEN 'SPEAKING' THEN 'SPEAKING'
        ELSE NULL
    END,
    scoring_policy_code = CASE question_type
        WHEN 'MCQ' THEN 'ALL_OR_NOTHING'
        WHEN 'SINGLE_CHOICE' THEN 'ALL_OR_NOTHING'
        WHEN 'MULTIPLE_CHOICE' THEN 'ALL_OR_NOTHING'
        WHEN 'TRUE_FALSE_NOT_GIVEN' THEN 'ALL_OR_NOTHING'
        WHEN 'FILL_BLANK' THEN 'NORMALIZED_EXACT'
        WHEN 'GAP_FILL' THEN 'NORMALIZED_EXACT'
        WHEN 'ESSAY' THEN 'PROFILE_BASED'
        WHEN 'SPEAKING' THEN 'PROFILE_BASED'
        ELSE NULL
    END;

ALTER TABLE question_explanation_cache
    ADD COLUMN question_version_id BIGINT NULL AFTER question_id,
    ADD COLUMN program_code VARCHAR(40) NULL AFTER test_id,
    ADD COLUMN stimulus_hash CHAR(64) NULL AFTER question_hash,
    ADD COLUMN answer_spec_hash CHAR(64) NULL AFTER stimulus_hash,
    ADD COLUMN prompt_profile_code VARCHAR(100) NULL AFTER prompt_version,
    ADD COLUMN prompt_profile_version INT NULL AFTER prompt_profile_code,
    ADD INDEX idx_qec_question_version (question_version_id),
    ADD CONSTRAINT fk_qec_question_version FOREIGN KEY (question_version_id) REFERENCES practice_question_versions(id),
    ADD CONSTRAINT fk_qec_program FOREIGN KEY (program_code) REFERENCES assessment_programs(code);
