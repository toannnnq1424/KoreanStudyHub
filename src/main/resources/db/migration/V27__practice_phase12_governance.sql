-- Phase 11I PDF destination identity plus the unreleased Phase 12 governance
-- contract. V26 is already published, so all new schema remains forward-only.

ALTER TABLE practice_pdf_import_sessions
    ADD COLUMN assessment_program_code VARCHAR(40) NULL AFTER exam_category,
    ADD COLUMN assessment_program_version_id BIGINT NULL AFTER assessment_program_code,
    ADD COLUMN exam_template_code VARCHAR(80) NULL AFTER assessment_program_version_id,
    ADD COLUMN target_test_no INT NULL AFTER exam_template_code,
    ADD COLUMN target_skill VARCHAR(20) NULL AFTER target_test_no,
    ADD COLUMN target_lesson_code VARCHAR(20) NULL AFTER target_skill,
    ADD INDEX idx_pdf_session_program_template (assessment_program_code, exam_template_code),
    ADD INDEX idx_pdf_session_target (target_test_no, target_lesson_code),
    ADD CONSTRAINT fk_pdf_session_program
        FOREIGN KEY (assessment_program_code) REFERENCES assessment_programs(code),
    ADD CONSTRAINT fk_pdf_session_program_version
        FOREIGN KEY (assessment_program_version_id) REFERENCES assessment_program_versions(id),
    ADD CONSTRAINT fk_pdf_session_exam_template
        FOREIGN KEY (exam_template_code) REFERENCES assessment_exam_templates(code);

UPDATE practice_pdf_import_sessions s
JOIN assessment_exam_templates t
  ON t.code = CASE
      WHEN UPPER(COALESCE(s.exam_category, '')) = 'TOPIK_I' THEN 'TOPIK_I'
      WHEN UPPER(COALESCE(s.exam_category, '')) IN ('TOPIK_II', 'TOPIK_MIXED') THEN 'TOPIK_II'
      ELSE 'CUSTOM_FLEXIBLE'
  END
JOIN assessment_program_versions v ON v.id = t.program_version_id
SET s.assessment_program_code = v.program_code,
    s.assessment_program_version_id = v.id,
    s.exam_template_code = t.code
WHERE s.exam_template_code IS NULL;

-- Version-locked attempts read immutable test/section versions. Keep the live
-- IDs as compatibility trace data without letting them block graph replacement.
ALTER TABLE practice_attempts
    DROP FOREIGN KEY fk_pa_test;

-- 12A: canonical action permissions. Role grants are defaults; user REVOKE
-- overrides in v_user_effective_permissions retain deny precedence.
INSERT INTO permissions (feature_key, name, description, permission_group) VALUES
    ('practice.create', 'Tạo học liệu luyện tập', 'Tạo set hoặc draft luyện tập', 'PRACTICE'),
    ('practice.read', 'Xem học liệu quản trị', 'Xem học liệu sở hữu hoặc được chia sẻ', 'PRACTICE'),
    ('practice.edit', 'Sửa học liệu luyện tập', 'Sửa draft hoặc phiên bản mới', 'PRACTICE'),
    ('practice.publish', 'Xuất bản học liệu luyện tập', 'Tạo phiên bản xuất bản mới', 'PRACTICE'),
    ('practice.archive', 'Lưu trữ học liệu luyện tập', 'Archive hoặc unarchive học liệu', 'PRACTICE'),
    ('practice.lock', 'Khóa học liệu sở hữu', 'Bật hoặc tắt khóa của chủ sở hữu', 'PRACTICE'),
    ('practice.restore', 'Khôi phục phiên bản học liệu', 'Khởi tạo phiên bản mới từ lịch sử', 'PRACTICE'),
    ('practice.material.manage', 'Quản lý tài nguyên luyện tập', 'Tải lên, gắn và xóa material', 'PRACTICE'),
    ('practice.media.review', 'Nghe media phục vụ review', 'Phát media riêng tư khi review', 'PRACTICE'),
    ('practice.governance.manage', 'Quản trị cấu hình chứng chỉ', 'Quản trị program, template và profile', 'PRACTICE'),
    ('practice.override', 'Override khẩn cấp học liệu', 'Bỏ qua owner lock với reason và audit', 'PRACTICE');

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'LECTURER', id FROM permissions WHERE feature_key IN (
    'practice.create', 'practice.read', 'practice.edit', 'practice.publish',
    'practice.archive', 'practice.lock', 'practice.restore',
    'practice.material.manage'
);

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'HEAD', id FROM permissions WHERE feature_key IN (
    'practice.media.review', 'practice.governance.manage', 'practice.override'
);

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'ADMIN', id FROM permissions WHERE feature_key IN (
    'practice.media.review', 'practice.governance.manage', 'practice.override'
);

ALTER TABLE practice_sets
    ADD COLUMN owner_locked TINYINT(1) NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN locked_by BIGINT NULL AFTER owner_locked,
    ADD COLUMN locked_at DATETIME NULL AFTER locked_by,
    ADD COLUMN archived_at DATETIME NULL AFTER locked_at,
    ADD INDEX idx_practice_set_owner_lock (created_by, owner_locked),
    ADD CONSTRAINT fk_practice_set_locked_by FOREIGN KEY (locked_by) REFERENCES users(id);

ALTER TABLE practice_drafts
    ADD COLUMN owner_locked TINYINT(1) NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN locked_by BIGINT NULL AFTER owner_locked,
    ADD COLUMN locked_at DATETIME NULL AFTER locked_by,
    ADD INDEX idx_practice_draft_owner_lock (owner_id, owner_locked),
    ADD CONSTRAINT fk_practice_draft_locked_by FOREIGN KEY (locked_by) REFERENCES users(id);

CREATE TABLE practice_authoring_collaborations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    collaborator_id BIGINT NOT NULL,
    can_edit TINYINT(1) NOT NULL DEFAULT 1,
    can_publish TINYINT(1) NOT NULL DEFAULT 1,
    can_restore TINYINT(1) NOT NULL DEFAULT 1,
    can_manage_material TINYINT(1) NOT NULL DEFAULT 1,
    granted_by BIGINT NOT NULL,
    granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at DATETIME NULL,
    CONSTRAINT uk_practice_collaboration UNIQUE (target_type, target_id, collaborator_id),
    CONSTRAINT chk_practice_collaboration_target CHECK (target_type IN ('SET','DRAFT')),
    CONSTRAINT chk_practice_collaboration_distinct CHECK (owner_id <> collaborator_id),
    CONSTRAINT fk_practice_collaboration_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_practice_collaboration_user FOREIGN KEY (collaborator_id) REFERENCES users(id),
    CONSTRAINT fk_practice_collaboration_grantor FOREIGN KEY (granted_by) REFERENCES users(id),
    INDEX idx_practice_collaboration_user (collaborator_id, revoked_at),
    INDEX idx_practice_collaboration_target (target_type, target_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_governance_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action_code VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NULL,
    owner_id BIGINT NULL,
    actor_id BIGINT NOT NULL,
    source_version_id BIGINT NULL,
    override_used TINYINT(1) NOT NULL DEFAULT 0,
    reason VARCHAR(500) NULL,
    before_json LONGTEXT NULL,
    after_json LONGTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_practice_governance_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_practice_governance_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT fk_practice_governance_source_version
        FOREIGN KEY (source_version_id) REFERENCES practice_published_versions(id),
    INDEX idx_practice_governance_target (target_type, target_id, created_at),
    INDEX idx_practice_governance_actor (actor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12C: assessment_exam_templates keeps its compatibility config while the
-- active pointer resolves an immutable version row.
CREATE TABLE assessment_exam_template_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(80) NOT NULL,
    version_number INT NOT NULL,
    config_json JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NULL,
    activated_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at DATETIME NULL,
    CONSTRAINT uk_aetv_template_version UNIQUE (template_code, version_number),
    CONSTRAINT chk_aetv_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    CONSTRAINT fk_aetv_template FOREIGN KEY (template_code) REFERENCES assessment_exam_templates(code),
    CONSTRAINT fk_aetv_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_aetv_activator FOREIGN KEY (activated_by) REFERENCES users(id),
    INDEX idx_aetv_template_status (template_code, status, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE assessment_exam_templates
    ADD COLUMN active_version_id BIGINT NULL AFTER config_json,
    ADD CONSTRAINT fk_aet_active_version
        FOREIGN KEY (active_version_id) REFERENCES assessment_exam_template_versions(id);

INSERT INTO assessment_exam_template_versions
    (template_code, version_number, config_json, status)
SELECT code, 1, config_json, 'ACTIVE'
FROM assessment_exam_templates;

UPDATE assessment_exam_templates t
JOIN assessment_exam_template_versions v
  ON v.template_code = t.code AND v.version_number = 1
SET t.active_version_id = v.id;

ALTER TABLE assessment_scoring_profiles
    ADD COLUMN governance_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER enabled,
    ADD COLUMN created_by BIGINT NULL AFTER governance_status,
    ADD COLUMN activated_at DATETIME NULL AFTER created_by,
    ADD CONSTRAINT fk_assessment_scoring_creator FOREIGN KEY (created_by) REFERENCES users(id);

ALTER TABLE assessment_prompt_profiles
    ADD COLUMN governance_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER enabled,
    ADD COLUMN created_by BIGINT NULL AFTER governance_status,
    ADD COLUMN activated_at DATETIME NULL AFTER created_by,
    ADD CONSTRAINT fk_assessment_prompt_creator FOREIGN KEY (created_by) REFERENCES users(id);

ALTER TABLE assessment_rubric_profiles
    ADD COLUMN governance_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER enabled,
    ADD COLUMN created_by BIGINT NULL AFTER governance_status,
    ADD COLUMN activated_at DATETIME NULL AFTER created_by,
    ADD CONSTRAINT fk_assessment_rubric_creator FOREIGN KEY (created_by) REFERENCES users(id);

-- 12D: private/published material identity and durable lifecycle work queue.
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
        UNIQUE (asset_id, reference_scope, draft_id, placement),
    CONSTRAINT uk_practice_material_version_ref
        UNIQUE (asset_id, reference_scope, published_version_id, placement),
    INDEX idx_practice_material_draft (draft_id, asset_id),
    INDEX idx_practice_material_version (published_version_id, asset_id),
    INDEX idx_practice_material_asset (asset_id, reference_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
