-- Existing classes remain operational. Only newly-created classes start in DRAFT.
SET @status_chk := (
    SELECT cc.CONSTRAINT_NAME
    FROM information_schema.CHECK_CONSTRAINTS cc
    JOIN information_schema.TABLE_CONSTRAINTS tc
      ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
     AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
    WHERE cc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'classes'
      AND cc.CHECK_CLAUSE LIKE '%UPCOMING%ACTIVE%COMPLETED%CANCELLED%'
      AND cc.CHECK_CLAUSE NOT LIKE '%DRAFT%'
    LIMIT 1
);
SET @drop_sql := IF(@status_chk IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE classes DROP CHECK `', @status_chk, '`'));
PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE classes
    ADD CONSTRAINT chk_classes_status
        CHECK (status IN ('DRAFT','UPCOMING','ACTIVE','COMPLETED','CANCELLED','REJECTED')),
    ADD COLUMN approved_by BIGINT NULL AFTER is_deleted,
    ADD COLUMN approved_at DATETIME NULL AFTER approved_by,
    ADD COLUMN rejection_note VARCHAR(500) NULL AFTER approved_at,
    ADD CONSTRAINT fk_class_approver FOREIGN KEY (approved_by) REFERENCES users(id),
    ADD INDEX idx_classes_department_status_created (department_id, status, created_at);
