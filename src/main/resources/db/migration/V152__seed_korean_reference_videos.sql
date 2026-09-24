-- Curated reference videos, discovered on YouTube on 2026-09-24.
-- External links only: no downloaded/copied media. Preserve all existing video fields.
CREATE TEMPORARY TABLE ksh_video_seed (
    slot INT PRIMARY KEY, url VARCHAR(500), summary VARCHAR(1000)
);
INSERT INTO ksh_video_seed VALUES
(0, 'https://www.youtube.com/watch?v=RNywocZvsGk', 'Video tham khảo mẫu · TTMIK: Core Grammar Level 1. Ôn tập ngữ pháp sơ cấp; giảng viên kiểm tra mức phù hợp trước khi sử dụng.'),
(1, 'https://www.youtube.com/watch?v=-Rtpq_N93YQ', 'Video tham khảo mẫu · TTMIK: 10 quy tắc phát âm tiếng Hàn qua một câu.'),
(2, 'https://www.youtube.com/watch?v=MiI4QWznwCQ', 'Video tham khảo mẫu · TTMIK: Core Grammar Level 3. Ôn tập nền tảng, không thay thế syllabus chuyên ngành.'),
(3, 'https://www.youtube.com/watch?v=cjCF5RaUuy4', 'Video tham khảo mẫu · masterTOPIK: TOPIK II, luyện viết lập luận.'),
(4, 'https://www.youtube.com/watch?v=lkbwZJwhHKw', 'Video tham khảo mẫu · masterTOPIK: TOPIK II, viết dựa trên thông tin.');
CREATE TEMPORARY TABLE ksh_subject_video_seed AS
SELECT id AS subject_id, code, leader_user_id,
       CASE WHEN code REGEXP '^(KOR|KRL)[12]' THEN 0
            WHEN code REGEXP '^(TOP|KOR[45]|KRL[45])' THEN 3
            ELSE 2 END AS base_slot
FROM subjects WHERE is_active = 1 AND code REGEXP '^(KOR|KRL|TOP)';

-- Empty subject libraries receive two clearly labelled supplemental lessons.
CREATE TEMPORARY TABLE ksh_empty_video_subjects AS
SELECT s.* FROM ksh_subject_video_seed s
JOIN users u ON u.id = s.leader_user_id AND u.is_deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM lesson_templates t WHERE t.subject_id = s.subject_id AND t.is_deleted = 0);
INSERT INTO lesson_templates
(owner_id, subject_id, title, chapter_title, chapter_order, display_order,
 content_type, video_provider, video_url, video_summary)
SELECT s.leader_user_id, s.subject_id, CONCAT(s.code, ' · Video tham khảo mẫu ', n.n + 1),
       'Video tham khảo bổ sung', 1, n.n + 1, 'VIDEO', 'YOUTUBE', v.url, v.summary
FROM ksh_empty_video_subjects s
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1) n
JOIN ksh_video_seed v ON v.slot = s.base_slot + n.n;

UPDATE lesson_templates t
JOIN ksh_subject_video_seed s ON s.subject_id = t.subject_id
JOIN ksh_video_seed v ON v.slot = s.base_slot + MOD(COALESCE(t.display_order, 0), 2)
SET t.video_provider = 'YOUTUBE', t.video_url = v.url,
    t.video_summary = CASE WHEN NULLIF(TRIM(t.video_summary), '') IS NULL THEN v.summary ELSE t.video_summary END
WHERE t.is_deleted = 0 AND NULLIF(TRIM(t.video_url), '') IS NULL
  AND NULLIF(TRIM(t.video_provider), '') IS NULL AND t.video_library_asset_id IS NULL;

UPDATE lessons l
JOIN sections sec ON sec.id = l.section_id AND sec.is_deleted = 0
JOIN classes c ON c.id = sec.class_id AND c.is_deleted = 0
JOIN ksh_subject_video_seed s ON s.subject_id = c.subject_id
JOIN ksh_video_seed v ON v.slot = s.base_slot + MOD(COALESCE(l.display_order, 0), 2)
SET l.video_provider = 'YOUTUBE', l.video_url = v.url,
    l.video_summary = CASE WHEN NULLIF(TRIM(l.video_summary), '') IS NULL THEN v.summary ELSE l.video_summary END
WHERE l.is_deleted = 0 AND NULLIF(TRIM(l.video_url), '') IS NULL
  AND NULLIF(TRIM(l.video_provider), '') IS NULL AND l.video_library_asset_id IS NULL;

DROP TEMPORARY TABLE ksh_empty_video_subjects;
DROP TEMPORARY TABLE ksh_subject_video_seed;
DROP TEMPORARY TABLE ksh_video_seed;
