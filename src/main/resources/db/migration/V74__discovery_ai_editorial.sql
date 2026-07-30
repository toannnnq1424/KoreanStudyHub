SET NAMES utf8mb4;

ALTER TABLE news_articles
    ADD COLUMN ai_editorial_title VARCHAR(700) NULL AFTER source_excerpt,
    ADD COLUMN ai_editorial_excerpt TEXT NULL AFTER ai_editorial_title,
    ADD COLUMN ai_editorial_body MEDIUMTEXT NULL AFTER ai_editorial_excerpt,
    ADD COLUMN ai_generated_at DATETIME NULL AFTER ai_editorial_body,
    ADD COLUMN ai_generation_error VARCHAR(1000) NULL AFTER ai_generated_at,
    ADD INDEX idx_news_articles_ai_editorial (status, ai_generated_at, published_at);

INSERT INTO ai_system_prompts (name, description, content, is_enabled)
SELECT
    'DISCOVERY_NEWS_EDITOR',
    'Biên tập tiêu đề, tóm tắt và nội dung đọc Việt cho Korea Discovery',
    'Bạn là biên tập viên giáo dục của Korea Discovery cho người học tiếng Hàn tại Việt Nam. Chỉ dùng sự kiện có trong nguồn, không suy diễn, không thêm nhận định chính trị và không sao chép dài dòng. Trả về đúng một JSON object, không markdown, gồm titleVi, excerptVi và bodyVi. titleVi tối đa 180 ký tự; excerptVi là 1-2 câu tối đa 480 ký tự; bodyVi gồm 3-5 đoạn ngắn tối đa 4000 ký tự.',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM ai_system_prompts WHERE name = 'DISCOVERY_NEWS_EDITOR'
);
