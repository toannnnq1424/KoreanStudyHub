-- The ACTIVE-class catalog/request flow has replaced invite tokens. Preserve
-- enrollment rows while severing and removing the token dependency.
SET @joined_via_check := (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'enrollments'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%joined_via%'
    LIMIT 1
);
SET @drop_joined_via_check := IF(
    @joined_via_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE enrollments DROP CHECK `', @joined_via_check, '`')
);
PREPARE drop_joined_via_stmt FROM @drop_joined_via_check;
EXECUTE drop_joined_via_stmt;
DEALLOCATE PREPARE drop_joined_via_stmt;

ALTER TABLE enrollments
    ADD CONSTRAINT chk_enrollments_joined_via
        CHECK (joined_via IN ('CODE','LINK','IMPORT','MANUAL','REQUEST'));

ALTER TABLE enrollments DROP FOREIGN KEY fk_enroll_invite;
ALTER TABLE enrollments DROP COLUMN invite_code_id;
DROP TABLE class_invite_codes;
