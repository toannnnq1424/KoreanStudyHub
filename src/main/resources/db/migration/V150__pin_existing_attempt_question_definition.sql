-- Historical attempts continue to use their original question and option IDs.
-- This includes in-progress attempts: do not change the questions mid-exam.
ALTER TABLE test_attempts ADD COLUMN question_definition_id BIGINT NULL;
UPDATE test_attempts a JOIN tests t ON t.id = a.test_id
SET a.question_definition_id = COALESCE(t.shared_question_source_id, t.id);
ALTER TABLE test_attempts ADD CONSTRAINT fk_attempt_question_definition
    FOREIGN KEY (question_definition_id) REFERENCES tests(id);
