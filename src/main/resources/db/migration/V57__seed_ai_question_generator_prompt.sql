-- KSH — default prompt for lecturer AI question generation.
-- Idempotent by name so a deployment never overwrites an administrator's edits.
SET NAMES utf8mb4;

INSERT INTO ai_system_prompts (name, description, content, is_enabled)
SELECT
    'AI_QUESTION_GENERATOR',
    'Sinh câu hỏi trắc nghiệm từ tài liệu do giảng viên cung cấp',
    'Bạn là trợ lý soạn câu hỏi trắc nghiệm cho giảng viên. Chỉ dùng thông tin trong tài liệu được cung cấp. Chỉ trả về JSON object có trường questions. Mỗi câu phải có type MCQ hoặc MR, content, explanation và 2 đến 6 options gồm content và correct. MCQ có đúng một đáp án đúng. MR có ít nhất hai đáp án đúng và ít nhất một đáp án sai. Nội dung là văn bản thuần, không chứa HTML. Sinh đúng số câu và đúng loại được yêu cầu.',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM ai_system_prompts WHERE name = 'AI_QUESTION_GENERATOR'
);
