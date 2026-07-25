-- Standardize the internal subject-leader actor as LEADER.
-- Historical migrations intentionally retain their original values; this
-- forward-only migration upgrades both fresh and existing databases.

-- The original users role CHECK only accepts the legacy code. Its generated
-- constraint name can vary, so discover and remove it dynamically.
SET @users_role_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'users'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%HEAD%'
    LIMIT 1
);
SET @drop_users_role_check = IF(
    @users_role_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE users DROP CHECK `', @users_role_check, '`')
);
PREPARE drop_users_role_check_stmt FROM @drop_users_role_check;
EXECUTE drop_users_role_check_stmt;
DEALLOCATE PREPARE drop_users_role_check_stmt;

-- Create the canonical role before moving any foreign-key references.
INSERT INTO roles (code, name, description, is_system, priority)
SELECT 'LEADER', name, description, is_system, priority
FROM roles
WHERE code = 'HEAD'
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    is_system = VALUES(is_system),
    priority = VALUES(priority);

INSERT IGNORE INTO role_permissions (role_code, permission_id)
SELECT 'LEADER', permission_id
FROM role_permissions
WHERE role_code = 'HEAD';

INSERT IGNORE INTO role_hierarchy (parent_role_code, child_role_code)
SELECT
    CASE WHEN parent_role_code = 'HEAD' THEN 'LEADER' ELSE parent_role_code END,
    CASE WHEN child_role_code = 'HEAD' THEN 'LEADER' ELSE child_role_code END
FROM role_hierarchy
WHERE parent_role_code = 'HEAD' OR child_role_code = 'HEAD';

UPDATE users
SET role = 'LEADER'
WHERE role = 'HEAD';

UPDATE users
SET email = 'leader@ksh.edu.vn'
WHERE email = 'head@ksh.edu.vn'
  AND NOT EXISTS (
      SELECT 1
      FROM (SELECT email FROM users) existing_users
      WHERE existing_users.email = 'leader@ksh.edu.vn'
  );

DELETE FROM role_hierarchy
WHERE parent_role_code = 'HEAD' OR child_role_code = 'HEAD';

DELETE FROM role_permissions
WHERE role_code = 'HEAD';

DELETE FROM roles
WHERE code = 'HEAD';

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
    CHECK (role IN ('STUDENT', 'LECTURER', 'LEADER', 'ADMIN'));

-- Align the organization ownership column and its database object names.
ALTER TABLE departments
    DROP FOREIGN KEY fk_dept_head,
    DROP INDEX idx_dept_head,
    CHANGE COLUMN head_user_id leader_user_id BIGINT NULL,
    ADD INDEX idx_dept_leader (leader_user_id),
    ADD CONSTRAINT fk_dept_leader
        FOREIGN KEY (leader_user_id) REFERENCES users(id) ON DELETE SET NULL;

UPDATE department_activities
SET type = CASE type
    WHEN 'HEAD_ASSIGNED' THEN 'LEADER_ASSIGNED'
    WHEN 'HEAD_CLEARED' THEN 'LEADER_CLEARED'
    ELSE type
END
WHERE type IN ('HEAD_ASSIGNED', 'HEAD_CLEARED');
