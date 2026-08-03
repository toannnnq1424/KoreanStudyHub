-- Non-Practice subject/class foundation. `subjects` remains the physical
-- table during the compatibility window, but its rows are the canonical
-- subject catalog for class and Question Bank scope.

INSERT INTO subjects (name, code, description, is_active) VALUES
    ('Intermediate Korean Language 1 — Hàn ngữ trung cấp 1', 'KOR311',
     'Chương trình Kỹ sư cầu nối Hàn Quốc', 1),
    ('Intermediate Korean Language 2 — Hàn ngữ trung cấp 2', 'KOR321',
     'Chương trình Kỹ sư cầu nối Hàn Quốc', 1),
    ('Intermediate Korean Language 3 — Hàn ngữ trung cấp 3', 'KOR411',
     'Chương trình Kỹ sư cầu nối Hàn Quốc', 1),
    ('Elementary Korean 1 — Tiếng Hàn sơ cấp 1', 'KRL112',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Elementary Korean 2 — Tiếng Hàn sơ cấp 2', 'KRL122',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Elementary Korean 3 — Tiếng Hàn sơ cấp 3', 'KRL212',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Elementary Korean 4 — Tiếng Hàn sơ cấp 4', 'KRL222',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Intermediate Korean 1 — Tiếng Hàn trung cấp 1', 'KRL312',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Intermediate Korean 2 — Tiếng Hàn trung cấp 2', 'KRL322',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Intermediate Korean 3 — Tiếng Hàn trung cấp 3', 'KRL402',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Intermediate Korean 3 — Tiếng Hàn trung cấp 3', 'KRL411',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Intermediate Korean 4 — Tiếng Hàn trung cấp 4', 'KRL421',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Advanced Korean 1 — Tiếng Hàn cao cấp 1', 'KRL502',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Advanced Korean 1 — Tiếng Hàn cao cấp 1', 'KRL511',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Advanced Korean 2 — Tiếng Hàn cao cấp 2', 'KRL521',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Elementary Korean 1 — Tiếng Hàn sơ cấp 1', 'KRL101',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Elementary Korean 2 — Tiếng Hàn sơ cấp 2', 'KRL201',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Intermediate Korean 1 — Tiếng Hàn trung cấp 1', 'KRL311',
     'Chương trình Ngôn ngữ Hàn', 1),
    ('Intermediate Korean 2 — Tiếng Hàn trung cấp 2', 'KRL321',
     'Chương trình Ngôn ngữ Hàn', 1) AS incoming
ON DUPLICATE KEY UPDATE
    name = incoming.name,
    description = incoming.description,
    is_active = incoming.is_active;

-- The unreleased fresh catalog contains only the Korean subjects approved for
-- this product slice. Legacy sample rows (CNTT/CK/DDT/...) remain physically
-- available for compatibility, but must not appear in active subject pickers.
UPDATE subjects
SET is_active = 0
WHERE code NOT IN (
    'KOR311', 'KOR321', 'KOR411',
    'KRL101', 'KRL112', 'KRL122', 'KRL201', 'KRL212', 'KRL222',
    'KRL311', 'KRL312', 'KRL321', 'KRL322', 'KRL402', 'KRL411',
    'KRL421', 'KRL502', 'KRL511', 'KRL521'
);

-- Fresh demo accounts must open a real Korean-subject Library instead of the
-- legacy CNTT seed. This affects local/demo data only; production user rows are
-- not addressed by email in this unreleased migration chain.
UPDATE users
SET subject_id = (SELECT id FROM subjects WHERE code = 'KOR311' LIMIT 1)
WHERE email IN ('lecturer@ksh.edu.vn', 'leader@ksh.edu.vn');

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
