-- Fresh-only demo content for the Non-Practice Korean learning flow.
-- No Practice table is read or written here.

INSERT INTO users (
    email, password_hash, full_name, role, subject_id,
    is_email_verified, is_active, is_deleted
) VALUES (
    'kor_leader@ksh.edu.vn',
    '$2a$10$QXT/sSbapXXHEWstiZbtT.oyHllsGWSF.E5C..Xl4SkMwYbfi.t5a',
    'Trưởng Bộ Môn Tiếng Hàn', 'LEADER',
    (SELECT id FROM subjects WHERE code = 'KOR311' LIMIT 1),
    1, 1, 0
) AS incoming
ON DUPLICATE KEY UPDATE
    full_name = incoming.full_name,
    role = incoming.role,
    subject_id = incoming.subject_id,
    is_email_verified = incoming.is_email_verified,
    is_active = incoming.is_active,
    is_deleted = incoming.is_deleted;

SET @korean_leader_id := (
    SELECT id FROM users WHERE email = 'kor_leader@ksh.edu.vn' LIMIT 1
);

UPDATE subjects
SET leader_user_id = @korean_leader_id
WHERE is_active = 1
  AND code IN (
      'KOR311', 'KOR321', 'KOR411',
      'KRL101', 'KRL112', 'KRL122', 'KRL201', 'KRL212', 'KRL222',
      'KRL311', 'KRL312', 'KRL321', 'KRL322', 'KRL402', 'KRL411',
      'KRL421', 'KRL502', 'KRL511', 'KRL521'
  );

SET @demo_lecturer_id := (
    SELECT id FROM users WHERE email = 'lecturer@ksh.edu.vn' LIMIT 1
);
SET @demo_reviewer_id := (
    SELECT id FROM users WHERE email = 'admin@ksh.edu.vn' LIMIT 1
);

-- One ACTIVE source class per Korean subject makes every catalog entry usable
-- in Test Bank. KOR311 receives two additional empty targets for multi-class
-- lesson/test distribution UAT.
INSERT INTO classes (
    name, lecturer_id, subject_id, start_date, end_date, max_students,
    status, description, created_by, approved_by, approved_at, is_deleted
)
SELECT CONCAT(subject.code, ' · Lớp minh hoạ'),
       @demo_lecturer_id, subject.id, CURRENT_DATE, NULL, 40,
       'ACTIVE', CONCAT('Lớp dữ liệu mẫu cho ', subject.name),
       @demo_lecturer_id, @demo_reviewer_id, CURRENT_TIMESTAMP, 0
FROM subjects subject
WHERE subject.is_active = 1
  AND subject.code IN (
      'KOR311', 'KOR321', 'KOR411',
      'KRL101', 'KRL112', 'KRL122', 'KRL201', 'KRL212', 'KRL222',
      'KRL311', 'KRL312', 'KRL321', 'KRL322', 'KRL402', 'KRL411',
      'KRL421', 'KRL502', 'KRL511', 'KRL521'
  )
  AND @demo_lecturer_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM classes existing
      WHERE existing.name = CONCAT(subject.code, ' · Lớp minh hoạ')
        AND existing.lecturer_id = @demo_lecturer_id
        AND existing.is_deleted = 0
  );

INSERT INTO classes (
    name, lecturer_id, subject_id, start_date, end_date, max_students,
    status, description, created_by, approved_by, approved_at, is_deleted
)
SELECT CONCAT('KOR311 · Lớp ', suffix.label),
       @demo_lecturer_id, subject.id, CURRENT_DATE, NULL, 40,
       'ACTIVE', 'Lớp đích dùng thử phân phối đồng thời',
       @demo_lecturer_id, @demo_reviewer_id, CURRENT_TIMESTAMP, 0
FROM subjects subject
CROSS JOIN (
    SELECT 'A' AS label
    UNION ALL SELECT 'B'
) suffix
WHERE subject.code = 'KOR311'
  AND subject.is_active = 1
  AND @demo_lecturer_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM classes existing
      WHERE existing.name = CONCAT('KOR311 · Lớp ', suffix.label)
        AND existing.lecturer_id = @demo_lecturer_id
        AND existing.is_deleted = 0
  );

-- Canonical subject hierarchy seed: two chapters and three lessons per code.
INSERT INTO lesson_templates (
    owner_id, subject_id, title, chapter_title, chapter_order, display_order,
    content_type, content_richtext, is_deleted
)
SELECT @demo_lecturer_id, subject.id,
       CASE lesson_seed.lesson_no
           WHEN 1 THEN CONCAT('Bài 1 · Chào hỏi ', subject.code)
           WHEN 2 THEN CONCAT('Bài 2 · Giới thiệu bản thân ', subject.code)
           ELSE CONCAT('Bài 3 · Giao tiếp lớp học ', subject.code)
       END,
       CASE WHEN lesson_seed.lesson_no < 3
            THEN 'Chương 1 · Nền tảng' ELSE 'Chương 2 · Vận dụng' END,
       CASE WHEN lesson_seed.lesson_no < 3 THEN 1 ELSE 2 END,
       lesson_seed.lesson_no, 'RICHTEXT',
       CONCAT('<h2>', subject.code, ' · Bài ', lesson_seed.lesson_no,
              '</h2><p>Nội dung bài giảng mẫu theo cây mã môn, chương và bài học cho ',
              subject.name, '.</p>'),
       0
FROM subjects subject
CROSS JOIN (
    SELECT 1 AS lesson_no UNION ALL SELECT 2 UNION ALL SELECT 3
) lesson_seed
WHERE subject.is_active = 1
  AND @demo_lecturer_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM lesson_templates existing
      WHERE existing.owner_id = @demo_lecturer_id
        AND existing.subject_id = subject.id
        AND existing.title = CASE lesson_seed.lesson_no
            WHEN 1 THEN CONCAT('Bài 1 · Chào hỏi ', subject.code)
            WHEN 2 THEN CONCAT('Bài 2 · Giới thiệu bản thân ', subject.code)
            ELSE CONCAT('Bài 3 · Giao tiếp lớp học ', subject.code)
        END
        AND existing.is_deleted = 0
  );

-- Three approved questions plus one pending-review question per subject.
INSERT INTO question_bank_items (
    subject_id, contributor_id, reviewed_by, question_type, workflow_status,
    content, explanation, approved_at, reviewed_at
)
SELECT subject.id, @demo_lecturer_id,
       CASE WHEN question_seed.question_no = 4 THEN NULL ELSE @demo_reviewer_id END,
       'MCQ', CASE WHEN question_seed.question_no = 4 THEN 'REVIEW' ELSE 'APPROVED' END,
       CONCAT('<p>[', subject.code, '] Câu hỏi bài ',
              CASE WHEN question_seed.question_no < 3 THEN 1
                   WHEN question_seed.question_no = 3 THEN 2 ELSE 3 END,
              ' số ', question_seed.question_no, ': Chọn biểu đạt tiếng Hàn phù hợp.</p>'),
       '<p>Đối chiếu nội dung và ví dụ trong bài học tương ứng.</p>',
       CASE WHEN question_seed.question_no = 4 THEN NULL ELSE CURRENT_TIMESTAMP END,
       CASE WHEN question_seed.question_no = 4 THEN NULL ELSE CURRENT_TIMESTAMP END
FROM subjects subject
CROSS JOIN (
    SELECT 1 AS question_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
) question_seed
WHERE subject.is_active = 1
  AND @demo_lecturer_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_items existing
      WHERE existing.subject_id = subject.id
        AND existing.contributor_id = @demo_lecturer_id
        AND existing.content = CONCAT('<p>[', subject.code, '] Câu hỏi bài ',
              CASE WHEN question_seed.question_no < 3 THEN 1
                   WHEN question_seed.question_no = 3 THEN 2 ELSE 3 END,
              ' số ', question_seed.question_no, ': Chọn biểu đạt tiếng Hàn phù hợp.</p>')
  );

INSERT INTO question_bank_options (item_id, content, is_correct, sort_order)
SELECT item.id,
       CASE option_seed.sort_order WHEN 1 THEN '<p>안녕하세요</p>'
           WHEN 2 THEN '<p>감사합니다</p>' WHEN 3 THEN '<p>미안합니다</p>'
           ELSE '<p>안녕히 가세요</p>' END,
       CASE WHEN option_seed.sort_order = 1 THEN 1 ELSE 0 END,
       option_seed.sort_order
FROM question_bank_items item
JOIN subjects subject ON subject.id = item.subject_id
CROSS JOIN (
    SELECT 1 AS sort_order
    UNION ALL SELECT 2
    UNION ALL SELECT 3
    UNION ALL SELECT 4
) option_seed
WHERE item.contributor_id = @demo_lecturer_id
  AND item.content LIKE CONCAT('<p>[', subject.code, '] Câu hỏi bài %')
  AND NOT EXISTS (
      SELECT 1 FROM question_bank_options existing
      WHERE existing.item_id = item.id
  );

-- One finished source test per subject. It remains class-bound so the current
-- test schema can derive its subject without introducing another table.
INSERT INTO tests (
    title, description, class_id, type, duration_minutes, passing_score,
    time_mode, total_questions, shuffle_questions, shuffle_options,
    status, created_by, is_deleted
)
SELECT CONCAT(subject.code, ' · Bài kiểm tra khởi động'),
       CONCAT('Bài test mẫu đã hoàn tất cho ', subject.name),
       clazz.id, 'MODULE', 20, 5.00, 'INDIVIDUAL', 1, 0, 0,
       'PUBLISHED', @demo_lecturer_id, 0
FROM subjects subject
JOIN classes clazz
  ON clazz.subject_id = subject.id
 AND clazz.name = CONCAT(subject.code, ' · Lớp minh hoạ')
 AND clazz.lecturer_id = @demo_lecturer_id
 AND clazz.is_deleted = 0
WHERE subject.is_active = 1
  AND @demo_lecturer_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tests existing
      WHERE existing.created_by = @demo_lecturer_id
        AND existing.class_id = clazz.id
        AND existing.title = CONCAT(subject.code, ' · Bài kiểm tra khởi động')
        AND existing.is_deleted = 0
  );

INSERT INTO questions (
    test_id, question_type, content, explanation, points, sort_order
)
SELECT test.id, 'MCQ',
       CONCAT('<p>[', subject.code, '] Chọn lời chào đúng.</p>'),
       '<p>Đáp án đúng là 안녕하세요.</p>', 10.00, 1
FROM tests test
JOIN classes clazz ON clazz.id = test.class_id
JOIN subjects subject ON subject.id = clazz.subject_id
WHERE test.created_by = @demo_lecturer_id
  AND test.title = CONCAT(subject.code, ' · Bài kiểm tra khởi động')
  AND test.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM questions existing WHERE existing.test_id = test.id
  );

INSERT INTO question_options (question_id, content, is_correct, sort_order)
SELECT question.id,
       CASE option_seed.sort_order
           WHEN 1 THEN '<p>안녕하세요</p>'
           WHEN 2 THEN '<p>감사합니다</p>'
           WHEN 3 THEN '<p>미안합니다</p>'
           ELSE '<p>안녕히 가세요</p>'
       END,
       CASE WHEN option_seed.sort_order = 1 THEN 1 ELSE 0 END,
       option_seed.sort_order
FROM questions question
JOIN tests test ON test.id = question.test_id
CROSS JOIN (
    SELECT 1 AS sort_order
    UNION ALL SELECT 2
    UNION ALL SELECT 3
    UNION ALL SELECT 4
) option_seed
WHERE test.created_by = @demo_lecturer_id
  AND test.title LIKE '% · Bài kiểm tra khởi động'
  AND NOT EXISTS (
      SELECT 1 FROM question_options existing
      WHERE existing.question_id = question.id
  );
