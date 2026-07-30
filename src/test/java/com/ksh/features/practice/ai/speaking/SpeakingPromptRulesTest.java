package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingPromptRulesTest {
    @Test
    void promptRulesIncludeAllowedRubricPolicyAndEvidenceConstraints() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("Chỉ đánh giá tiếng Hàn của người học trong phạm vi luyện tập KSH")
                .contains("không phải điểm Nói TOPIK chính thức")
                .contains("Chỉ chấm các tiêu chí được cung cấp trong allowed_rubric")
                .contains("allowed_rubric cung cấp từng tiêu chí và max_score")
                .contains("Không dùng band 10 điểm")
                .contains("nhãn band 9.0 / 7.5 / 5.0")
                .contains("Không dùng ví dụ hiệu chuẩn few-shot")
                .contains("cấp âm vị")
                .contains("Giá trị nguồn bằng chứng chỉ được là: TRANSCRIPT.")
                .contains("AUDIO_METADATA không phải nguồn căn cứ")
                .contains("không chấp nhận TASK_METADATA hoặc prompt_context")
                .contains("interpreted_intent=null và intent_confidence=null")
                .contains("S_CONTENT_TASK_FULFILLMENT")
                .contains("S_GRAMMAR_SENTENCE_CONTROL")
                .contains("S_VOCABULARY_EXPRESSIONS")
                .contains("S_COHERENCE_ORGANIZATION")
                .contains("Độ lưu loát và Phát âm / Cách thể hiện là NOT_SCORABLE")
                .contains("score_available=false, overall_score=null và level_label=null")
                .contains("Không quy đổi bốn tiêu chí ngôn ngữ thành 100")
                .contains("Nội dung / Hoàn thành nhiệm vụ")
                .contains("Từ vựng / Biểu đạt")
                .contains("Ngữ pháp / Kiểm soát câu")
                .contains("Văn phong / Kính ngữ / Nhất quán đuôi câu")
                .contains("Mạch lạc / Tổ chức ý")
                .contains("action_plan")
                .contains("criterion_feedback")
                .contains("transcript_annotations")
                .contains("upgraded_answer")
                .contains("sample_answer")
                .contains("confidence_notes")
                .contains("không tự giả định trọng số cố định")
                .doesNotContain("S_FLUENCY")
                .doesNotContain("S_PRONUNCIATION_DELIVERY")
                .doesNotContain("AUDIO_METADATA, PROMPT")
                .doesNotContain("criteria with max 20 and max 15");
    }

    @Test
    void promptRulesUseStableKshSectionsAndLanguagePolicy() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("[QUY TẮC CHÍNH SÁCH]")
                .contains("[QUY TẮC CHẤM ALLOWED_RUBRIC]")
                .contains("[QUY TẮC NGUỒN BẰNG CHỨNG]")
                .contains("[TỔNG QUAN VÀ RUBRIC]")
                .contains("[ĐIỂM MẠNH VÀ ĐIỂM CẦN CẢI THIỆN]")
                .contains("[CHÚ THÍCH BẢN CHÉP LỜI]")
                .contains("[CÂU TRẢ LỜI NÂNG CẤP VÀ CÂU MẪU]")
                .contains("[JSON OUTPUT]")
                .contains("[CHÍNH SÁCH NGÔN NGỮ]")
                .contains("Dùng tiếng Việt cho overall_summary")
                .contains("evidence phải là văn bản chính xác từ bản chép lời")
                .contains("không dịch, chuẩn hóa hoặc viết lại evidence")
                .contains("Không dùng tiếng Anh trong phần giải thích dành cho người học");
    }

    @Test
    void promptRulesContainKoreanSpecificChecklistsAndGuardrails() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("이/가")
                .contains("은/는")
                .contains("을/를")
                .contains("관심이 많다")
                .contains("영향을 미치다")
                .contains("문제를 해결하다")
                .contains("말투")
                .contains("높임말")
                .contains("반말/존댓말")
                .contains("Không suy diễn ngập ngừng, khoảng dừng, nhịp nói, tốc độ nói")
                .contains("Độ tin cậy ASR chỉ mô tả độ tin cậy bản chép lời")
                .contains("AUDIO_METADATA chỉ là dữ liệu nguồn");
    }

    @Test
    void promptRulesContainStrictJsonAndSpamGuardrail() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("overall_summary")
                .contains("task_achievement_summary")
                .contains("rubric_scores")
                .contains("strengths")
                .contains("needs_improvement")
                .contains("transcript_annotations")
                .contains("upgraded_answer")
                .contains("sample_answer")
                .contains("confidence_notes")
                .contains("action_plan")
                .contains("[SPAM_DETECTED]")
                .contains("strengths phải rỗng")
                .contains("Không coi các từ đúng ngữ cảnh như TOPIK, AI, K-pop, 2026, Internet, SNS là spam");
    }

    @Test
    void textFallbackRulesMakeNoAudioLimitExplicit() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(true);

        assertThat(prompt)
                .contains("bản nhập chữ dự phòng")
                .contains("Độ lưu loát và Phát âm / Cách thể hiện là NOT_SCORABLE")
                .contains("không có điểm, phần trăm tối đa, trình độ hoặc band")
                .contains("Không giả vờ rằng âm thanh người học đã được đánh giá");
    }

    @Test
    void promptRulesDefineCriterionFeedbackAndActionPlanSchemasExplicitly() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("Mỗi criterion_feedback: criterion_id, display_name, score, max_score, level_label, summary")
                .contains("strengths, needs_improvement, subcriteria")
                .contains("Mỗi subcriteria trong criterion_feedback: sub_criterion_id, display_name, level_label, summary")
                .contains("Mỗi action_plan: criterion_id, sub_criterion_id, title, instruction, reason, priority")
                .contains("Dùng chính xác tên trường snake_case trong JSON schema")
                .contains("Dữ liệu nguồn backend, danh tính bản chép lời, danh tính model/version và media")
                .doesNotContain("pronunciation_advisory")
                .doesNotContain("fluency_observations");
    }
}
