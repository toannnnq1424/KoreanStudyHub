-- =============================================================================
-- KSH — V41__subjects_activities.sql
-- Audit log table for administrative mutations on subjects.
-- Mirrors user_activities (V10):
--   - subject_id ON DELETE CASCADE
--   - performed_by ON DELETE SET NULL
-- =============================================================================

CREATE TABLE subjects_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    message TEXT NULL,
    metadata TEXT NULL,
    performed_by BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_subjects_activity_subject (subject_id),
    INDEX idx_dact_type (type),
    INDEX idx_dact_created (created_at),
    CONSTRAINT fk_subjects_activity_subject FOREIGN KEY (subject_id)
        REFERENCES subjects(id) ON DELETE CASCADE,
    CONSTRAINT fk_dact_actor FOREIGN KEY (performed_by)
        REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
