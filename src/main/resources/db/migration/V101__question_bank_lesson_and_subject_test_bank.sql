-- Non-Practice Question/Test Bank hierarchy. Reuse lesson_templates and tests;
-- no category or Practice table participates in this flow.

ALTER TABLE question_bank_items
    ADD COLUMN lesson_template_id BIGINT NULL AFTER subject_id,
    ADD INDEX idx_qb_items_lesson_status (lesson_template_id, workflow_status),
    ADD CONSTRAINT fk_qb_items_lesson_template
        FOREIGN KEY (lesson_template_id) REFERENCES lesson_templates(id)
        ON DELETE SET NULL;

ALTER TABLE tests
    ADD COLUMN subject_id BIGINT NULL AFTER class_id,
    ADD INDEX idx_tests_subject_status (subject_id, status, is_deleted),
    ADD CONSTRAINT fk_tests_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(id)
        ON DELETE RESTRICT;

UPDATE tests test_row
JOIN classes class_row ON class_row.id = test_row.class_id
SET test_row.subject_id = class_row.subject_id
WHERE test_row.subject_id IS NULL;

-- Attach the demo bank rows to their canonical lesson so the fresh workspace
-- immediately demonstrates subject -> chapter -> lesson grouping.
UPDATE question_bank_items item
JOIN subjects subject ON subject.id = item.subject_id
JOIN lesson_templates lesson
  ON lesson.subject_id = subject.id
 AND lesson.owner_id = item.contributor_id
 AND (
      (item.content LIKE '%Câu hỏi bài 1 số %' AND lesson.title = CONCAT('Bài 1 · Chào hỏi ', subject.code))
   OR (item.content LIKE '%Câu hỏi bài 2 số %' AND lesson.title = CONCAT('Bài 2 · Giới thiệu bản thân ', subject.code))
   OR (item.content LIKE '%Câu hỏi bài 3 số %' AND lesson.title = CONCAT('Bài 3 · Giao tiếp lớp học ', subject.code))
 )
SET item.lesson_template_id = lesson.id
WHERE item.lesson_template_id IS NULL;
