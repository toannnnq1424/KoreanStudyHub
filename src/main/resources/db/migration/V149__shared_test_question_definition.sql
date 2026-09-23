-- A class delivery keeps timing/attempt identity, but no longer copies questions.
-- Legacy snapshots remain untouched until their content can be verified equal.
ALTER TABLE tests ADD COLUMN shared_question_source_id BIGINT NULL;
ALTER TABLE tests ADD CONSTRAINT fk_tests_shared_question_source
    FOREIGN KEY (shared_question_source_id) REFERENCES tests(id);
