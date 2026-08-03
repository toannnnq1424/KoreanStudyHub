-- V28: Add classes.subject_id for subject-scoped HEAD features.
-- Backfill from the assigned lecturer's users.subject_id when present.

ALTER TABLE classes
    ADD COLUMN subject_id BIGINT NULL AFTER lecturer_id,
    ADD INDEX idx_class_subject (subject_id),
    ADD CONSTRAINT fk_class_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE SET NULL;

UPDATE classes c
    INNER JOIN users u ON u.id = c.lecturer_id
SET c.subject_id = u.subject_id
WHERE u.subject_id IS NOT NULL
  AND c.subject_id IS NULL;
