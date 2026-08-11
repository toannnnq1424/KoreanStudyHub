-- Class lifecycle has exactly four durable business states:
-- PENDING -> ACTIVE | REJECTED, REJECTED -> PENDING, ACTIVE -> ARCHIVED.
-- V88 intentionally compacted historical scheduler states into DRAFT/ACTIVE/
-- ARCHIVED. This migration splits the overloaded DRAFT value without changing
-- any already-applied migration checksum.

SET @class_status_check := (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'classes'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%DRAFT%'
      AND cc.CHECK_CLAUSE LIKE '%ACTIVE%'
      AND cc.CHECK_CLAUSE LIKE '%ARCHIVED%'
    LIMIT 1
);
SET @drop_class_status_check := IF(
    @class_status_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE classes DROP CHECK `', @class_status_check, '`')
);
PREPARE drop_class_status_stmt FROM @drop_class_status_check;
EXECUTE drop_class_status_stmt;
DEALLOCATE PREPARE drop_class_status_stmt;

-- A DRAFT row that already carries a review decision is a historical rejection;
-- all other DRAFT rows are genuinely waiting for their first review.
UPDATE classes
SET status = CASE
    WHEN status = 'DRAFT'
         AND (approved_by IS NOT NULL OR approved_at IS NOT NULL
              OR NULLIF(TRIM(rejection_note), '') IS NOT NULL) THEN 'REJECTED'
    WHEN status IN ('DRAFT', 'UPCOMING') OR status IS NULL THEN 'PENDING'
    WHEN status = 'REJECTED' THEN 'REJECTED'
    WHEN status = 'ACTIVE' THEN 'ACTIVE'
    WHEN status IN ('ARCHIVED', 'COMPLETED', 'CANCELLED') THEN 'ARCHIVED'
    ELSE status
END;

ALTER TABLE classes
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD CONSTRAINT chk_classes_status
        CHECK (status IN ('PENDING','REJECTED','ACTIVE','ARCHIVED'));
