-- Phase 13E local-only result fixtures.
--
-- Safety: this script deliberately switches to the disposable schema created
-- for pre-13E UI review. It must not be included in Flyway or production seed.
-- Visual-fidelity audit authority: VISUAL_ONLY_NON_AUTHORITATIVE.
-- These rows exercise layout density only. They do not accept grading,
-- evidence identity, Writing score reconciliation, Speaking acoustic scoring,
-- or any provider/model behavior.
USE ksh_phase13e_result_ui;

START TRANSACTION;

-- Immutable group hierarchy fixtures. These mirror the published-version
-- authority used by the lecturer preview/player and exercise group-scoped
-- source ownership without changing production seed or mutable authoring data.
INSERT INTO practice_question_group_versions (
    id, published_version_id, section_version_id, group_id, group_label,
    question_from, question_to, instruction, stimulus_type,
    passage_text, transcript_text, image_url, stimulus_provenance_json,
    audio_url, example_json, display_order
) VALUES
    (
        13010, 1, 1, 13010, 'Bài đọc 1',
        1, 2, 'Đọc đoạn văn rồi trả lời các câu hỏi trong nhóm.',
        'PASSAGE',
        CONCAT(
            '민수 씨는 주말마다 도서관에 갑니다. 도서관에서 한국어 책을 읽고 ',
            '새 단어를 공책에 정리합니다. 오전에는 문법을 복습하고 점심을 먹은 뒤에는 ',
            '친구와 짧은 한국어 대화를 연습합니다. 모르는 표현이 나오면 사전을 먼저 찾고 ',
            '예문을 직접 만들어 봅니다. 이런 습관 덕분에 민수 씨는 긴 글을 읽을 때도 ',
            '중요한 정보를 빠르게 찾을 수 있게 되었습니다.',
            CHAR(10), CHAR(10),
            '처음에는 한 페이지를 읽는 데 시간이 오래 걸렸습니다. 익숙하지 않은 문법과 ',
            '전문적인 단어가 한 문장에 함께 나오면 뜻을 짐작하기가 어려웠기 때문입니다. ',
            '그래서 민수 씨는 글을 읽기 전에 제목과 소제목을 먼저 확인하고, 각 문단에서 ',
            '반복되는 표현에 밑줄을 긋는 방법을 사용하기 시작했습니다.',
            CHAR(10), CHAR(10),
            '도서관 사서도 학습에 도움이 되는 자료를 추천해 주었습니다. 쉬운 뉴스 기사와 ',
            '청소년용 과학 잡지는 문장이 비교적 짧고 그림이 많아서 새로운 주제를 이해하기 ',
            '좋았습니다. 민수 씨는 읽은 내용을 세 문장으로 요약한 뒤 친구에게 설명하면서 ',
            '자신이 정확히 이해하지 못한 부분을 다시 찾아보았습니다.',
            CHAR(10), CHAR(10),
            '몇 달이 지나자 변화가 나타났습니다. 예전에는 모르는 단어를 만날 때마다 독서를 ',
            '멈췄지만, 이제는 앞뒤 문장을 보고 의미를 추측한 뒤 중요한 단어만 사전에서 ',
            '확인합니다. 또한 같은 주제의 글을 여러 편 비교하여 필자의 관점과 근거가 어떻게 ',
            '다른지도 정리할 수 있게 되었습니다.',
            CHAR(10), CHAR(10),
            '민수 씨는 앞으로도 이 습관을 유지할 계획입니다. 다음 학기에는 한국어로 진행되는 ',
            '전공 수업을 듣고 싶기 때문에 매주 한 편의 긴 글을 읽고 요약문을 작성하려고 ',
            '합니다. 읽기 기록에는 날짜, 자료의 제목, 핵심 내용, 새로 배운 표현을 남겨 ',
            '학습 과정을 스스로 점검할 예정입니다.'
        ),
        NULL, NULL,
        JSON_OBJECT('source', 'PUBLISHED_IMMUTABLE_SNAPSHOT', 'approved', TRUE),
        NULL, NULL, 0
    ),
    (
        13011, 1, 1, 13011, 'Câu đọc độc lập',
        3, 3, 'Đọc câu ngắn rồi chọn cách hiểu phù hợp.',
        'NONE',
        NULL, NULL, NULL,
        JSON_OBJECT('source', 'PUBLISHED_IMMUTABLE_SNAPSHOT', 'approved', TRUE),
        NULL, NULL, 1
    ),
    (
        13020, 2, 2, 13020, 'Phần nghe 1',
        1, 3, 'Nghe đoạn hội thoại và chọn câu trả lời phù hợp.',
        'AUDIO_TRANSCRIPT',
        NULL,
        CONCAT(
            '가: 오늘 수업이 끝난 뒤에 어디에 가요? ',
            '나: 학교 도서관에 가요. 다음 주 발표를 준비하려고 한국어 자료를 찾을 거예요. ',
            '가: 발표 주제가 뭐예요? ',
            '나: 한국 대학생의 주말 생활이에요. 자료를 읽고 중요한 표현도 정리할 거예요. ',
            '가: 저도 비슷한 자료가 있는데 필요하면 보내 줄게요. ',
            '나: 고마워요. 저녁 전에 확인해 볼게요.',
            CHAR(10), CHAR(10),
            '가: 발표는 혼자 준비해요, 아니면 조별로 준비해요? ',
            '나: 세 명이 한 조예요. 저는 설문 결과를 정리하고, 지수 씨는 인터뷰 내용을 ',
            '요약하기로 했어요. 민호 씨는 발표 자료의 디자인을 맡았어요.',
            CHAR(10), CHAR(10),
            '가: 설문에는 어떤 질문이 들어가요? ',
            '나: 주말에 가장 자주 하는 활동, 친구를 만나는 장소, 여가 시간에 사용하는 ',
            '비용을 물어봤어요. 학년별로 답이 다른지도 비교할 생각이에요.',
            CHAR(10), CHAR(10),
            '가: 자료가 많으면 정리하기 어렵겠네요. ',
            '나: 그래서 오늘 도서관에서 관련 보고서를 먼저 찾아보려고 해요. 이미 발표된 ',
            '통계와 우리 설문 결과를 비교하면 변화의 이유를 더 분명하게 설명할 수 있을 것 같아요.',
            CHAR(10), CHAR(10),
            '가: 발표 연습은 언제 해요? ',
            '나: 금요일 오후에 강의실을 예약했어요. 각자 맡은 부분을 설명한 뒤 연결이 ',
            '자연스러운지 확인하고, 시간이 남으면 예상 질문에 답하는 연습도 할 거예요.',
            CHAR(10), CHAR(10),
            '가: 준비가 체계적이네요. 필요한 자료가 있으면 언제든지 말해 주세요. ',
            '나: 네, 정말 고마워요. 오늘 찾은 자료와 설문 표를 조원들에게 공유한 다음 ',
            '부족한 부분을 다시 확인할게요.'
        ),
        NULL,
        JSON_OBJECT('source', 'PUBLISHED_IMMUTABLE_SNAPSHOT', 'approved', TRUE),
        '/audio/practice/listening-speaker-check.wav?fixtureGroup=1', NULL, 0
    ),
    (
        13021, 2, 2, 13021, 'Phần nghe 2',
        4, 4, 'Nghe thông báo ngắn và chọn thông tin chính xác.',
        'AUDIO_TRANSCRIPT',
        NULL,
        CONCAT(
            '안내 말씀드립니다. 이번 주 토요일 한국 문화 체험 행사는 오전 열 시에 ',
            '학생회관 앞에서 시작합니다. 참가자는 아홉 시 오십 분까지 도착해 주세요.'
        ),
        NULL,
        JSON_OBJECT('source', 'PUBLISHED_IMMUTABLE_SNAPSHOT', 'approved', TRUE),
        '/audio/practice/listening-speaker-check.wav?fixtureGroup=2', NULL, 1
    )
ON DUPLICATE KEY UPDATE
    group_label = VALUES(group_label),
    question_from = VALUES(question_from),
    question_to = VALUES(question_to),
    instruction = VALUES(instruction),
    stimulus_type = VALUES(stimulus_type),
    passage_text = VALUES(passage_text),
    transcript_text = VALUES(transcript_text),
    stimulus_provenance_json = VALUES(stimulus_provenance_json),
    audio_url = VALUES(audio_url),
    display_order = VALUES(display_order);

UPDATE practice_question_versions
SET group_version_id = 13010
WHERE id IN (1, 2) AND published_version_id = 1 AND section_version_id = 1;

UPDATE practice_question_versions
SET group_version_id = 13020
WHERE id = 3 AND published_version_id = 2 AND section_version_id = 2;

UPDATE practice_question_versions
SET options_json = JSON_ARRAY('회사', '학교', '시장'),
    answer_key = '2'
WHERE id = 3 AND published_version_id = 2 AND section_version_id = 2;

INSERT INTO practice_question_versions (
    id, published_version_id, section_version_id, group_version_id,
    question_id, question_no, question_type, prompt, options_json,
    question_content_json, answer_key, answer_spec_json, explanation,
    points, display_order, writing_task_type
) VALUES (
    13011, 1, 1, 13011, 13011, 3, 'SINGLE_CHOICE',
    '다음 문장의 뜻으로 알맞은 것을 고르십시오. 오늘은 비가 오지만 예정대로 산책할 것입니다.',
    JSON_ARRAY(
        'Mưa nên kế hoạch đi dạo bị hủy.',
        'Dù mưa nhưng vẫn sẽ đi dạo như dự định.',
        'Hôm nay trời quang nên sẽ đi dạo.'
    ),
    NULL, '2', NULL,
    '지만 thể hiện quan hệ tương phản; 예정대로 cho biết kế hoạch vẫn được giữ nguyên.',
    1.00, 2, NULL
)
ON DUPLICATE KEY UPDATE
    group_version_id = VALUES(group_version_id),
    question_no = VALUES(question_no),
    prompt = VALUES(prompt),
    options_json = VALUES(options_json),
    answer_key = VALUES(answer_key),
    explanation = VALUES(explanation),
    display_order = VALUES(display_order);

INSERT INTO practice_question_versions (
    id, published_version_id, section_version_id, group_version_id,
    question_id, question_no, question_type, prompt, options_json,
    question_content_json, answer_key, answer_spec_json, explanation,
    points, display_order, writing_task_type
) VALUES
    (
        13031, 2, 2, 13020, 13031, 2, 'SINGLE_CHOICE',
        '여자는 도서관에서 무엇을 하려고 합니까?',
        JSON_ARRAY('친구를 만납니다', '발표 자료를 찾습니다', '영화를 봅니다', '운동을 합니다'),
        NULL, '2', NULL, '발표 준비를 위해 한국어 자료를 찾으려고 합니다.',
        1.00, 1, NULL
    ),
    (
        13032, 2, 2, 13020, 13032, 3, 'SINGLE_CHOICE',
        '발표 주제는 무엇입니까?',
        JSON_ARRAY('한국 음식', '한국 대학생의 주말 생활', '도서관 이용 방법', '운동 습관'),
        NULL, '2', NULL, '발표 주제는 한국 대학생의 주말 생활입니다.',
        1.00, 2, NULL
    ),
    (
        13033, 2, 2, 13021, 13033, 4, 'SINGLE_CHOICE',
        '참가자는 몇 시까지 도착해야 합니까?',
        JSON_ARRAY('아홉 시', '아홉 시 오십 분', '열 시', '열 시 십 분'),
        NULL, '2', NULL, '참가자는 아홉 시 오십 분까지 도착해야 합니다.',
        1.00, 3, NULL
    )
ON DUPLICATE KEY UPDATE
    group_version_id = VALUES(group_version_id),
    question_no = VALUES(question_no),
    prompt = VALUES(prompt),
    options_json = VALUES(options_json),
    answer_key = VALUES(answer_key),
    explanation = VALUES(explanation),
    display_order = VALUES(display_order);

-- Reading question 1 explanation (question version 2, original question 1).
SET @reading_q1_fingerprint = SHA2('phase13e-result-reading-qv2-v1', 256);
SET @reading_q1_explanation = JSON_OBJECT(
    'meaningVi', 'Đoạn văn mô tả thói quen cuối tuần của Min-su tại thư viện.',
    'evidenceQuote', '도서관에서 한국어 책을 읽고 새 단어를 정리합니다.',
    'correctReasonVi', 'Đáp án 2 lặp lại đúng thông tin Min-su đọc sách tiếng Hàn.',
    'relatedTranslationVi', 'Ở thư viện, Min-su đọc sách tiếng Hàn và ghi chép từ mới.',
    'eliminatedOptions', JSON_ARRAY(
        JSON_OBJECT('optionKey', 'option_1', 'reasonVi', 'Bài đọc nói cuối tuần, không phải ngày thường.'),
        JSON_OBJECT('optionKey', 'option_3', 'reasonVi', 'Bài đọc không nhắc đến việc xem phim.'),
        JSON_OBJECT('optionKey', 'option_4', 'reasonVi', 'Min-su có ghi chép từ mới nên phát biểu này trái nội dung.')
    ),
    'learningTipVi', 'Đối chiếu từng chủ ngữ, thời gian và hành động với câu gốc.'
);

INSERT INTO question_explanation_artifacts (
    id, fingerprint, legacy_cache_id, skill, question_type,
    assessment_schema_version, provider_model, prompt_version,
    response_schema_version, explanation_language,
    question_hash, stimulus_hash, answer_spec_hash, media_bundle_hash,
    input_contract_json, status, explanation_json,
    error_category, last_error_message, ready_at, failed_at,
    created_at, updated_at
) VALUES (
    13001, @reading_q1_fingerprint, NULL, 'READING', 'SINGLE_CHOICE',
    'practice-assessment-v1', 'phase13e-fixture', 'fixture-prompt-v1',
    'reading-listening-explanation-v1', 'vi',
    SHA2('phase13e-result-reading-question-1', 256),
    SHA2('phase13e-result-reading-stimulus-1', 256),
    SHA2('phase13e-result-reading-answer-1', 256),
    SHA2('phase13e-result-reading-media-1', 256),
    JSON_OBJECT('fixture', 'phase13e', 'questionVersionId', 2),
    'READY', @reading_q1_explanation,
    NULL, NULL, '2026-07-17 04:00:00', NULL,
    '2026-07-17 04:00:00', '2026-07-17 04:00:00'
)
ON DUPLICATE KEY UPDATE
    fingerprint = @reading_q1_fingerprint,
    status = 'READY',
    explanation_json = @reading_q1_explanation,
    error_category = NULL,
    last_error_message = NULL,
    ready_at = '2026-07-17 04:00:00',
    failed_at = NULL,
    updated_at = '2026-07-17 04:00:00';

-- Reading question 2 explanation (question version 1, original question 2).
SET @reading_q2_fingerprint = SHA2('phase13e-result-reading-qv1-v1', 256);
SET @reading_q2_explanation = JSON_OBJECT(
    'meaningVi', 'Câu hỏi kiểm tra động từ phù hợp với hoạt động học tiếng Hàn.',
    'evidenceQuote', '저는 매일 아침 한국어를 공부합니다.',
    'correctReasonVi', '공부합니다 có nghĩa là học và kết hợp tự nhiên với 한국어를.',
    'relatedTranslationVi', 'Tôi học tiếng Hàn vào mỗi buổi sáng.',
    'eliminatedOptions', JSON_ARRAY(
        JSON_OBJECT('optionKey', 'option_2', 'reasonVi', '잡니다 là ngủ, không phù hợp với tân ngữ 한국어를.'),
        JSON_OBJECT('optionKey', 'option_3', 'reasonVi', '먹습니다 là ăn, không diễn tả hoạt động học.'),
        JSON_OBJECT('optionKey', 'option_4', 'reasonVi', '삽니다 là sống hoặc mua, không phù hợp ngữ cảnh.')
    ),
    'learningTipVi', 'Hãy nhận diện tân ngữ trước rồi chọn động từ có quan hệ nghĩa tự nhiên.'
);

INSERT INTO question_explanation_artifacts (
    id, fingerprint, legacy_cache_id, skill, question_type,
    assessment_schema_version, provider_model, prompt_version,
    response_schema_version, explanation_language,
    question_hash, stimulus_hash, answer_spec_hash, media_bundle_hash,
    input_contract_json, status, explanation_json,
    error_category, last_error_message, ready_at, failed_at,
    created_at, updated_at
) VALUES (
    13002, @reading_q2_fingerprint, NULL, 'READING', 'SINGLE_CHOICE',
    'practice-assessment-v1', 'phase13e-fixture', 'fixture-prompt-v1',
    'reading-listening-explanation-v1', 'vi',
    SHA2('phase13e-result-reading-question-2', 256),
    SHA2('phase13e-result-reading-stimulus-2', 256),
    SHA2('phase13e-result-reading-answer-2', 256),
    SHA2('phase13e-result-reading-media-2', 256),
    JSON_OBJECT('fixture', 'phase13e', 'questionVersionId', 1),
    'READY', @reading_q2_explanation,
    NULL, NULL, '2026-07-17 04:00:00', NULL,
    '2026-07-17 04:00:00', '2026-07-17 04:00:00'
)
ON DUPLICATE KEY UPDATE
    fingerprint = @reading_q2_fingerprint,
    status = 'READY',
    explanation_json = @reading_q2_explanation,
    error_category = NULL,
    last_error_message = NULL,
    ready_at = '2026-07-17 04:00:00',
    failed_at = NULL,
    updated_at = '2026-07-17 04:00:00';

-- Listening question explanation (question version 3, original question 3).
SET @listening_q1_fingerprint = SHA2('phase13e-result-listening-qv3-v1', 256);
SET @listening_q1_explanation = JSON_OBJECT(
    'meaningVi', 'Người nữ được hỏi đang đi đâu.',
    'evidenceQuote', '나: 학교에 가요.',
    'correctReasonVi', '학교에 가요 nghĩa là đi đến trường nên đáp án 2 là chính xác.',
    'relatedTranslationVi', 'Nữ: Tôi đi đến trường.',
    'eliminatedOptions', JSON_ARRAY(
        JSON_OBJECT('optionKey', 'option_1', 'reasonVi', '회사 là công ty, không xuất hiện trong câu trả lời.'),
        JSON_OBJECT('optionKey', 'option_3', 'reasonVi', '시장 là chợ, không phải địa điểm được nói đến.'),
        JSON_OBJECT('optionKey', 'option_4', 'reasonVi', '공원 là công viên, không khớp audio transcript.')
    ),
    'learningTipVi', 'Tập trung nghe danh từ đứng trước tiểu từ chỉ hướng 에.'
);

INSERT INTO question_explanation_artifacts (
    id, fingerprint, legacy_cache_id, skill, question_type,
    assessment_schema_version, provider_model, prompt_version,
    response_schema_version, explanation_language,
    question_hash, stimulus_hash, answer_spec_hash, media_bundle_hash,
    input_contract_json, status, explanation_json,
    error_category, last_error_message, ready_at, failed_at,
    created_at, updated_at
) VALUES (
    13003, @listening_q1_fingerprint, NULL, 'LISTENING', 'SINGLE_CHOICE',
    'practice-assessment-v1', 'phase13e-fixture', 'fixture-prompt-v1',
    'reading-listening-explanation-v1', 'vi',
    SHA2('phase13e-result-listening-question-1', 256),
    SHA2('phase13e-result-listening-stimulus-1', 256),
    SHA2('phase13e-result-listening-answer-1', 256),
    SHA2('phase13e-result-listening-media-1', 256),
    JSON_OBJECT('fixture', 'phase13e', 'questionVersionId', 3),
    'READY', @listening_q1_explanation,
    NULL, NULL, '2026-07-17 04:00:00', NULL,
    '2026-07-17 04:00:00', '2026-07-17 04:00:00'
)
ON DUPLICATE KEY UPDATE
    fingerprint = @listening_q1_fingerprint,
    status = 'READY',
    explanation_json = @listening_q1_explanation,
    error_category = NULL,
    last_error_message = NULL,
    ready_at = '2026-07-17 04:00:00',
    failed_at = NULL,
    updated_at = '2026-07-17 04:00:00';

INSERT INTO question_version_explanation_bindings (
    id, question_version_id, artifact_id, explanation_language, fingerprint, bound_at
) VALUES
    (13001, 2, 13001, 'vi', @reading_q1_fingerprint, '2026-07-17 04:00:00'),
    (13002, 1, 13002, 'vi', @reading_q2_fingerprint, '2026-07-17 04:00:00'),
    (13003, 3, 13003, 'vi', @listening_q1_fingerprint, '2026-07-17 04:00:00')
ON DUPLICATE KEY UPDATE
    artifact_id = IF(question_version_id = 2, 13001,
        IF(question_version_id = 1, 13002, 13003)),
    fingerprint = IF(question_version_id = 2, @reading_q1_fingerprint,
        IF(question_version_id = 1, @reading_q2_fingerprint, @listening_q1_fingerprint)),
    bound_at = '2026-07-17 04:00:00';

SET @writing_answer = CONCAT(
    '저는 한국 드라마를 자막 없이 이해하고 한국 친구들과 자연스럽게 이야기하기 위해 ',
    '한국어를 배우고 있습니다. 한국어를 잘하면 전공 자료를 더 폭넓게 읽을 수 있고 ',
    '한국 문화를 깊이 이해할 수 있다고 생각합니다. 앞으로 매일 새로운 단어를 복습하고 ',
    '일주일에 세 번 듣기와 쓰기 연습을 하겠습니다. 또한 틀린 표현을 기록하고 선생님의 ',
    '피드백을 다시 확인하여 같은 실수를 반복하지 않겠습니다.'
);

SET @writing_feedback = JSON_OBJECT(
    '4', JSON_OBJECT(
        'task_type', 'Q53',
        'engine', 'phase13e-fixture-v1',
        'evaluation_status', 'EVALUATED',
        'evaluation_source', 'FIXTURE',
        'evaluation_reason', 'NONE',
        'evaluation_retryable', FALSE,
        'score_available', TRUE,
        'raw_score', 24,
        'raw_score_max', 30,
        'score', 80,
        'summary_vi', 'Bài viết bám đúng yêu cầu, có kế hoạch học cụ thể và diễn đạt khá tự nhiên.',
        'rubric_scores', JSON_ARRAY(
            JSON_OBJECT(
                'criterionId', 'W_CONTENT_TASK_ACHIEVEMENT',
                'name', 'Hoàn thành nhiệm vụ và Nội dung',
                'score', 10, 'maxScore', 12,
                'feedback', 'Nêu rõ lý do học và kế hoạch, có ví dụ thực tế.'
            ),
            JSON_OBJECT(
                'criterionId', 'W_ORGANIZATION_COHERENCE',
                'name', 'Cấu trúc và Mạch lạc',
                'score', 7, 'maxScore', 9,
                'feedback', 'Trình tự ý hợp lý; có thể chia câu để nhịp bài rõ hơn.'
            ),
            JSON_OBJECT(
                'criterionId', 'W_LANGUAGE_EXPRESSION',
                'name', 'Ngôn ngữ và Biểu đạt',
                'score', 7, 'maxScore', 9,
                'feedback', 'Từ vựng phù hợp và ngữ pháp ổn định, còn ít lặp cấu trúc.'
            )
        ),
        'strengths', JSON_ARRAY(
            JSON_OBJECT(
                'criterionId', 'W_CONTENT_TASK_ACHIEVEMENT',
                'category', 'CONTENT',
                'uiLabel', 'Bám sát yêu cầu',
                'evidenceScope', 'TEXT_SPAN',
                'evidence', '한국어를 배우고 있습니다',
                'explanationVi', 'Mục đích học tiếng Hàn được nêu trực tiếp và rõ ràng.',
                'whyItIsGood', 'Giúp người đọc hiểu ngay trọng tâm bài viết.',
                'severity', 'INFO'
            ),
            JSON_OBJECT(
                'criterionId', 'W_ORGANIZATION_COHERENCE',
                'category', 'COHERENCE',
                'uiLabel', 'Kế hoạch có trình tự',
                'evidenceScope', 'TEXT_SPAN',
                'evidence', '앞으로 매일 새로운 단어를 복습하고',
                'explanationVi', 'Từ nối 앞으로 mở rõ phần kế hoạch tương lai.',
                'whyItIsGood', 'Tạo chuyển ý mạch lạc giữa lý do và hành động.',
                'severity', 'INFO'
            )
        ),
        'needs_improvement', JSON_ARRAY(
            JSON_OBJECT(
                'criterionId', 'W_LANGUAGE_EXPRESSION',
                'category', 'EXPRESSION',
                'uiLabel', 'Đa dạng hóa cấu trúc',
                'evidenceScope', 'TEXT_SPAN',
                'evidence', '수 있고',
                'explanationVi', 'Cấu trúc khả năng được dùng gần nhau; nên thay đổi cách diễn đạt.',
                'correction', '전공 자료를 폭넓게 읽는 데에도 도움이 됩니다.',
                'severity', 'MEDIUM',
                'topikTip', 'Ưu tiên câu gọn, liên kết rõ và tránh lặp đuôi câu.'
            )
        ),
        'annotations', JSON_ARRAY(),
        'upgraded_answer', CONCAT(
            '저는 한국 드라마를 자막 없이 이해하고 한국 친구들과 자연스럽게 소통하기 위해 한국어를 공부합니다. ',
            '한국어 실력이 향상되면 전공 자료를 폭넓게 읽는 데에도 도움이 됩니다. 앞으로는 매일 어휘를 복습하고 ',
            '주 3회 듣기와 쓰기를 연습하겠습니다. 틀린 표현은 따로 기록한 뒤 피드백과 비교하며 고치겠습니다.'
        ),
        'sentence_rewrites', JSON_ARRAY(
            JSON_OBJECT(
                'original', '한국어를 잘하면 전공 자료를 더 폭넓게 읽을 수 있고',
                'upgraded', '한국어 실력이 향상되면 전공 자료를 폭넓게 읽는 데에도 도움이 됩니다.',
                'reason', 'Giảm lặp cấu trúc và hoàn chỉnh ý trong một câu.'
            )
        ),
        'sample_answer', CONCAT(
            '저는 한국 사람들과 직접 소통하고 한국의 사회와 문화를 깊이 이해하기 위해 한국어를 배우고 있습니다. ',
            '앞으로 매일 어휘를 정리하고 뉴스와 팟캐스트를 들으며 표현을 익히겠습니다. 주말에는 짧은 글을 쓰고 ',
            '첨삭 내용을 복습하여 정확하고 자연스럽게 말하고 쓰는 능력을 기르겠습니다.'
        )
    )
);

SET @speaking_answer = CONCAT(
    '안녕하세요. 저는 베트남에서 온 대학생 응우옌 꽝 또안입니다. ',
    '한국 드라마와 음악을 좋아해서 한국어를 공부하기 시작했습니다. ',
    '앞으로 한국 친구들과 자연스럽게 이야기하고 전공 공부에도 한국어를 활용하고 싶습니다.'
);

SET @speaking_feedback = JSON_OBJECT(
    '_contract', 'speaking_ai_v1',
    'speaking_feedback_by_question', JSON_OBJECT(
        '5', JSON_OBJECT(
            'evaluation_status', 'EVALUATED',
            'source', 'PROVIDER',
            'model', 'phase13e-fixture-v1',
            'transcription_model', 'phase13e-transcript-fixture-v1',
            'prompt_version', 'speaking-prompt-v1',
            'rubric_version', 'speaking-rubric-v1',
            'schema_version', 'speaking-evaluation-v1',
            'score_available', FALSE,
            'overall_score', NULL,
            'level_label', 'Không chấm điểm',
            'overall_summary', 'Fixture hình học chỉ hiển thị phản hồi dựa trên transcript; không có điểm tổng hoặc suy luận âm học.',
            'task_achievement_summary', 'Giới thiệu đủ thông tin cá nhân và lý do học tiếng Hàn.',
            'transcript', @speaking_answer,
            'normalized_transcript', @speaking_answer,
            'actually_heard_transcript', @speaking_answer,
            'interpreted_intent', 'Giới thiệu bản thân và động lực học tiếng Hàn.',
            'intent_confidence', 0.96,
            'transcript_confidence', 0.92,
            'listener_burden', 'LOW',
            'major_strengths', JSON_ARRAY(
                'Hoàn thành đầy đủ hai phần của đề.',
                'Từ vựng phù hợp với chủ đề giới thiệu bản thân.'
            ),
            'major_needs_improvement', JSON_ARRAY(
                'Cần nối câu linh hoạt hơn để bài nói bớt đều nhịp.',
                'Cần luyện ngữ điệu cuối câu và phụ âm căng.'
            ),
            'rubric_scores', JSON_ARRAY(
                JSON_OBJECT('criterion_id', 'S_CONTENT_TASK_FULFILLMENT', 'score', 17, 'feedback', 'Đủ ý và bám sát yêu cầu.'),
                JSON_OBJECT('criterion_id', 'S_GRAMMAR_SENTENCE_CONTROL', 'score', 15, 'feedback', 'Cấu trúc đúng, còn thiên về câu đơn.'),
                JSON_OBJECT('criterion_id', 'S_VOCABULARY_EXPRESSIONS', 'score', 12, 'feedback', 'Từ vựng phù hợp và dễ hiểu.'),
                JSON_OBJECT('criterion_id', 'S_COHERENCE_ORGANIZATION', 'score', 12, 'feedback', 'Trình tự giới thiệu hợp lý.'),
                JSON_OBJECT('criterion_id', 'S_FLUENCY', 'score', 11, 'feedback', 'Nhịp nói ổn định nhưng còn vài khoảng ngắt.'),
                JSON_OBJECT('criterion_id', 'S_PRONUNCIATION_DELIVERY', 'score', 11, 'feedback', 'Phát âm nhìn chung rõ, ngữ điệu còn phẳng.')
            ),
            'criterion_feedback', JSON_ARRAY(
                JSON_OBJECT(
                    'criterion_id', 'S_CONTENT_TASK_FULFILLMENT',
                    'display_name', 'Nội dung và hoàn thành nhiệm vụ',
                    'score', 17, 'max_score', 20, 'level_label', 'Tốt',
                    'summary', 'Đáp ứng đủ phần giới thiệu và động lực học.',
                    'strengths', JSON_ARRAY('Thông tin cụ thể và liên quan trực tiếp.'),
                    'needs_improvement', JSON_ARRAY('Có thể bổ sung một mục tiêu ngắn hạn.'),
                    'subcriteria', JSON_ARRAY()
                ),
                JSON_OBJECT(
                    'criterion_id', 'S_GRAMMAR_SENTENCE_CONTROL',
                    'display_name', 'Ngữ pháp và kiểm soát câu',
                    'score', 15, 'max_score', 20, 'level_label', 'Khá',
                    'summary', 'Ngữ pháp chính xác; độ đa dạng cấu trúc ở mức khá.',
                    'strengths', JSON_ARRAY('Dùng đúng liên kết nguyên nhân 아/어서.'),
                    'needs_improvement', JSON_ARRAY('Kết hợp thêm mệnh đề định ngữ.'),
                    'subcriteria', JSON_ARRAY()
                ),
                JSON_OBJECT(
                    'criterion_id', 'S_VOCABULARY_EXPRESSIONS',
                    'display_name', 'Từ vựng và biểu đạt',
                    'score', 12, 'max_score', 15, 'level_label', 'Tốt',
                    'summary', 'Từ vựng đúng chủ đề và tự nhiên.',
                    'strengths', JSON_ARRAY('Dùng tự nhiên 활용하고 싶습니다.'),
                    'needs_improvement', JSON_ARRAY('Có thể thêm từ nối chuyển ý.'),
                    'subcriteria', JSON_ARRAY()
                ),
                JSON_OBJECT(
                    'criterion_id', 'S_COHERENCE_ORGANIZATION',
                    'display_name', 'Mạch lạc và tổ chức',
                    'score', 12, 'max_score', 15, 'level_label', 'Tốt',
                    'summary', 'Mở đầu, lý do và mục tiêu được sắp xếp rõ.',
                    'strengths', JSON_ARRAY('Các ý đi theo trình tự dễ theo dõi.'),
                    'needs_improvement', JSON_ARRAY('Kết bài có thể nhấn lại mục tiêu.'),
                    'subcriteria', JSON_ARRAY()
                ),
                JSON_OBJECT(
                    'criterion_id', 'S_FLUENCY',
                    'display_name', 'Độ lưu loát',
                    'score', 11, 'max_score', 15, 'level_label', 'Khá',
                    'summary', 'Tốc độ vừa phải, còn ngắt nhẹ giữa các cụm.',
                    'strengths', JSON_ARRAY('Duy trì được mạch nói đến hết câu.'),
                    'needs_improvement', JSON_ARRAY('Luyện nói theo cụm nghĩa dài hơn.'),
                    'subcriteria', JSON_ARRAY()
                ),
                JSON_OBJECT(
                    'criterion_id', 'S_PRONUNCIATION_DELIVERY',
                    'display_name', 'Phát âm và truyền đạt',
                    'score', 11, 'max_score', 15, 'level_label', 'Khá',
                    'summary', 'Người nghe hiểu dễ; một số âm và ngữ điệu cần rõ hơn.',
                    'strengths', JSON_ARRAY('Âm tiết và khoảng cách từ nhìn chung rõ.'),
                    'needs_improvement', JSON_ARRAY('Luyện phụ âm căng và ngữ điệu cuối câu.'),
                    'subcriteria', JSON_ARRAY()
                )
            ),
            'strengths', JSON_ARRAY(
                JSON_OBJECT(
                    'criterion_id', 'S_CONTENT_TASK_FULFILLMENT',
                    'evidence_source', 'TRANSCRIPT',
                    'evidence_scope', 'TEXT_SPAN',
                    'evidence', '한국어를 공부하기 시작했습니다',
                    'explanation_vi', 'Nêu trực tiếp động lực bắt đầu học tiếng Hàn.',
                    'correction', NULL
                )
            ),
            'needs_improvement', JSON_ARRAY(
                JSON_OBJECT(
                    'criterion_id', 'S_FLUENCY',
                    'evidence_source', 'TRANSCRIPT',
                    'evidence_scope', 'HOLISTIC',
                    'evidence', 'Toàn bộ câu trả lời',
                    'explanation_vi', 'Các câu có độ dài gần giống nhau nên nhịp nói hơi đều.',
                    'correction', 'Nối hai câu bằng 그래서 hoặc 앞으로도.'
                )
            ),
            'action_plan', JSON_ARRAY(
                JSON_OBJECT(
                    'criterion_id', 'S_FLUENCY',
                    'title', 'Luyện nói theo cụm nghĩa',
                    'instruction', 'Đọc lại bài theo cụm 5 đến 8 âm tiết và ghi âm hai lần.',
                    'reason', 'Giảm khoảng ngắt không cần thiết.',
                    'priority', 'HIGH'
                ),
                JSON_OBJECT(
                    'criterion_id', 'S_PRONUNCIATION_DELIVERY',
                    'title', 'Luyện ngữ điệu kết câu',
                    'instruction', 'Bắt chước ba câu mẫu và so sánh đường cao độ.',
                    'reason', 'Tăng tính tự nhiên khi truyền đạt.',
                    'priority', 'MEDIUM'
                )
            ),
            'evidence', JSON_ARRAY(
                JSON_OBJECT('source', 'TRANSCRIPT', 'criterion', 'S_GRAMMAR_SENTENCE_CONTROL', 'excerpt', @speaking_answer, 'confidence', 0.92),
                JSON_OBJECT('source', 'TRANSCRIPT', 'criterion', 'S_FLUENCY', 'excerpt', @speaking_answer, 'confidence', 0.88),
                JSON_OBJECT('source', 'AUDIO_METADATA', 'criterion', 'S_PRONUNCIATION_DELIVERY', 'excerpt', 'fixture audio metadata', 'confidence', 0.80)
            ),
            'recommendations', JSON_ARRAY(
                'Ghi âm lại cùng nội dung sau khi luyện nối câu.',
                'Đối chiếu phụ âm căng với bản đọc mẫu.'
            ),
            'upgraded_answer', CONCAT(
                '안녕하세요. 저는 베트남에서 온 대학생 응우옌 꽝 또안입니다. 한국 드라마와 음악을 좋아해서 ',
                '한국어 공부를 시작했으며, 지금은 한국 친구들과 자연스럽게 소통하는 것을 목표로 하고 있습니다. ',
                '앞으로 전공 공부에도 한국어를 적극적으로 활용하고 싶습니다.'
            ),
            'sample_answer', CONCAT(
                '안녕하세요. 저는 베트남에서 온 대학생입니다. 한국 문화에 관심이 많아서 한국어를 공부하고 있습니다. ',
                '매일 듣기와 말하기를 연습하며, 앞으로 한국 사람들과 편안하게 대화하고 전공 자료도 읽고 싶습니다.'
            ),
            'pronunciation_advisory', JSON_ARRAY('Phát âm là nhận xét tham khảo trong fixture transcript.'),
            'fluency_observations', JSON_ARRAY('Nhịp nói ổn định, còn vài khoảng ngắt giữa các câu.'),
            'confidence_notes', 'Fixture dùng transcript và metadata mô phỏng để kiểm tra UI.',
            'retryable', FALSE
        )
    )
);

-- Deterministic learner attempts. IDs are intentionally outside normal seed ranges.
INSERT INTO practice_attempts (
    id, user_id, set_id, test_id, skill, section_id,
    status, analysis_status, score, total_points,
    score_unit, earned_points, score_percentage,
    answers_json, ai_feedback_json,
    analysis_requested_at, analysis_completed_at, analysis_engine, analysis_error_code,
    started_at, deadline_at, submitted_at, discarded_at, created_at, updated_at, lock_version,
    published_version_id, set_version_id, test_version_id, section_version_id,
    version_compatibility_status, version_compatibility_note
) VALUES (
    13001, 4, 1, 1, 'READING', 1,
    'GRADED', 'NOT_REQUESTED', 50.00, 100.00,
    'EARNED_POINTS', 50.00, 50.00,
    JSON_OBJECT('1', '2', '2', '3', '13011', '2'), NULL,
    NULL, NULL, NULL, NULL,
    '2026-07-16 20:00:00', '2026-07-16 21:00:00', '2026-07-16 20:24:00', NULL,
    '2026-07-16 20:00:00', '2026-07-17 04:00:00', 0,
    1, 1, 1, 1, NULL, 'Phase 13E deterministic result fixture'
)
ON DUPLICATE KEY UPDATE
    user_id = 4, set_id = 1, test_id = 1, skill = 'READING', section_id = 1,
    status = 'GRADED', analysis_status = 'NOT_REQUESTED',
    score = 50.00, total_points = 100.00,
    score_unit = 'EARNED_POINTS', earned_points = 50.00, score_percentage = 50.00,
    answers_json = JSON_OBJECT('1', '2', '2', '3', '13011', '2'), ai_feedback_json = NULL,
    analysis_requested_at = NULL, analysis_completed_at = NULL,
    analysis_engine = NULL, analysis_error_code = NULL,
    started_at = '2026-07-16 20:00:00', deadline_at = '2026-07-16 21:00:00',
    submitted_at = '2026-07-16 20:24:00',
    discarded_at = NULL, updated_at = '2026-07-17 04:00:00', lock_version = 0,
    published_version_id = 1, set_version_id = 1, test_version_id = 1,
    section_version_id = 1, version_compatibility_status = NULL,
    version_compatibility_note = 'Phase 13E deterministic result fixture';

INSERT INTO practice_attempts (
    id, user_id, set_id, test_id, skill, section_id,
    status, analysis_status, score, total_points,
    score_unit, earned_points, score_percentage,
    answers_json, ai_feedback_json,
    analysis_requested_at, analysis_completed_at, analysis_engine, analysis_error_code,
    started_at, deadline_at, submitted_at, discarded_at, created_at, updated_at, lock_version,
    published_version_id, set_version_id, test_version_id, section_version_id,
    version_compatibility_status, version_compatibility_note
) VALUES (
    13002, 4, 2, 2, 'LISTENING', 2,
    'GRADED', 'NOT_REQUESTED', 100.00, 100.00,
    'EARNED_POINTS', 100.00, 100.00,
    JSON_OBJECT('3', '2', '13031', '1', '13033', '2'), NULL,
    NULL, NULL, NULL, NULL,
    '2026-07-16 20:30:00', '2026-07-16 21:30:00', '2026-07-16 20:47:00', NULL,
    '2026-07-16 20:30:00', '2026-07-17 04:00:00', 0,
    2, 2, 2, 2, NULL, 'Phase 13E deterministic result fixture'
)
ON DUPLICATE KEY UPDATE
    user_id = 4, set_id = 2, test_id = 2, skill = 'LISTENING', section_id = 2,
    status = 'GRADED', analysis_status = 'NOT_REQUESTED',
    score = 100.00, total_points = 100.00,
    score_unit = 'EARNED_POINTS', earned_points = 100.00, score_percentage = 100.00,
    answers_json = JSON_OBJECT('3', '2', '13031', '1', '13033', '2'), ai_feedback_json = NULL,
    analysis_requested_at = NULL, analysis_completed_at = NULL,
    analysis_engine = NULL, analysis_error_code = NULL,
    started_at = '2026-07-16 20:30:00', deadline_at = '2026-07-16 21:30:00',
    submitted_at = '2026-07-16 20:47:00',
    discarded_at = NULL, updated_at = '2026-07-17 04:00:00', lock_version = 0,
    published_version_id = 2, set_version_id = 2, test_version_id = 2,
    section_version_id = 2, version_compatibility_status = NULL,
    version_compatibility_note = 'Phase 13E deterministic result fixture';

INSERT INTO practice_attempts (
    id, user_id, set_id, test_id, skill, section_id,
    status, analysis_status, score, total_points,
    score_unit, earned_points, score_percentage,
    answers_json, ai_feedback_json,
    analysis_requested_at, analysis_completed_at, analysis_engine, analysis_error_code,
    started_at, deadline_at, submitted_at, discarded_at, created_at, updated_at, lock_version,
    published_version_id, set_version_id, test_version_id, section_version_id,
    version_compatibility_status, version_compatibility_note
) VALUES (
    13003, 4, 3, 3, 'WRITING', 3,
    'GRADED', 'SUCCEEDED', 80.00, 100.00,
    'PERCENTAGE', 80.00, 80.00,
    JSON_OBJECT('4', @writing_answer), @writing_feedback,
    '2026-07-16 21:00:00', '2026-07-16 21:21:00',
    'phase13e-fixture-v1', NULL,
    '2026-07-16 20:50:00', '2026-07-16 21:50:00', '2026-07-16 21:21:00', NULL,
    '2026-07-16 20:50:00', '2026-07-17 04:00:00', 0,
    3, 3, 3, 3, NULL, 'Phase 13E deterministic result fixture'
)
ON DUPLICATE KEY UPDATE
    user_id = 4, set_id = 3, test_id = 3, skill = 'WRITING', section_id = 3,
    status = 'GRADED', analysis_status = 'SUCCEEDED',
    score = 80.00, total_points = 100.00,
    score_unit = 'PERCENTAGE', earned_points = 80.00, score_percentage = 80.00,
    answers_json = JSON_OBJECT('4', @writing_answer), ai_feedback_json = @writing_feedback,
    analysis_requested_at = '2026-07-16 21:00:00',
    analysis_completed_at = '2026-07-16 21:21:00',
    analysis_engine = 'phase13e-fixture-v1', analysis_error_code = NULL,
    started_at = '2026-07-16 20:50:00', deadline_at = '2026-07-16 21:50:00',
    submitted_at = '2026-07-16 21:21:00',
    discarded_at = NULL, updated_at = '2026-07-17 04:00:00', lock_version = 0,
    published_version_id = 3, set_version_id = 3, test_version_id = 3,
    section_version_id = 3, version_compatibility_status = NULL,
    version_compatibility_note = 'Phase 13E deterministic result fixture';

INSERT INTO practice_attempts (
    id, user_id, set_id, test_id, skill, section_id,
    status, analysis_status, score, total_points,
    score_unit, earned_points, score_percentage,
    answers_json, ai_feedback_json,
    analysis_requested_at, analysis_completed_at, analysis_engine, analysis_error_code,
    started_at, deadline_at, submitted_at, discarded_at, created_at, updated_at, lock_version,
    published_version_id, set_version_id, test_version_id, section_version_id,
    version_compatibility_status, version_compatibility_note
) VALUES (
    13004, 4, 4, 4, 'SPEAKING', 4,
    'GRADED', 'SUCCEEDED', NULL, NULL,
    NULL, NULL, NULL,
    JSON_OBJECT('5', @speaking_answer), @speaking_feedback,
    '2026-07-16 21:30:00', '2026-07-16 21:42:00',
    'phase13e-fixture-v1', NULL,
    '2026-07-16 21:25:00', '2026-07-16 22:25:00', '2026-07-16 21:42:00', NULL,
    '2026-07-16 21:25:00', '2026-07-17 04:00:00', 0,
    4, 4, 4, 4, NULL, 'Phase 13E deterministic result fixture'
)
ON DUPLICATE KEY UPDATE
    user_id = 4, set_id = 4, test_id = 4, skill = 'SPEAKING', section_id = 4,
    status = 'GRADED', analysis_status = 'SUCCEEDED',
    score = NULL, total_points = NULL,
    score_unit = NULL, earned_points = NULL, score_percentage = NULL,
    answers_json = JSON_OBJECT('5', @speaking_answer), ai_feedback_json = @speaking_feedback,
    analysis_requested_at = '2026-07-16 21:30:00',
    analysis_completed_at = '2026-07-16 21:42:00',
    analysis_engine = 'phase13e-fixture-v1', analysis_error_code = NULL,
    started_at = '2026-07-16 21:25:00', deadline_at = '2026-07-16 22:25:00',
    submitted_at = '2026-07-16 21:42:00',
    discarded_at = NULL, updated_at = '2026-07-17 04:00:00', lock_version = 0,
    published_version_id = 4, set_version_id = 4, test_version_id = 4,
    section_version_id = 4, version_compatibility_status = NULL,
    version_compatibility_note = 'Phase 13E deterministic result fixture';

COMMIT;

SELECT
    id,
    skill,
    status,
    analysis_status,
    score,
    total_points,
    score_percentage,
    published_version_id
FROM practice_attempts
WHERE id BETWEEN 13001 AND 13004
ORDER BY id;

SELECT
    b.question_version_id,
    a.id AS artifact_id,
    a.skill,
    a.status,
    a.explanation_language
FROM question_version_explanation_bindings b
JOIN question_explanation_artifacts a ON a.id = b.artifact_id
WHERE b.question_version_id IN (1, 2, 3)
ORDER BY b.question_version_id;
