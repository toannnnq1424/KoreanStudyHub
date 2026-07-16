-- Phase 12R clean reduced-scope consolidation.
-- This squashed V25 replaces the unreleased V26-V29 migrations with the final
-- single KSH practice scope: no certificate/program governance tables, no
-- generic question-type policy tables, and no transient create-then-drop DDL.
-- It keeps the pieces still used by the application: immutable published
-- snapshots, set-level collaboration/locks/history, material access lifecycle,
-- PDF/Excel authoring targets, Reading/Listening AI explanations, Writing
-- Q51-Q54, and Speaking media/evaluation.

-- Immutable snapshots retain source IDs for traceability, but must not keep
-- foreign keys that prevent the live authoring graph from being replaced.
ALTER TABLE practice_section_versions
    DROP FOREIGN KEY fk_pscv_section;

ALTER TABLE practice_question_group_versions
    DROP FOREIGN KEY fk_pqgv_group;

ALTER TABLE practice_question_versions
    DROP FOREIGN KEY fk_pqv_question;

ALTER TABLE practice_test_versions
    DROP FOREIGN KEY fk_ptv_test;

ALTER TABLE practice_attempts
    DROP FOREIGN KEY fk_pa_test;

-- Final authoring/question payload contract. The reduced scope allows only
-- SINGLE_CHOICE, FILL_BLANK, TRUE_FALSE_NOT_GIVEN, ESSAY and SPEAKING after
-- the compatibility normalization below.
ALTER TABLE practice_questions
    DROP CHECK chk_pq_type,
    MODIFY question_type VARCHAR(40) NOT NULL,
    ADD COLUMN question_content_json JSON NULL AFTER options_json,
    ADD COLUMN answer_spec_json JSON NULL AFTER answer_key;

ALTER TABLE practice_question_versions
    MODIFY question_type VARCHAR(40) NOT NULL,
    ADD COLUMN question_content_json JSON NULL AFTER options_json,
    ADD COLUMN answer_spec_json JSON NULL AFTER answer_key;

UPDATE practice_question_versions qv
JOIN practice_questions q ON q.id = qv.question_id
SET qv.question_content_json = q.question_content_json,
    qv.answer_spec_json = q.answer_spec_json;

ALTER TABLE question_explanation_cache
    ADD COLUMN question_version_id BIGINT NULL AFTER question_id,
    ADD COLUMN stimulus_hash CHAR(64) NULL AFTER question_hash,
    ADD COLUMN answer_spec_hash CHAR(64) NULL AFTER stimulus_hash,
    ADD INDEX idx_qec_question_version (question_version_id),
    ADD CONSTRAINT fk_qec_question_version
        FOREIGN KEY (question_version_id) REFERENCES practice_question_versions(id);

-- Drafts now use the v3 manual/PDF/Excel structure without certificate or
-- template selection.
ALTER TABLE practice_drafts
    ADD COLUMN draft_schema_version VARCHAR(40) NULL AFTER creation_method;

UPDATE practice_drafts
SET draft_schema_version = 'practice-draft-v3'
WHERE draft_schema_version IS NULL;

-- Shared group stimuli are first-class so Reading passages, Listening
-- transcripts/audio and prompt images do not have to be duplicated per question.
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

-- Result rendering keeps earned points and percentage as separate fields.
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

-- PDF import targets a test/skill/lesson inside the single authoring flow.
ALTER TABLE practice_pdf_import_sessions
    ADD COLUMN target_test_no INT NULL AFTER exam_category,
    ADD COLUMN target_skill VARCHAR(20) NULL AFTER target_test_no,
    ADD COLUMN target_lesson_code VARCHAR(20) NULL AFTER target_skill,
    ADD INDEX idx_pdf_session_target (target_test_no, target_lesson_code);

-- Set-level lecturer collaboration and owner lock remain; Head/Admin program
-- governance is intentionally out of scope after reduction.
INSERT INTO permissions (feature_key, name, description, permission_group) VALUES
    ('practice.create', 'Tạo học liệu luyện tập', 'Tạo set hoặc draft luyện tập', 'PRACTICE'),
    ('practice.read', 'Xem học liệu quản trị', 'Xem học liệu sở hữu hoặc được chia sẻ', 'PRACTICE'),
    ('practice.edit', 'Sửa học liệu luyện tập', 'Sửa draft hoặc phiên bản mới', 'PRACTICE'),
    ('practice.publish', 'Xuất bản học liệu luyện tập', 'Tạo phiên bản xuất bản mới', 'PRACTICE'),
    ('practice.archive', 'Lưu trữ học liệu luyện tập', 'Archive hoặc unarchive học liệu', 'PRACTICE'),
    ('practice.lock', 'Khóa học liệu sở hữu', 'Bật hoặc tắt khóa của chủ sở hữu', 'PRACTICE'),
    ('practice.restore', 'Khôi phục phiên bản học liệu', 'Khởi tạo phiên bản mới từ lịch sử', 'PRACTICE'),
    ('practice.material.manage', 'Quản lý tài nguyên luyện tập', 'Tải lên, gắn và xóa material', 'PRACTICE');

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'LECTURER', id FROM permissions WHERE feature_key IN (
    'practice.create', 'practice.read', 'practice.edit', 'practice.publish',
    'practice.archive', 'practice.lock', 'practice.restore',
    'practice.material.manage'
);

ALTER TABLE practice_sets
    ADD COLUMN owner_locked TINYINT(1) NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN locked_by BIGINT NULL AFTER owner_locked,
    ADD COLUMN locked_at DATETIME NULL AFTER locked_by,
    ADD COLUMN archived_at DATETIME NULL AFTER locked_at,
    ADD INDEX idx_practice_set_owner_lock (created_by, owner_locked),
    ADD CONSTRAINT fk_practice_set_locked_by FOREIGN KEY (locked_by) REFERENCES users(id);

CREATE TABLE practice_authoring_collaborations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    collaborator_id BIGINT NOT NULL,
    granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at DATETIME NULL,
    CONSTRAINT uk_practice_collaboration UNIQUE (set_id, collaborator_id),
    CONSTRAINT fk_practice_collaboration_set
        FOREIGN KEY (set_id) REFERENCES practice_sets(id) ON DELETE CASCADE,
    CONSTRAINT fk_practice_collaboration_user
        FOREIGN KEY (collaborator_id) REFERENCES users(id),
    INDEX idx_practice_collaboration_user (collaborator_id, revoked_at),
    INDEX idx_practice_collaboration_set (set_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Private/published material identity plus durable lifecycle work queue.
ALTER TABLE lecturer_assets
    ADD COLUMN content_verified TINYINT(1) NOT NULL DEFAULT 0 AFTER mime_type,
    ADD COLUMN retention_until DATETIME NULL AFTER status,
    ADD INDEX idx_lecturer_assets_visibility_status (visibility, status, deleted_at);

UPDATE lecturer_assets
SET content_verified = 1
WHERE source_type = 'PDF_REGION'
  AND mime_type IN ('image/png', 'image/jpeg', 'image/gif', 'image/webp')
  AND sha256 IS NOT NULL;

CREATE TABLE practice_material_references (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    draft_id BIGINT NULL,
    set_id BIGINT NULL,
    published_version_id BIGINT NULL,
    reference_scope VARCHAR(30) NOT NULL,
    placement VARCHAR(64) NOT NULL DEFAULT 'MATERIAL',
    reference_key VARCHAR(255) NOT NULL DEFAULT '',
    reference_metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_practice_material_scope CHECK (
        (reference_scope = 'DRAFT' AND draft_id IS NOT NULL AND set_id IS NULL AND published_version_id IS NULL)
        OR (reference_scope = 'PUBLISHED_VERSION' AND draft_id IS NULL AND set_id IS NOT NULL AND published_version_id IS NOT NULL)
    ),
    CONSTRAINT fk_practice_material_asset FOREIGN KEY (asset_id) REFERENCES lecturer_assets(id),
    CONSTRAINT fk_practice_material_draft FOREIGN KEY (draft_id) REFERENCES practice_drafts(id) ON DELETE CASCADE,
    CONSTRAINT fk_practice_material_set FOREIGN KEY (set_id) REFERENCES practice_sets(id),
    CONSTRAINT fk_practice_material_version FOREIGN KEY (published_version_id) REFERENCES practice_published_versions(id),
    CONSTRAINT uk_practice_material_draft_ref
        UNIQUE (asset_id, reference_scope, draft_id, placement, reference_key),
    CONSTRAINT uk_practice_material_version_ref
        UNIQUE (asset_id, reference_scope, published_version_id, placement),
    INDEX idx_practice_material_draft (draft_id, asset_id),
    INDEX idx_practice_material_version (published_version_id, asset_id),
    INDEX idx_practice_material_asset (asset_id, reference_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO practice_material_references
    (asset_id, draft_id, reference_scope, placement, reference_key,
     reference_metadata_json, created_at)
SELECT u.asset_id,
       u.draft_id,
       'DRAFT',
       COALESCE(NULLIF(TRIM(u.placement), ''), 'MATERIAL'),
       CONCAT('legacy:', u.id),
       JSON_OBJECT(
           'sectionRef', u.section_temp_id,
           'groupRef', u.group_temp_id,
           'questionRef', u.question_temp_id,
           'displayOrder', u.display_order,
           'caption', u.caption,
           'altText', u.alt_text
       ),
       u.created_at
FROM practice_draft_asset_usages u;

CREATE TABLE practice_asset_lifecycle_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NULL,
    operation VARCHAR(40) NOT NULL,
    source_storage_key VARCHAR(512) NULL,
    target_storage_key VARCHAR(512) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_practice_asset_task_operation CHECK (operation IN ('DELETE','PROMOTE_CLEANUP','ORPHAN_RECONCILE')),
    CONSTRAINT chk_practice_asset_task_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    CONSTRAINT fk_practice_asset_task_asset FOREIGN KEY (asset_id) REFERENCES lecturer_assets(id),
    INDEX idx_practice_asset_task_due (status, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fail closed when existing authored content cannot be represented by the
-- five-type contract. Development databases may be reset/reseeded explicitly;
-- learner attempts and immutable history must never be silently rewritten.

-- V16 ships two development fixtures that predate the reduced contract. They
-- are recognizable by their exact seeded title and have no learner attempts on
-- a fresh install. Normalize only those fixtures; any other incompatible data
-- still fails the preflight below and requires an explicit operator decision.
UPDATE practice_questions q
JOIN practice_sets s ON s.id = q.set_id
SET q.question_no = 53,
    q.writing_task_type = 'Q53'
WHERE s.title = 'TOPIK II - Viết câu 53'
  AND q.question_type = 'ESSAY'
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

UPDATE practice_question_versions qv
JOIN practice_questions q ON q.id = qv.question_id
JOIN practice_sets s ON s.id = q.set_id
SET qv.question_no = 53,
    qv.writing_task_type = 'Q53'
WHERE s.title = 'TOPIK II - Viết câu 53'
  AND qv.question_type = 'ESSAY'
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

UPDATE practice_question_groups g
JOIN practice_sets s ON s.id = g.set_id
SET g.question_from = 53,
    g.question_to = 53
WHERE s.title = 'TOPIK II - Viết câu 53'
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

UPDATE practice_question_group_versions gv
JOIN practice_set_versions sv ON sv.published_version_id = gv.published_version_id
JOIN practice_sets s ON s.id = sv.set_id
SET gv.question_from = 53,
    gv.question_to = 53
WHERE s.title = 'TOPIK II - Viết câu 53'
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

DELETE qv
FROM practice_question_versions qv
JOIN practice_questions q ON q.id = qv.question_id
JOIN practice_sets s ON s.id = q.set_id
WHERE s.title = 'Đọc hiểu nâng cao (True/False & Matching)'
  AND qv.question_type IN ('MATCHING_INFORMATION', 'ORDERING', 'TEXT_COMPLETION', 'SHORT_TEXT')
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

DELETE q
FROM practice_questions q
JOIN practice_sets s ON s.id = q.set_id
WHERE s.title = 'Đọc hiểu nâng cao (True/False & Matching)'
  AND q.question_type IN ('MATCHING_INFORMATION', 'ORDERING', 'TEXT_COMPLETION', 'SHORT_TEXT')
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

UPDATE practice_questions q
JOIN practice_sets s ON s.id = q.set_id
SET q.question_no = CASE q.question_type
        WHEN 'TRUE_FALSE_NOT_GIVEN' THEN 1
        WHEN 'FILL_BLANK' THEN 2
        ELSE q.question_no
    END,
    q.display_order = CASE q.question_type
        WHEN 'TRUE_FALSE_NOT_GIVEN' THEN 0
        WHEN 'FILL_BLANK' THEN 1
        ELSE q.display_order
    END
WHERE s.title = 'Đọc hiểu nâng cao (True/False & Matching)'
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

UPDATE practice_question_versions qv
JOIN practice_questions q ON q.id = qv.question_id
JOIN practice_sets s ON s.id = q.set_id
SET qv.question_no = q.question_no,
    qv.display_order = q.display_order
WHERE s.title = 'Đọc hiểu nâng cao (True/False & Matching)'
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

UPDATE practice_question_groups g
JOIN practice_sets s ON s.id = g.set_id
SET g.question_from = 1,
    g.question_to = 2
WHERE s.title = 'Đọc hiểu nâng cao (True/False & Matching)'
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

UPDATE practice_question_group_versions gv
JOIN practice_set_versions sv ON sv.published_version_id = gv.published_version_id
JOIN practice_sets s ON s.id = sv.set_id
SET gv.question_from = 1,
    gv.question_to = 2
WHERE s.title = 'Đọc hiểu nâng cao (True/False & Matching)'
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

-- The V16 sample catalog still advertises certificate labels and removed
-- question types. Normalize only those exact fixtures. Immutable sample
-- snapshots are changed only when no learner attempt references their set.
UPDATE practice_test_versions tv
JOIN practice_set_versions sv ON sv.id = tv.set_version_id
JOIN practice_sets s ON s.id = sv.set_id
SET tv.title = CASE tv.title
        WHEN 'TOPIK I - Luyện đọc nhanh — Full Test' THEN 'Bài kiểm tra đọc nhanh'
        WHEN 'TOPIK I - Luyện nghe hội thoại — Full Test' THEN 'Bài kiểm tra nghe hội thoại'
        WHEN 'TOPIK II - Viết câu 53 — Full Test' THEN 'Bài viết câu 53'
        WHEN 'TOPIK II - Phòng luyện nói cá nhân — Full Test' THEN 'Bài luyện nói cá nhân'
        WHEN 'Đọc hiểu nâng cao (True/False & Matching) — Full Test' THEN 'Bài đọc tổng hợp'
        WHEN 'Luyện nói nâng cao (Speaking Tasks) — Full Test' THEN 'Bài luyện nói nâng cao'
        ELSE tv.title
    END,
    tv.description = CASE tv.description
        WHEN 'Bài viết câu 53 TOPIK II' THEN 'Bài luyện viết câu 53'
        WHEN 'Bài luyện nói TOPIK II tự luận' THEN 'Bài luyện nói tự luận'
        WHEN 'Bài thi đọc tổng hợp nhiều dạng câu hỏi' THEN 'Bài đọc với các dạng câu hỏi thuộc phạm vi KSH'
        ELSE tv.description
    END
WHERE tv.title IN (
        'TOPIK I - Luyện đọc nhanh — Full Test',
        'TOPIK I - Luyện nghe hội thoại — Full Test',
        'TOPIK II - Viết câu 53 — Full Test',
        'TOPIK II - Phòng luyện nói cá nhân — Full Test',
        'Đọc hiểu nâng cao (True/False & Matching) — Full Test',
        'Luyện nói nâng cao (Speaking Tasks) — Full Test'
    )
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

UPDATE practice_set_versions sv
JOIN practice_sets s ON s.id = sv.set_id
SET sv.title = CASE sv.title
        WHEN 'TOPIK I - Luyện đọc nhanh' THEN 'Luyện đọc nhanh'
        WHEN 'TOPIK I - Luyện nghe hội thoại' THEN 'Luyện nghe hội thoại'
        WHEN 'TOPIK II - Viết câu 53' THEN 'Luyện viết câu 53'
        WHEN 'TOPIK II - Phòng luyện nói cá nhân' THEN 'Phòng luyện nói cá nhân'
        WHEN 'Đọc hiểu nâng cao (True/False & Matching)' THEN 'Đọc hiểu tổng hợp'
        WHEN 'Luyện nói nâng cao (Speaking Tasks)' THEN 'Luyện nói nâng cao'
        ELSE sv.title
    END,
    sv.description = CASE sv.description
        WHEN 'Bộ luyện nói mô phỏng: học sinh trả lời theo gợi ý, hệ thống trả feedback mock trước khi nối chấm phát âm thật.' THEN 'Bộ luyện nói để học sinh trả lời theo gợi ý và nhận phản hồi.'
        WHEN 'Bài tập đọc hiểu tổng hợp chứa các dạng câu hỏi True/False/Not Given, Matching Information, Điền từ và Sắp xếp.' THEN 'Bài đọc tổng hợp với đúng/sai/không có thông tin và điền từ.'
        ELSE sv.description
    END,
    sv.metadata_json = CASE
        WHEN sv.metadata_json IS NOT NULL
            THEN JSON_REMOVE(sv.metadata_json, '$.extended_types')
        ELSE sv.metadata_json
    END
WHERE sv.title IN (
        'TOPIK I - Luyện đọc nhanh',
        'TOPIK I - Luyện nghe hội thoại',
        'TOPIK II - Viết câu 53',
        'TOPIK II - Phòng luyện nói cá nhân',
        'Đọc hiểu nâng cao (True/False & Matching)',
        'Luyện nói nâng cao (Speaking Tasks)'
    )
  AND NOT EXISTS (SELECT 1 FROM practice_attempts a WHERE a.set_id = s.id);

UPDATE practice_tests
SET title = CASE title
        WHEN 'TOPIK I - Luyện đọc nhanh — Full Test' THEN 'Bài kiểm tra đọc nhanh'
        WHEN 'TOPIK I - Luyện nghe hội thoại — Full Test' THEN 'Bài kiểm tra nghe hội thoại'
        WHEN 'TOPIK II - Viết câu 53 — Full Test' THEN 'Bài viết câu 53'
        WHEN 'TOPIK II - Phòng luyện nói cá nhân — Full Test' THEN 'Bài luyện nói cá nhân'
        WHEN 'Đọc hiểu nâng cao (True/False & Matching) — Full Test' THEN 'Bài đọc tổng hợp'
        WHEN 'Luyện nói nâng cao (Speaking Tasks) — Full Test' THEN 'Bài luyện nói nâng cao'
        ELSE title
    END,
    description = CASE description
        WHEN 'Bài viết câu 53 TOPIK II' THEN 'Bài luyện viết câu 53'
        WHEN 'Bài luyện nói TOPIK II tự luận' THEN 'Bài luyện nói tự luận'
        WHEN 'Bài thi đọc tổng hợp nhiều dạng câu hỏi' THEN 'Bài đọc với các dạng câu hỏi thuộc phạm vi KSH'
        ELSE description
    END
WHERE title IN (
        'TOPIK I - Luyện đọc nhanh — Full Test',
        'TOPIK I - Luyện nghe hội thoại — Full Test',
        'TOPIK II - Viết câu 53 — Full Test',
        'TOPIK II - Phòng luyện nói cá nhân — Full Test',
        'Đọc hiểu nâng cao (True/False & Matching) — Full Test',
        'Luyện nói nâng cao (Speaking Tasks) — Full Test'
    );

UPDATE practice_sets
SET title = CASE title
        WHEN 'TOPIK I - Luyện đọc nhanh' THEN 'Luyện đọc nhanh'
        WHEN 'TOPIK I - Luyện nghe hội thoại' THEN 'Luyện nghe hội thoại'
        WHEN 'TOPIK II - Viết câu 53' THEN 'Luyện viết câu 53'
        WHEN 'TOPIK II - Phòng luyện nói cá nhân' THEN 'Phòng luyện nói cá nhân'
        WHEN 'Đọc hiểu nâng cao (True/False & Matching)' THEN 'Đọc hiểu tổng hợp'
        WHEN 'Luyện nói nâng cao (Speaking Tasks)' THEN 'Luyện nói nâng cao'
        ELSE title
    END,
    description = CASE description
        WHEN 'Bộ luyện nói mô phỏng: học sinh trả lời theo gợi ý, hệ thống trả feedback mock trước khi nối chấm phát âm thật.' THEN 'Bộ luyện nói để học sinh trả lời theo gợi ý và nhận phản hồi.'
        WHEN 'Bài tập đọc hiểu tổng hợp chứa các dạng câu hỏi True/False/Not Given, Matching Information, Điền từ và Sắp xếp.' THEN 'Bài đọc tổng hợp với đúng/sai/không có thông tin và điền từ.'
        ELSE description
    END,
    metadata_json = CASE
        WHEN metadata_json IS NOT NULL
            THEN JSON_REMOVE(metadata_json, '$.extended_types')
        ELSE metadata_json
    END
WHERE title IN (
        'TOPIK I - Luyện đọc nhanh',
        'TOPIK I - Luyện nghe hội thoại',
        'TOPIK II - Viết câu 53',
        'TOPIK II - Phòng luyện nói cá nhân',
        'Đọc hiểu nâng cao (True/False & Matching)',
        'Luyện nói nâng cao (Speaking Tasks)'
    );

CREATE TEMPORARY TABLE practice_v25_preflight (
    invalid_count BIGINT NOT NULL,
    CONSTRAINT chk_practice_v25_preflight CHECK (invalid_count = 0)
);

INSERT INTO practice_v25_preflight (invalid_count)
SELECT COUNT(*)
FROM (
    SELECT question_no, question_type, writing_task_type
    FROM practice_questions
    UNION ALL
    SELECT question_no, question_type, writing_task_type
    FROM practice_question_versions
) q
WHERE q.question_type NOT IN (
        'MCQ', 'MCQ_SINGLE', 'SINGLE_CHOICE',
        'GAP_FILL', 'FILL_BLANK',
        'TRUE_FALSE_NOT_GIVEN', 'ESSAY', 'SPEAKING'
    )
   OR (q.question_type = 'ESSAY'
       AND (q.writing_task_type IS NULL
            OR q.writing_task_type NOT IN ('Q51', 'Q52', 'Q53', 'Q54')
            OR q.question_no <> CAST(SUBSTRING(q.writing_task_type, 2) AS UNSIGNED)));

DROP TEMPORARY TABLE practice_v25_preflight;

UPDATE practice_questions
SET question_type = CASE question_type
        WHEN 'MCQ' THEN 'SINGLE_CHOICE'
        WHEN 'MCQ_SINGLE' THEN 'SINGLE_CHOICE'
        WHEN 'GAP_FILL' THEN 'FILL_BLANK'
        ELSE question_type
    END;

UPDATE practice_question_versions
SET question_type = CASE question_type
        WHEN 'MCQ' THEN 'SINGLE_CHOICE'
        WHEN 'MCQ_SINGLE' THEN 'SINGLE_CHOICE'
        WHEN 'GAP_FILL' THEN 'FILL_BLANK'
        ELSE question_type
    END;

-- Writing task identity is meaningful only for ESSAY. Clear stale metadata
-- before adding the reduced-scope constraints to both graphs.
UPDATE practice_questions
SET writing_task_type = NULL
WHERE question_type <> 'ESSAY';

UPDATE practice_question_versions
SET writing_task_type = NULL
WHERE question_type <> 'ESSAY';



ALTER TABLE practice_questions
    ADD CONSTRAINT chk_pq_type CHECK (question_type IN (
        'SINGLE_CHOICE', 'FILL_BLANK', 'TRUE_FALSE_NOT_GIVEN',
        'ESSAY', 'SPEAKING'
    )),
    ADD CONSTRAINT chk_pq_writing_task CHECK (
        (question_type <> 'ESSAY' AND writing_task_type IS NULL)
        OR (question_type = 'ESSAY'
            AND question_no IN (51, 52, 53, 54)
            AND writing_task_type IS NOT NULL
            AND writing_task_type IN ('Q51', 'Q52', 'Q53', 'Q54')
            AND question_no = CAST(SUBSTRING(writing_task_type, 2) AS UNSIGNED))
    );

ALTER TABLE practice_question_versions
    ADD CONSTRAINT chk_pqv_type CHECK (question_type IN (
        'SINGLE_CHOICE', 'FILL_BLANK', 'TRUE_FALSE_NOT_GIVEN',
        'ESSAY', 'SPEAKING'
    )),
    ADD CONSTRAINT chk_pqv_writing_task CHECK (
        (question_type <> 'ESSAY' AND writing_task_type IS NULL)
        OR (question_type = 'ESSAY'
            AND question_no IN (51, 52, 53, 54)
            AND writing_task_type IS NOT NULL
            AND writing_task_type IN ('Q51', 'Q52', 'Q53', 'Q54')
            AND question_no = CAST(SUBSTRING(writing_task_type, 2) AS UNSIGNED))
    );

-- Remove legacy tables/columns that no longer have production code after the
-- reduction. Shared global RBAC tables stay in place.
ALTER TABLE practice_pdf_import_sessions
    DROP COLUMN exam_category;

ALTER TABLE practice_drafts
    DROP COLUMN category;

ALTER TABLE practice_sets
    DROP COLUMN topik_level;

ALTER TABLE practice_set_versions
    DROP COLUMN topik_level;

ALTER TABLE practice_attempts
    DROP COLUMN analysis_usage_id;

DROP TABLE practice_draft_asset_usages;
DROP TABLE practice_submissions;
DROP TABLE practice_ai_analysis_usage;
