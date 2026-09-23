-- Optional local/demo seed. Does not replace existing curricula or distribute
-- lessons to classes. Only active, unlocked subjects with no lessons are seeded.
START TRANSACTION;
INSERT INTO lesson_templates
    (owner_id, subject_id, title, chapter_title, chapter_order, display_order,
     content_type, content_richtext)
SELECT s.leader_user_id, s.id, CONCAT(s.code, ' · ', outline.lesson_title),
       outline.chapter_title, outline.chapter_order, outline.lesson_order,
       'RICHTEXT', CONCAT('<p>Bài thực hành mẫu cho môn ', s.code,
           '. Giảng viên điều chỉnh yêu cầu phù hợp trình độ lớp trước khi phân phối.</p>',
           '<h2>Mục tiêu</h2><p>', outline.lesson_title,
           '</p><h2>Thực hành</h2><p>Chuẩn bị 5 ví dụ bằng tiếng Hàn, trình bày và trao đổi với bạn học.</p>')
FROM subjects s
JOIN users owner ON owner.id = s.leader_user_id AND owner.is_deleted = 0
CROSS JOIN (
    SELECT 1 chapter_order, 1 lesson_order, 'Chương 1: Kiến thức nền tảng' chapter_title, 'Từ vựng theo chủ đề' lesson_title
    UNION ALL SELECT 1, 2, 'Chương 1: Kiến thức nền tảng', 'Cấu trúc câu và ngữ pháp'
    UNION ALL SELECT 2, 3, 'Chương 2: Vận dụng', 'Đọc hiểu và trao đổi'
    UNION ALL SELECT 2, 4, 'Chương 2: Vận dụng', 'Nghe và phản hồi'
    UNION ALL SELECT 3, 5, 'Chương 3: Thực hành tổng hợp', 'Viết và trình bày'
    UNION ALL SELECT 3, 6, 'Chương 3: Thực hành tổng hợp', 'Ôn tập và tự đánh giá'
) outline
WHERE s.is_active = 1 AND s.library_locked = 0
  AND NOT EXISTS (SELECT 1 FROM lesson_templates existing
                  WHERE existing.subject_id = s.id AND existing.is_deleted = 0);
COMMIT;
