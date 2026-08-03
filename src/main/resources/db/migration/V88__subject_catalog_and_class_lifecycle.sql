-- Non-Practice subject/class foundation. `departments` remains the physical
-- table during the compatibility window, but its rows are the canonical
-- subject catalog for class and Question Bank scope.

INSERT INTO departments (name, code, description, is_active)
SELECT 'Tiếng Hàn 3.1.1', 'KOR311', 'Mã môn tiếng Hàn KOR311', 1
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE code = 'KOR311');

INSERT INTO departments (name, code, description, is_active)
SELECT 'Tiếng Hàn 3.2.1', 'KOR321', 'Mã môn tiếng Hàn KOR321', 1
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE code = 'KOR321');

INSERT INTO departments (name, code, description, is_active)
SELECT 'Tiếng Hàn 4.1.1', 'KOR411', 'Mã môn tiếng Hàn KOR411', 1
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE code = 'KOR411');

UPDATE classes SET status = 'ACTIVE' WHERE status IN ('UPCOMING', 'ACTIVE');
UPDATE classes SET status = 'ARCHIVED' WHERE status IN ('COMPLETED', 'CANCELLED');
UPDATE classes SET status = 'DRAFT' WHERE status IN ('DRAFT', 'REJECTED');

SET @class_status_check := (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'classes'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%UPCOMING%'
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

ALTER TABLE classes
    ADD CONSTRAINT chk_classes_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'));
