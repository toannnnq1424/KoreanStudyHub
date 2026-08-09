-- Preserve the canonical Library hierarchy on each bank item. The live
-- lesson_template_id remains authoritative while available; these columns are
-- a historical fallback after a Library lesson is soft-deleted or removed.
ALTER TABLE question_bank_items
    ADD COLUMN chapter_title_snapshot VARCHAR(200) NULL AFTER lesson_template_id,
    ADD COLUMN lesson_title_snapshot VARCHAR(300) NULL AFTER chapter_title_snapshot,
    ADD COLUMN chapter_order_snapshot INT NULL AFTER lesson_title_snapshot,
    ADD COLUMN lesson_order_snapshot INT NULL AFTER chapter_order_snapshot;

UPDATE question_bank_items item
JOIN lesson_templates lesson ON lesson.id = item.lesson_template_id
SET item.chapter_title_snapshot = lesson.chapter_title,
    item.lesson_title_snapshot = lesson.title,
    item.chapter_order_snapshot = lesson.chapter_order,
    item.lesson_order_snapshot = lesson.display_order
WHERE item.lesson_template_id IS NOT NULL;

-- Repair legacy demo rows that V101 could not bind because the Library lesson
-- was created by a different lecturer than the sample question contributor.
UPDATE question_bank_items item
JOIN subjects subject ON subject.id = item.subject_id
JOIN lesson_templates lesson
  ON lesson.subject_id = subject.id
 AND lesson.is_deleted = 0
 AND (
      (item.content LIKE '%Câu hỏi bài 1 số %' AND lesson.display_order = 1)
   OR (item.content LIKE '%Câu hỏi bài 2 số %' AND lesson.display_order = 2)
   OR (item.content LIKE '%Câu hỏi bài 3 số %' AND lesson.display_order = 3)
 )
SET item.lesson_template_id = lesson.id,
    item.chapter_title_snapshot = lesson.chapter_title,
    item.lesson_title_snapshot = lesson.title,
    item.chapter_order_snapshot = lesson.chapter_order,
    item.lesson_order_snapshot = lesson.display_order
WHERE item.lesson_template_id IS NULL;
