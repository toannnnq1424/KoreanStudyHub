-- Phase 13E local-only result fixtures.
--
-- Safety: this script deliberately switches to the disposable schema created
-- for pre-13E UI review. It must not be included in Flyway or production seed.
USE ksh_phase13e_result_ui;

START TRANSACTION;

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
            'score_available', TRUE,
            'overall_score', 78,
            'level_label', 'Khá',
            'overall_summary', 'Bài nói hoàn thành đúng nhiệm vụ, nội dung rõ và có trình tự tự nhiên.',
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
    started_at, submitted_at, discarded_at, created_at, updated_at, lock_version,
    published_version_id, set_version_id, test_version_id, section_version_id,
    version_compatibility_status, version_compatibility_note
) VALUES (
    13001, 4, 1, 1, 'READING', 1,
    'GRADED', 'NOT_REQUESTED', 50.00, 100.00,
    'EARNED_POINTS', 50.00, 50.00,
    JSON_OBJECT('1', '2', '2', '3'), NULL,
    NULL, NULL, NULL, NULL,
    '2026-07-16 20:00:00', '2026-07-16 20:24:00', NULL,
    '2026-07-16 20:00:00', '2026-07-17 04:00:00', 0,
    1, 1, 1, 1, NULL, 'Phase 13E deterministic result fixture'
)
ON DUPLICATE KEY UPDATE
    user_id = 4, set_id = 1, test_id = 1, skill = 'READING', section_id = 1,
    status = 'GRADED', analysis_status = 'NOT_REQUESTED',
    score = 50.00, total_points = 100.00,
    score_unit = 'EARNED_POINTS', earned_points = 50.00, score_percentage = 50.00,
    answers_json = JSON_OBJECT('1', '2', '2', '3'), ai_feedback_json = NULL,
    analysis_requested_at = NULL, analysis_completed_at = NULL,
    analysis_engine = NULL, analysis_error_code = NULL,
    started_at = '2026-07-16 20:00:00', submitted_at = '2026-07-16 20:24:00',
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
    started_at, submitted_at, discarded_at, created_at, updated_at, lock_version,
    published_version_id, set_version_id, test_version_id, section_version_id,
    version_compatibility_status, version_compatibility_note
) VALUES (
    13002, 4, 2, 2, 'LISTENING', 2,
    'GRADED', 'NOT_REQUESTED', 100.00, 100.00,
    'EARNED_POINTS', 100.00, 100.00,
    JSON_OBJECT('3', '2'), NULL,
    NULL, NULL, NULL, NULL,
    '2026-07-16 20:30:00', '2026-07-16 20:47:00', NULL,
    '2026-07-16 20:30:00', '2026-07-17 04:00:00', 0,
    2, 2, 2, 2, NULL, 'Phase 13E deterministic result fixture'
)
ON DUPLICATE KEY UPDATE
    user_id = 4, set_id = 2, test_id = 2, skill = 'LISTENING', section_id = 2,
    status = 'GRADED', analysis_status = 'NOT_REQUESTED',
    score = 100.00, total_points = 100.00,
    score_unit = 'EARNED_POINTS', earned_points = 100.00, score_percentage = 100.00,
    answers_json = JSON_OBJECT('3', '2'), ai_feedback_json = NULL,
    analysis_requested_at = NULL, analysis_completed_at = NULL,
    analysis_engine = NULL, analysis_error_code = NULL,
    started_at = '2026-07-16 20:30:00', submitted_at = '2026-07-16 20:47:00',
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
    started_at, submitted_at, discarded_at, created_at, updated_at, lock_version,
    published_version_id, set_version_id, test_version_id, section_version_id,
    version_compatibility_status, version_compatibility_note
) VALUES (
    13003, 4, 3, 3, 'WRITING', 3,
    'GRADED', 'SUCCEEDED', 80.00, 100.00,
    'PERCENTAGE', 80.00, 80.00,
    JSON_OBJECT('4', @writing_answer), @writing_feedback,
    '2026-07-16 21:00:00', '2026-07-16 21:21:00',
    'phase13e-fixture-v1', NULL,
    '2026-07-16 20:50:00', '2026-07-16 21:21:00', NULL,
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
    started_at = '2026-07-16 20:50:00', submitted_at = '2026-07-16 21:21:00',
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
    started_at, submitted_at, discarded_at, created_at, updated_at, lock_version,
    published_version_id, set_version_id, test_version_id, section_version_id,
    version_compatibility_status, version_compatibility_note
) VALUES (
    13004, 4, 4, 4, 'SPEAKING', 4,
    'GRADED', 'SUCCEEDED', 78.00, 100.00,
    'PERCENTAGE', 78.00, 78.00,
    JSON_OBJECT('5', @speaking_answer), @speaking_feedback,
    '2026-07-16 21:30:00', '2026-07-16 21:42:00',
    'phase13e-fixture-v1', NULL,
    '2026-07-16 21:25:00', '2026-07-16 21:42:00', NULL,
    '2026-07-16 21:25:00', '2026-07-17 04:00:00', 0,
    4, 4, 4, 4, NULL, 'Phase 13E deterministic result fixture'
)
ON DUPLICATE KEY UPDATE
    user_id = 4, set_id = 4, test_id = 4, skill = 'SPEAKING', section_id = 4,
    status = 'GRADED', analysis_status = 'SUCCEEDED',
    score = 78.00, total_points = 100.00,
    score_unit = 'PERCENTAGE', earned_points = 78.00, score_percentage = 78.00,
    answers_json = JSON_OBJECT('5', @speaking_answer), ai_feedback_json = @speaking_feedback,
    analysis_requested_at = '2026-07-16 21:30:00',
    analysis_completed_at = '2026-07-16 21:42:00',
    analysis_engine = 'phase13e-fixture-v1', analysis_error_code = NULL,
    started_at = '2026-07-16 21:25:00', submitted_at = '2026-07-16 21:42:00',
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
