-- ============================================================================
-- KSH Practice Module Schema & Seeds (Consolidated V16 - V29)
-- ============================================================================

-- 1. practice_sets
CREATE TABLE practice_sets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT NULL,
    skill VARCHAR(20) NOT NULL,
    topik_level VARCHAR(20) NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
    class_id BIGINT NULL,
    source_pdf_path VARCHAR(500) NULL,
    audio_path VARCHAR(500) NULL,
    metadata_json JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    creation_method VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    cover_image_url VARCHAR(500) NULL,
    INDEX idx_ps_skill_status (skill, status, is_deleted),
    INDEX idx_ps_scope_class (scope, class_id),
    CONSTRAINT chk_ps_skill CHECK (skill IN ('READING','LISTENING','WRITING','SPEAKING','MIXED')),
    CONSTRAINT chk_ps_scope CHECK (scope IN ('GLOBAL','CLASS')),
    CONSTRAINT chk_ps_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    CONSTRAINT fk_ps_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE SET NULL,
    CONSTRAINT fk_ps_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. practice_tests
CREATE TABLE practice_tests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT NULL,
    display_order INT NOT NULL DEFAULT 0,
    estimated_minutes INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ptest_set (set_id, display_order),
    CONSTRAINT fk_ptest_set FOREIGN KEY (set_id) REFERENCES practice_sets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. practice_sections
CREATE TABLE practice_sections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    test_id BIGINT NULL,
    title VARCHAR(300) NOT NULL,
    skill VARCHAR(20) NOT NULL,
    section_type VARCHAR(50) NULL,
    instructions TEXT NULL,
    duration_minutes INT NULL,
    total_points DECIMAL(6,2) NULL,
    display_order INT NOT NULL,
    CONSTRAINT fk_ps_set FOREIGN KEY (set_id) REFERENCES practice_sets(id) ON DELETE CASCADE,
    CONSTRAINT fk_psec_test FOREIGN KEY (test_id) REFERENCES practice_tests(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. practice_question_groups
CREATE TABLE practice_question_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    section_id BIGINT NULL,
    group_label VARCHAR(50) NOT NULL,
    question_from INT NOT NULL,
    question_to INT NOT NULL,
    instruction TEXT NULL,
    audio_url VARCHAR(500) NULL,
    example_json JSON NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pqg_set FOREIGN KEY (set_id) REFERENCES practice_sets(id) ON DELETE CASCADE,
    CONSTRAINT fk_pqg_section FOREIGN KEY (section_id) REFERENCES practice_sections(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. practice_questions
CREATE TABLE practice_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    group_id BIGINT NULL,
    question_no INT NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    prompt TEXT NOT NULL,
    options_json JSON NULL,
    answer_key VARCHAR(500) NULL,
    explanation TEXT NULL,
    points DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pq_set_order (set_id, display_order),
    CONSTRAINT chk_pq_type CHECK (question_type IN ('MCQ','SHORT_TEXT','ESSAY','SPEAKING','TRUE_FALSE_NOT_GIVEN','MATCHING_INFORMATION','FILL_BLANK','ORDERING','TEXT_COMPLETION')),
    CONSTRAINT fk_pq_set FOREIGN KEY (set_id) REFERENCES practice_sets(id) ON DELETE CASCADE,
    CONSTRAINT fk_pq_group FOREIGN KEY (group_id) REFERENCES practice_question_groups(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. practice_submissions (legacy flow compatibility)
CREATE TABLE practice_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    score DECIMAL(6,2) NULL,
    total_points DECIMAL(6,2) NULL,
    answers_json JSON NULL,
    ai_feedback_json JSON NULL,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_psub_user_created (user_id, created_at),
    INDEX idx_psub_set_user (set_id, user_id),
    CONSTRAINT chk_psub_status CHECK (status IN ('IN_PROGRESS','SUBMITTED','GRADED')),
    CONSTRAINT fk_psub_set FOREIGN KEY (set_id) REFERENCES practice_sets(id) ON DELETE CASCADE,
    CONSTRAINT fk_psub_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. practice_attempts
CREATE TABLE practice_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    set_id BIGINT NOT NULL,
    test_id BIGINT NOT NULL,
    skill VARCHAR(20) NOT NULL,
    section_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    analysis_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUESTED',
    score DECIMAL(6,2) NULL,
    total_points DECIMAL(6,2) NULL,
    answers_json JSON NULL,
    ai_feedback_json JSON NULL,
    analysis_requested_at DATETIME NULL,
    analysis_completed_at DATETIME NULL,
    analysis_usage_id BIGINT NULL,
    analysis_engine VARCHAR(50) NULL,
    analysis_error_code VARCHAR(100) NULL,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_pa_skill CHECK (skill IN ('READING','LISTENING','WRITING','SPEAKING')),
    CONSTRAINT chk_pa_status CHECK (status IN ('IN_PROGRESS','SUBMITTED','GRADED')),
    CONSTRAINT chk_pa_analysis CHECK (analysis_status IN ('NOT_REQUESTED','QUEUED','PROCESSING','SUCCEEDED','FAILED')),
    INDEX idx_pa_user_test_skill (user_id, test_id, skill),
    INDEX idx_pa_user_set (user_id, set_id),
    INDEX idx_pa_test (test_id),
    CONSTRAINT fk_pa_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_pa_set FOREIGN KEY (set_id) REFERENCES practice_sets(id),
    CONSTRAINT fk_pa_test FOREIGN KEY (test_id) REFERENCES practice_tests(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. practice_ai_analysis_usage
CREATE TABLE practice_ai_analysis_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    skill VARCHAR(20) NOT NULL,
    usage_date DATE NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NULL,
    model_name VARCHAR(100) NULL,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    failure_code VARCHAR(100) NULL,
    failure_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_aiau_status CHECK (status IN ('RESERVED','SUCCEEDED','FAILED','CANCELLED_BEFORE_PROVIDER')),
    UNIQUE KEY uk_aiau_user_key (user_id, request_key),
    INDEX idx_aiau_user_date (user_id, usage_date),
    INDEX idx_aiau_attempt (attempt_id),
    INDEX idx_aiau_status (status),
    CONSTRAINT fk_aiau_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_aiau_attempt FOREIGN KEY (attempt_id) REFERENCES practice_attempts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. question_explanation_cache
CREATE TABLE question_explanation_cache (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id     BIGINT NOT NULL,
    test_id         BIGINT,
    skill_type      VARCHAR(20) NOT NULL,
    question_type   VARCHAR(40) NOT NULL,
    question_hash   VARCHAR(64) NOT NULL,
    correct_answer  VARCHAR(500),
    explanation_json LONGTEXT NOT NULL,
    ai_model        VARCHAR(100),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_explanation (question_id, question_hash, correct_answer(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. practice_drafts
CREATE TABLE practice_drafts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT NULL,
    category VARCHAR(50) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    class_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    owner_id BIGINT NOT NULL,
    draft_json LONGTEXT NULL,
    version INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    published_set_id BIGINT NULL,
    creation_method VARCHAR(30) NULL,
    CONSTRAINT fk_pd_published_set FOREIGN KEY (published_set_id) REFERENCES practice_sets(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. practice_edit_logs
CREATE TABLE practice_edit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    edited_by BIGINT NOT NULL,
    change_summary VARCHAR(500) NOT NULL,
    change_details_json LONGTEXT NULL,
    before_snapshot_json LONGTEXT NULL,
    after_snapshot_json LONGTEXT NULL,
    edit_type VARCHAR(50) NULL,
    edited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pel_set FOREIGN KEY (set_id) REFERENCES practice_sets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. practice_pdf_import_sessions
CREATE TABLE practice_pdf_import_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    linked_draft_id BIGINT NULL,
    created_by BIGINT NULL,
    uploader_id BIGINT NOT NULL,
    title VARCHAR(255) NULL,
    exam_category VARCHAR(64) NULL,
    original_filename VARCHAR(255) NULL,
    stored_pdf_path VARCHAR(512) NULL,
    total_pages INT NULL,
    selected_start_page INT NULL,
    selected_end_page INT NULL,
    current_page INT NOT NULL DEFAULT 1,
    status VARCHAR(64) NOT NULL,
    extraction_strategy VARCHAR(64) NULL,
    extraction_mode VARCHAR(64) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    last_saved_at DATETIME NULL,
    snapshot_json LONGTEXT NULL,
    INDEX idx_pdf_session_uploader (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. practice_pdf_region_annotations
CREATE TABLE practice_pdf_region_annotations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    page_number INT NOT NULL,
    region_type VARCHAR(64) NOT NULL,
    x_ratio DOUBLE NOT NULL,
    y_ratio DOUBLE NOT NULL,
    width_ratio DOUBLE NOT NULL,
    height_ratio DOUBLE NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    section_temp_id VARCHAR(128) NULL,
    group_temp_id VARCHAR(128) NULL,
    expected_question_type VARCHAR(64) NULL,
    expected_question_from INT NULL,
    expected_question_to INT NULL,
    target_question_no INT NULL,
    option_index INT NULL,
    asset_placement VARCHAR(64) NULL,
    include_in_ai BOOLEAN NOT NULL DEFAULT TRUE,
    include_text_in_ai BOOLEAN NOT NULL DEFAULT TRUE,
    include_image_in_ai BOOLEAN NOT NULL DEFAULT TRUE,
    save_to_asset_library BOOLEAN NOT NULL DEFAULT FALSE,
    lecturer_note TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_pdf_region_session_page (session_id, page_number),
    INDEX idx_pdf_region_session_type (session_id, region_type),
    INDEX idx_pdf_region_session_order (session_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. practice_pdf_import_section_drafts
CREATE TABLE practice_pdf_import_section_drafts (
    temp_id VARCHAR(128) PRIMARY KEY,
    session_id BIGINT NOT NULL,
    title VARCHAR(255) NULL,
    skill VARCHAR(64) NULL,
    section_type VARCHAR(64) NULL,
    display_order INT NOT NULL DEFAULT 0,
    INDEX idx_pdf_section_draft_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. practice_pdf_import_group_drafts
CREATE TABLE practice_pdf_import_group_drafts (
    temp_id VARCHAR(128) PRIMARY KEY,
    session_id BIGINT NOT NULL,
    section_temp_id VARCHAR(128) NULL,
    title VARCHAR(255) NULL,
    expected_from INT NULL,
    expected_to INT NULL,
    display_order INT NOT NULL DEFAULT 0,
    INDEX idx_pdf_group_draft_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. practice_pdf_page_extractions
CREATE TABLE practice_pdf_page_extractions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    page_number INT NOT NULL,
    raw_text LONGTEXT NULL,
    normalized_text LONGTEXT NULL,
    raw_char_count INT NOT NULL DEFAULT 0,
    extraction_status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_page_extract_session FOREIGN KEY (session_id) REFERENCES practice_pdf_import_sessions (id) ON DELETE CASCADE,
    INDEX idx_page_extract_session_page (session_id, page_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. lecturer_assets
CREATE TABLE lecturer_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_lecturer_id BIGINT NOT NULL,
    source_import_session_id BIGINT NULL,
    source_region_id BIGINT NULL,
    source_page_number INT NULL,
    crop_x DOUBLE NULL,
    crop_y DOUBLE NULL,
    crop_width DOUBLE NULL,
    crop_height DOUBLE NULL,
    sha256 VARCHAR(64) NULL,
    source_type VARCHAR(64) NOT NULL DEFAULT 'PDF_REGION',
    storage_provider VARCHAR(64) NOT NULL DEFAULT 'LOCAL',
    storage_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NULL,
    mime_type VARCHAR(128) NULL,
    width INT NULL,
    height INT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    asset_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NULL,
    alt_text VARCHAR(512) NULL,
    visibility VARCHAR(64) NOT NULL DEFAULT 'PRIVATE',
    status VARCHAR(64) NOT NULL DEFAULT 'TEMPORARY',
    lecturer_note TEXT NULL,
    tags_json TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    INDEX idx_lecturer_assets_owner (owner_lecturer_id),
    INDEX idx_lecturer_assets_session (source_import_session_id),
    INDEX idx_lecturer_assets_status (status),
    INDEX idx_lecturer_assets_sha256 (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 18. practice_draft_asset_usages
CREATE TABLE practice_draft_asset_usages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    section_temp_id VARCHAR(64) NULL,
    group_temp_id VARCHAR(64) NULL,
    question_temp_id VARCHAR(64) NULL,
    placement VARCHAR(64) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    caption VARCHAR(512) NULL,
    alt_text VARCHAR(512) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_draft_asset_usage_draft FOREIGN KEY (draft_id) REFERENCES practice_drafts (id) ON DELETE CASCADE,
    CONSTRAINT fk_draft_asset_usage_asset FOREIGN KEY (asset_id) REFERENCES lecturer_assets (id) ON DELETE CASCADE,
    INDEX idx_draft_asset_usage_draft (draft_id),
    INDEX idx_draft_asset_usage_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 19. practice_ai_request_audits
CREATE TABLE practice_ai_request_audits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    prompt_version VARCHAR(64) NULL,
    model VARCHAR(128) NULL,
    strategy VARCHAR(64) NULL,
    sent_text_chars INT NOT NULL DEFAULT 0,
    sent_image_count INT NOT NULL DEFAULT 0,
    sent_image_bytes BIGINT NOT NULL DEFAULT 0,
    payload_summary_json LONGTEXT NULL,
    status VARCHAR(64) NOT NULL,
    error_code VARCHAR(128) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_ai_audit_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================================
-- SEED DATA FOR PRACTICE FEATURE
-- ============================================================================

-- 1. Insert seed practice sets
INSERT INTO practice_sets
    (id, title, description, skill, topik_level, scope, metadata_json, status, created_by, creation_method)
VALUES
    (1, 'TOPIK I - Luyện đọc nhanh', 'Bộ câu hỏi đọc mẫu để học sinh làm quen giao diện luyện tập.', 'READING', 'TOPIK_I', 'GLOBAL',
     JSON_OBJECT('topic', 'daily_life', 'source', 'seed'), 'PUBLISHED', 1, 'MANUAL'),
    (2, 'TOPIK I - Luyện nghe hội thoại', 'Bài nghe mẫu kèm transcript để luyện chọn đáp án.', 'LISTENING', 'TOPIK_I', 'GLOBAL',
     JSON_OBJECT('topic', 'conversation', 'source', 'seed'), 'PUBLISHED', 1, 'MANUAL'),
    (3, 'TOPIK II - Viết câu 53', 'Bài viết mẫu để thử AI chấm theo rubric tiếng Việt - tiếng Hàn.', 'WRITING', 'TOPIK_II', 'GLOBAL',
     JSON_OBJECT('writing_question', 53, 'target_chars', '200-300'), 'PUBLISHED', 1, 'MANUAL'),
    (4, 'TOPIK II - Phòng luyện nói cá nhân', 'Bộ luyện nói mô phỏng: học sinh trả lời theo gợi ý, hệ thống trả feedback mock trước khi nối chấm phát âm thật.', 'SPEAKING', 'TOPIK_II', 'GLOBAL',
     JSON_OBJECT('source', 'seed', 'mock_evaluator', true, 'parts', 3), 'PUBLISHED', 1, 'MANUAL'),
    (5, 'Đọc hiểu nâng cao (True/False & Matching)', 'Bài tập đọc hiểu tổng hợp chứa các dạng câu hỏi True/False/Not Given, Matching Information, Điền từ và Sắp xếp.', 'READING', 'UNCLASSIFIED', 'GLOBAL',
     JSON_OBJECT('source', 'seed', 'extended_types', true), 'PUBLISHED', 1, 'MANUAL'),
    (6, 'Luyện nói nâng cao (Speaking Tasks)', 'Bài luyện nói mở rộng với nhiều Task độc lập kiểm tra kỹ năng đàm thoại và thuyết trình tiếng Hàn.', 'SPEAKING', 'UNCLASSIFIED', 'GLOBAL',
     JSON_OBJECT('source', 'seed', 'extended_types', true), 'PUBLISHED', 1, 'MANUAL');

-- 2. Insert seed practice tests (for each set)
INSERT INTO practice_tests
    (id, set_id, title, description, display_order, estimated_minutes)
VALUES
    (1, 1, 'TOPIK I - Luyện đọc nhanh — Full Test', 'Bài kiểm tra kỹ năng Đọc cơ bản', 0, 40),
    (2, 2, 'TOPIK I - Luyện nghe hội thoại — Full Test', 'Bài kiểm tra kỹ năng Nghe hội thoại', 0, 30),
    (3, 3, 'TOPIK II - Viết câu 53 — Full Test', 'Bài viết câu 53 TOPIK II', 0, 20),
    (4, 4, 'TOPIK II - Phòng luyện nói cá nhân — Full Test', 'Bài luyện nói TOPIK II tự luận', 0, 15),
    (5, 5, 'Đọc hiểu nâng cao (True/False & Matching) — Full Test', 'Bài thi đọc tổng hợp nhiều dạng câu hỏi', 0, 50),
    (6, 6, 'Luyện nói nâng cao (Speaking Tasks) — Full Test', 'Bài thi nói nâng cao', 0, 20);

-- 3. Insert seed practice sections
INSERT INTO practice_sections
    (id, set_id, test_id, title, skill, section_type, instructions, duration_minutes, total_points, display_order)
VALUES
    (1, 1, 1, 'Phần Đọc', 'READING', 'MAIN', 'Hãy hoàn thành phần thi.', 40, 100.00, 0),
    (2, 2, 2, 'Phần Nghe', 'LISTENING', 'MAIN', 'Hãy hoàn thành phần thi.', 30, 100.00, 0),
    (3, 3, 3, 'Phần Viết', 'WRITING', 'MAIN', 'Hãy viết bài văn ngắn theo yêu cầu của đề bài.', 20, 100.00, 0),
    (4, 4, 4, 'Phần Nói', 'SPEAKING', 'MAIN', 'Hãy thu âm câu trả lời của bạn.', 15, 100.00, 0),
    (5, 5, 5, 'Phần Đọc Nâng Cao', 'READING', 'MAIN', 'Hãy đọc kỹ văn bản và hoàn thành các dạng câu hỏi khác nhau.', 50, 100.00, 0),
    (6, 6, 6, 'Phần Nói Nâng Cao', 'SPEAKING', 'MAIN', 'Hãy thu âm phần trình bày của bạn.', 20, 100.00, 0);

-- 4. Insert seed practice questions
-- Set 1 (Reading) Questions
INSERT INTO practice_questions
    (set_id, group_id, question_no, question_type, prompt, options_json, answer_key, explanation, points, display_order)
VALUES
    (1, NULL, 1, 'MCQ', '다음 글의 내용과 같은 것을 고르십시오. 민수 씨는 주말마다 도서관에 갑니다. 도서관에서 한국어 책을 읽고 새 단어를 정리합니다.',
     '["민수 씨는 평일마다 도서관에 갑니다.", "민수 씨는 한국어 책을 읽습니다.", "민수 씨는 도서관에서 영화를 봅니다.", "민수 씨는 새 단어를 배우지 않습니다."]',
     '2', 'Đáp án 2 đúng vì đoạn văn nói 민수 씨 đọc sách tiếng Hàn ở thư viện.', 50.00, 0),
    (1, NULL, 2, 'MCQ', '빈칸에 알맞은 것을 고르십시오. 저는 매일 아침 한국어를 ______.',
     '["공부합니다", "잡니다", "먹습니다", "삽니다"]',
     '1', 'Động từ 공부합니다 phù hợp với việc học tiếng Hàn.', 50.00, 1);

-- Set 2 (Listening) Questions
INSERT INTO practice_questions
    (set_id, group_id, question_no, question_type, prompt, options_json, answer_key, explanation, points, display_order)
VALUES
    (2, NULL, 1, 'MCQ', 'Transcript: 가: 어디에 가요? 나: 학교에 가요. 질문: 여자는 어디에 갑니까?',
     '["회사", "학교", "시장", "공원"]',
     '2', 'Người nói trả lời 학교에 가요 nên đáp án là 학교.', 100.00, 0);

-- Set 3 (Writing) Questions
INSERT INTO practice_questions
    (set_id, group_id, question_no, question_type, prompt, options_json, answer_key, explanation, points, display_order)
VALUES
    (3, NULL, 1, 'ESSAY', '다음을 읽고 200~300자로 글을 쓰십시오. 한국어를 배우는 이유와 앞으로의 학습 계획을 쓰십시오.',
     NULL, NULL, 'AI sẽ chấm theo 3 tiêu chí: nội dung, bố cục, sử dụng ngôn ngữ.', 100.00, 0);

-- Set 4 (Speaking) Questions
INSERT INTO practice_questions
    (set_id, group_id, question_no, question_type, prompt, options_json, answer_key, explanation, points, display_order)
VALUES
    (4, NULL, 1, 'SPEAKING', 'Bạn hãy giới thiệu ngắn gọn về bản thân và nói vì sao bạn học tiếng Hàn.',
     NULL, NULL, 'Mock evaluator sẽ đánh giá độ dài, mức bám đề và cấu trúc câu trả lời.', 100.00, 0);

-- Set 5 (Reading Extended) Questions
INSERT INTO practice_questions
    (set_id, group_id, question_no, question_type, prompt, options_json, answer_key, explanation, points, display_order)
VALUES
    (5, NULL, 1, 'TRUE_FALSE_NOT_GIVEN', '민수 씨는 주말마다 한국 도서관에서 한국어를 공부합니다. [질문: 민수 씨는 평일에도 도서관에 자주 갑니다.]',
     NULL, 'FALSE', 'Bài đọc ghi rõ 민수 씨 chỉ đi thư viện vào cuối tuần (주말마다), nên nhận định đi vào ngày thường (평일에도) là FALSE.', 15.00, 0),
    (5, NULL, 2, 'MATCHING_INFORMATION', 'Hãy ghép thông tin: (A) 한국 드라마 - phim Hàn, (B) 김치 - Kimchi. Hãy tìm từ tương ứng với: "Món ăn truyền thống làm từ cải thảo của Hàn Quốc."',
     NULL, '김치', 'Kimchi là món ăn truyền thống làm từ cải thảo.', 15.00, 1),
    (5, NULL, 3, 'FILL_BLANK', '저는 어제 시장에 ______ 과일을 샀습니다. (가서 / 가고)',
     NULL, '가서', 'Cấu trúc 아/어서 chỉ trình tự thời gian liên tục: đi chợ rồi mua trái cây.', 20.00, 2),
    (5, NULL, 4, 'ORDERING', 'Hãy sắp xếp các câu sau theo thứ tự đúng: (A) 학교에 도착했습니다. (B) 아침에 일어났습니다. (C) 버스를 탔습니다.',
     NULL, 'B-C-A', 'Thứ tự hành động logic: Thức dậy (B) -> Lên xe bus (C) -> Đến trường (A).', 20.00, 3),
    (5, NULL, 5, 'TEXT_COMPLETION', '한국어 실력을 향상시키기 위해서는 매일 꾸준히 ______ 하는 것이 중요합니다. (연습 / 숙면)',
     NULL, '연습', 'Để nâng cao năng lực tiếng Hàn thì việc luyện tập (연습) đều đặn mỗi ngày rất quan trọng.', 20.00, 4),
    (5, NULL, 6, 'SHORT_TEXT', '대한민국의 수도는 어디입니까?',
     NULL, '서울', 'Thủ đô của Hàn Quốc là Seoul (서울).', 10.00, 5);

-- Set 6 (Speaking Extended) Questions
INSERT INTO practice_questions
    (set_id, group_id, question_no, question_type, prompt, options_json, answer_key, explanation, points, display_order)
VALUES
    (6, NULL, 1, 'SPEAKING', 'Task 1: Hãy giới thiệu về bản thân bạn (Tên, tuổi, nghề nghiệp, nơi sống) bằng tiếng Hàn trong 1 phút.',
     NULL, NULL, 'Task 1 đánh giá khả năng phát âm, từ vựng giới thiệu bản thân cơ bản.', 50.00, 0),
    (6, NULL, 2, 'SPEAKING', 'Task 2: Hãy nêu suy nghĩ của bạn về tầm quan trọng của việc học ngoại ngữ đối với giới trẻ hiện nay.',
     NULL, NULL, 'Task 2 đánh giá lập luận logic, từ vựng trung-cao cấp về chủ đề xã hội.', 50.00, 1);
