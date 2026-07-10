CREATE TABLE practice_published_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    content_hash CHAR(64) NULL,
    published_by BIGINT NULL,
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ppv_set_version UNIQUE (set_id, version_number),
    INDEX idx_ppv_set_status_version (set_id, status, version_number),
    CONSTRAINT fk_ppv_set FOREIGN KEY (set_id) REFERENCES practice_sets(id),
    CONSTRAINT chk_ppv_status CHECK (status IN ('PUBLISHED','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_set_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    published_version_id BIGINT NOT NULL,
    set_id BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT NULL,
    skill VARCHAR(20) NOT NULL,
    topik_level VARCHAR(20) NULL,
    scope VARCHAR(20) NOT NULL,
    class_id BIGINT NULL,
    metadata_json JSON NULL,
    creation_method VARCHAR(30) NULL,
    cover_image_url VARCHAR(500) NULL,
    CONSTRAINT uk_psv_published UNIQUE (published_version_id),
    INDEX idx_psv_set (set_id),
    CONSTRAINT fk_psv_published FOREIGN KEY (published_version_id) REFERENCES practice_published_versions(id),
    CONSTRAINT fk_psv_set FOREIGN KEY (set_id) REFERENCES practice_sets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_test_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    published_version_id BIGINT NOT NULL,
    set_version_id BIGINT NOT NULL,
    test_id BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT NULL,
    display_order INT NOT NULL,
    estimated_minutes INT NULL,
    INDEX idx_ptv_published_test (published_version_id, test_id),
    INDEX idx_ptv_set_version (set_version_id, display_order),
    CONSTRAINT fk_ptv_published FOREIGN KEY (published_version_id) REFERENCES practice_published_versions(id),
    CONSTRAINT fk_ptv_set_version FOREIGN KEY (set_version_id) REFERENCES practice_set_versions(id),
    CONSTRAINT fk_ptv_test FOREIGN KEY (test_id) REFERENCES practice_tests(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_section_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    published_version_id BIGINT NOT NULL,
    test_version_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    skill VARCHAR(20) NOT NULL,
    section_type VARCHAR(50) NULL,
    instructions TEXT NULL,
    duration_minutes INT NULL,
    total_points DECIMAL(6,2) NULL,
    display_order INT NOT NULL,
    INDEX idx_pscv_published_section (published_version_id, section_id),
    INDEX idx_pscv_test_version (test_version_id, display_order),
    CONSTRAINT fk_pscv_published FOREIGN KEY (published_version_id) REFERENCES practice_published_versions(id),
    CONSTRAINT fk_pscv_test_version FOREIGN KEY (test_version_id) REFERENCES practice_test_versions(id),
    CONSTRAINT fk_pscv_section FOREIGN KEY (section_id) REFERENCES practice_sections(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_question_group_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    published_version_id BIGINT NOT NULL,
    section_version_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    group_label VARCHAR(50) NOT NULL,
    question_from INT NOT NULL,
    question_to INT NOT NULL,
    instruction TEXT NULL,
    audio_url VARCHAR(500) NULL,
    example_json JSON NULL,
    display_order INT NOT NULL,
    INDEX idx_pqgv_section_version (section_version_id, display_order),
    INDEX idx_pqgv_group (group_id),
    CONSTRAINT fk_pqgv_published FOREIGN KEY (published_version_id) REFERENCES practice_published_versions(id),
    CONSTRAINT fk_pqgv_section_version FOREIGN KEY (section_version_id) REFERENCES practice_section_versions(id),
    CONSTRAINT fk_pqgv_group FOREIGN KEY (group_id) REFERENCES practice_question_groups(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_question_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    published_version_id BIGINT NOT NULL,
    section_version_id BIGINT NOT NULL,
    group_version_id BIGINT NULL,
    question_id BIGINT NOT NULL,
    question_no INT NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    prompt TEXT NOT NULL,
    options_json JSON NULL,
    answer_key VARCHAR(500) NULL,
    explanation TEXT NULL,
    points DECIMAL(5,2) NOT NULL,
    display_order INT NOT NULL,
    writing_task_type VARCHAR(20) NULL,
    INDEX idx_pqv_section_version (section_version_id, display_order, question_no),
    INDEX idx_pqv_question (question_id),
    CONSTRAINT fk_pqv_published FOREIGN KEY (published_version_id) REFERENCES practice_published_versions(id),
    CONSTRAINT fk_pqv_section_version FOREIGN KEY (section_version_id) REFERENCES practice_section_versions(id),
    CONSTRAINT fk_pqv_group_version FOREIGN KEY (group_version_id) REFERENCES practice_question_group_versions(id),
    CONSTRAINT fk_pqv_question FOREIGN KEY (question_id) REFERENCES practice_questions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE practice_attempts
    ADD COLUMN published_version_id BIGINT NULL,
    ADD COLUMN set_version_id BIGINT NULL,
    ADD COLUMN test_version_id BIGINT NULL,
    ADD COLUMN section_version_id BIGINT NULL,
    ADD COLUMN version_compatibility_status VARCHAR(40) NULL,
    ADD COLUMN version_compatibility_note VARCHAR(500) NULL,
    ADD INDEX idx_pa_version_lock (published_version_id, set_version_id, test_version_id, section_version_id),
    ADD CONSTRAINT fk_pa_published_version FOREIGN KEY (published_version_id) REFERENCES practice_published_versions(id),
    ADD CONSTRAINT fk_pa_set_version FOREIGN KEY (set_version_id) REFERENCES practice_set_versions(id),
    ADD CONSTRAINT fk_pa_test_version FOREIGN KEY (test_version_id) REFERENCES practice_test_versions(id),
    ADD CONSTRAINT fk_pa_section_version FOREIGN KEY (section_version_id) REFERENCES practice_section_versions(id);

INSERT INTO practice_published_versions (set_id, version_number, status, content_hash, published_by, published_at)
SELECT s.id, 1, 'PUBLISHED', SHA2(CONCAT('baseline:', s.id, ':', COALESCE(s.updated_at, s.created_at)), 256), s.created_by, COALESCE(s.updated_at, s.created_at, CURRENT_TIMESTAMP)
FROM practice_sets s
WHERE s.status = 'PUBLISHED' AND s.is_deleted = 0;

INSERT INTO practice_set_versions (
    published_version_id, set_id, title, description, skill, topik_level, scope, class_id,
    metadata_json, creation_method, cover_image_url
)
SELECT ppv.id, s.id, s.title, s.description, s.skill, s.topik_level, s.scope, s.class_id,
       s.metadata_json, s.creation_method, s.cover_image_url
FROM practice_published_versions ppv
JOIN practice_sets s ON s.id = ppv.set_id
WHERE ppv.version_number = 1;

INSERT INTO practice_test_versions (
    published_version_id, set_version_id, test_id, title, description, display_order, estimated_minutes
)
SELECT ppv.id, psv.id, t.id, t.title, t.description, t.display_order, t.estimated_minutes
FROM practice_published_versions ppv
JOIN practice_set_versions psv ON psv.published_version_id = ppv.id
JOIN practice_tests t ON t.set_id = ppv.set_id
WHERE ppv.version_number = 1;

INSERT INTO practice_section_versions (
    published_version_id, test_version_id, section_id, title, skill, section_type, instructions,
    duration_minutes, total_points, display_order
)
SELECT ppv.id, ptv.id, sec.id, sec.title, sec.skill, sec.section_type, sec.instructions,
       sec.duration_minutes, sec.total_points, sec.display_order
FROM practice_published_versions ppv
JOIN practice_test_versions ptv ON ptv.published_version_id = ppv.id
JOIN practice_sections sec ON sec.test_id = ptv.test_id
WHERE ppv.version_number = 1;

INSERT INTO practice_question_group_versions (
    published_version_id, section_version_id, group_id, group_label, question_from, question_to,
    instruction, audio_url, example_json, display_order
)
SELECT ppv.id, pscv.id, g.id, g.group_label, g.question_from, g.question_to,
       g.instruction, g.audio_url, g.example_json, g.display_order
FROM practice_published_versions ppv
JOIN practice_section_versions pscv ON pscv.published_version_id = ppv.id
JOIN practice_question_groups g ON g.section_id = pscv.section_id;

INSERT INTO practice_question_versions (
    published_version_id, section_version_id, group_version_id, question_id, question_no,
    question_type, prompt, options_json, answer_key, explanation, points, display_order, writing_task_type
)
SELECT ppv.id, pqgv.section_version_id, pqgv.id, q.id, q.question_no,
       q.question_type, q.prompt, q.options_json, q.answer_key, q.explanation,
       q.points, q.display_order, q.writing_task_type
FROM practice_published_versions ppv
JOIN practice_question_group_versions pqgv ON pqgv.published_version_id = ppv.id
JOIN practice_questions q ON q.group_id = pqgv.group_id;

INSERT INTO practice_question_versions (
    published_version_id, section_version_id, group_version_id, question_id, question_no,
    question_type, prompt, options_json, answer_key, explanation, points, display_order, writing_task_type
)
SELECT ppv.id, pscv.id, NULL, q.id, q.question_no,
       q.question_type, q.prompt, q.options_json, q.answer_key, q.explanation,
       q.points, q.display_order, q.writing_task_type
FROM practice_published_versions ppv
JOIN practice_test_versions ptv ON ptv.published_version_id = ppv.id
JOIN practice_section_versions pscv ON pscv.published_version_id = ppv.id
    AND pscv.test_version_id = ptv.id
JOIN practice_questions q ON q.set_id = ppv.set_id AND q.group_id IS NULL
WHERE ppv.version_number = 1
  AND (
      SELECT COUNT(*)
      FROM practice_sections sec_count
      WHERE sec_count.test_id = ptv.test_id
  ) = 1;

UPDATE practice_attempts a
JOIN practice_published_versions ppv ON ppv.set_id = a.set_id AND ppv.version_number = 1
JOIN practice_set_versions psv ON psv.published_version_id = ppv.id
JOIN practice_test_versions ptv ON ptv.published_version_id = ppv.id AND ptv.test_id = a.test_id
JOIN practice_section_versions pscv ON pscv.published_version_id = ppv.id AND pscv.section_id = a.section_id
SET a.published_version_id = ppv.id,
    a.set_version_id = psv.id,
    a.test_version_id = ptv.id,
    a.section_version_id = pscv.id,
    a.version_compatibility_status = 'BASELINE_MIGRATED',
    a.version_compatibility_note = 'Best-effort baseline created from current live content during Phase 9 migration.'
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_questions q_un
    WHERE q_un.set_id = ppv.set_id
      AND q_un.group_id IS NULL
      AND (
          SELECT COUNT(*)
          FROM practice_sections sec_count
          WHERE sec_count.test_id = ptv.test_id
      ) > 1
);

UPDATE practice_attempts a
JOIN practice_published_versions ppv ON ppv.set_id = a.set_id AND ppv.version_number = 1
JOIN practice_test_versions ptv ON ptv.published_version_id = ppv.id AND ptv.test_id = a.test_id
SET a.version_compatibility_status = 'LEGACY_UNGROUPED_UNSUPPORTED',
    a.version_compatibility_note = 'Legacy attempt kept on live fallback because null-group questions cannot be safely assigned to a section in a multi-section test.'
WHERE a.published_version_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM practice_questions q_un
      WHERE q_un.set_id = ppv.set_id
        AND q_un.group_id IS NULL
        AND (
            SELECT COUNT(*)
            FROM practice_sections sec_count
            WHERE sec_count.test_id = ptv.test_id
        ) > 1
  );
