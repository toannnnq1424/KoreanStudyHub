-- Question Bank is scoped directly by subject; category identity is removed.
ALTER TABLE question_bank_items DROP FOREIGN KEY fk_qbi_category;
ALTER TABLE question_bank_items DROP INDEX idx_qbi_department_category;
ALTER TABLE question_bank_items DROP FOREIGN KEY fk_qbi_department;
ALTER TABLE question_bank_items RENAME COLUMN department_id TO subject_id;
ALTER TABLE question_bank_items DROP COLUMN category_id;
ALTER TABLE question_bank_items
    RENAME INDEX idx_qbi_department_status TO idx_qbi_subject_status,
    ADD CONSTRAINT fk_qbi_subject FOREIGN KEY (subject_id)
        REFERENCES departments(id) ON DELETE CASCADE;

DELETE rp FROM role_permissions rp
JOIN permissions p ON p.id = rp.permission_id
WHERE p.feature_key = 'question_bank.category_manage';
DELETE FROM permissions WHERE feature_key = 'question_bank.category_manage';

DROP TABLE question_bank_categories;
