-- V3's activity_departments has no application entity, writer, reader or UI.
-- The live subject-admin history is renamed below and keeps all rows.
DROP TABLE activity_departments;

ALTER TABLE department_activities DROP FOREIGN KEY fk_dact_department;
ALTER TABLE department_activities RENAME COLUMN department_id TO subject_id;
RENAME TABLE department_activities TO subject_activities;
ALTER TABLE subject_activities
    RENAME INDEX idx_dact_department TO idx_subject_activity_subject,
    ADD CONSTRAINT fk_subject_activity_subject FOREIGN KEY (subject_id)
        REFERENCES departments(id) ON DELETE CASCADE;
