-- V116 may have matched a same-position lesson from another chapter in legacy
-- demo data. Tighten those sample bindings by canonical title without changing
-- the immutable V116 checksum already applied by local DevTools.
UPDATE question_bank_items item
JOIN subjects subject ON subject.id = item.subject_id
JOIN lesson_templates lesson
  ON lesson.subject_id = subject.id
 AND lesson.is_deleted = 0
 AND (
      (item.content LIKE '%Câu hỏi bài 1 số %' AND lesson.title = CONCAT('Bài 1 · Chào hỏi ', subject.code))
   OR (item.content LIKE '%Câu hỏi bài 2 số %' AND lesson.title = CONCAT('Bài 2 · Giới thiệu bản thân ', subject.code))
   OR (item.content LIKE '%Câu hỏi bài 3 số %' AND lesson.title = CONCAT('Bài 3 · Giao tiếp lớp học ', subject.code))
 )
SET item.lesson_template_id = lesson.id,
    item.chapter_title_snapshot = lesson.chapter_title,
    item.lesson_title_snapshot = lesson.title,
    item.chapter_order_snapshot = lesson.chapter_order,
    item.lesson_order_snapshot = lesson.display_order;
