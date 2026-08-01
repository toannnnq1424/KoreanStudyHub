package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.media.AiImageEvidence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingEvaluationPromptBuilderTest {
    private static final List<String> TRANSCRIPT_SUBCRITERIA = List.of(
            "S_CONTENT_RELEVANCE",
            "S_CONTENT_PROMPT_COVERAGE",
            "S_CONTENT_SPECIFICITY_EXAMPLES",
            "S_GRAMMAR_PARTICLES",
            "S_GRAMMAR_TENSE_ASPECT",
            "S_GRAMMAR_ENDINGS",
            "S_GRAMMAR_SENTENCE_STRUCTURE",
            "S_GRAMMAR_HONORIFIC_REGISTER",
            "S_GRAMMAR_CONNECTORS",
            "S_VOCAB_TOPIC_WORDS",
            "S_VOCAB_NATURAL_EXPRESSIONS",
            "S_VOCAB_REPETITION_CONTROL",
            "S_VOCAB_WORD_CHOICE",
            "S_COHERENCE_ORGANIZATION",
            "S_COHERENCE_LOGICAL_FLOW",
            "S_COHERENCE_DISCOURSE_MARKERS");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingEvaluationPromptBuilder builder = new SpeakingEvaluationPromptBuilder(objectMapper);

    @Test
    void userPayloadIncludesTaskMetadataAndTranscriptFieldsSeparately() throws Exception {
        String payload = builder.userPayload(request(false));
        JsonNode root = objectMapper.readTree(payload);

        assertThat(root.path("task").has("question_text")).isFalse();
        assertThat(root.path("task").path("question_version_id").asLong())
                .isEqualTo(101L);
        assertThat(root.path("task").path("target_level").asText()).isEqualTo("TOPIK II");
        assertThat(root.path("prompt_context").path("authority").asText())
                .isEqualTo("IMMUTABLE_LECTURER_QUESTION_VERSION_CONTEXT");
        assertThat(root.path("prompt_context").path("text").asText())
                .isEqualTo("자기소개를 하세요.");
        assertThat(root.path("prompt_context").path("learner_evidence").asBoolean())
                .isFalse();
        assertThat(root.path("transcription").path("authority").asText())
                .isEqualTo("LEARNER_MEDIA_TRANSCRIPTION_PIPELINE");
        assertThat(root.path("transcription").path("transcript").asText()).isEqualTo("저는 학생 이에요");
        assertThat(root.path("transcription").path("normalized_transcript").asText()).isEqualTo("저는 학생이에요.");
        assertThat(root.path("transcription").path("actually_heard_transcript").asText()).isEqualTo("저는 학생이에요.");
        assertThat(root.path("transcription").has("interpreted_intent")).isFalse();
        assertThat(root.path("transcription").path("transcript_confidence").decimalValue()).isEqualByComparingTo("0.81");
        assertThat(root.path("allowed_rubric").toString()).contains("S_CONTENT_TASK_FULFILLMENT");
        assertThat(root.path("allowed_rubric").toString())
                .doesNotContain("S_FLUENCY", "S_PRONUNCIATION_DELIVERY");
        assertThat(root.path("allowed_subcriteria"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(
                        TRANSCRIPT_SUBCRITERIA);
        assertThat(root.path("evaluator_capability")
                .path("not_scorable_criteria"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "S_FLUENCY",
                        "S_PRONUNCIATION_DELIVERY");
        assertThat(root.path("allowed_evidence_sources")).hasSize(1);
        assertThat(root.path("allowed_evidence_sources").get(0).asText()).isEqualTo("TRANSCRIPT");
        assertThat(root.path("evaluator_capability").path("capability").asText())
                .isEqualTo("TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION");
        assertThat(root.path("evaluator_capability").path("learner_audio_received_by_evaluator").asBoolean())
                .isFalse();
        assertThat(root.path("evaluator_capability").path("acoustic_criteria_available").asBoolean())
                .isFalse();
        assertThat(root.path("evaluator_capability").path("holistic_score_available").asBoolean())
                .isFalse();
        assertThat(root.path("versions").path("prompt_version").asText())
                .isEqualTo(SpeakingPromptRules.PROMPT_VERSION);
        assertThat(root.path("versions").path("rubric_version").asText())
                .isEqualTo(SpeakingPromptRules.RUBRIC_VERSION);
        assertThat(root.path("versions").path("schema_version").asText())
                .isEqualTo(SpeakingPromptRules.SCHEMA_VERSION);
        assertThat(root.path("versions").path("evidence_contract_version").asText())
                .isEqualTo(SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION);
        assertThat(root.path("policy_bundle_id").asText())
                .isEqualTo(
                        SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(root.path("policy_bundle_fingerprint").asText())
                .isEqualTo(SpeakingAssessmentPolicyBundle.fingerprint());
        assertThat(root.path("versions").path("policy_bundle_id").asText())
                .isEqualTo(
                        SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(root.path("versions")
                .path("policy_bundle_fingerprint").asText())
                .isEqualTo(SpeakingAssessmentPolicyBundle.fingerprint());
        assertThat(root.path("score_policy").asText())
                .startsWith("Chỉ lập hồ sơ ngôn ngữ KSH");
        assertThat(root.path("required_output").asText())
                .startsWith("Trả về JSON nghiêm ngặt");
    }

    @Test
    void payloadAndToStringExcludeSensitiveStoragePathPlaybackUserAndApiValues() {
        String payload = builder.userPayload(request(false));
        String requestString = request(false).toString();

        assertThat(payload)
                .doesNotContain("audio_metadata")
                .doesNotContain("audio_media_id")
                .doesNotContain("media_version")
                .doesNotContain("duration_ms")
                .doesNotContain("byte_size")
                .doesNotContain("audio/webm")
                .doesNotContain("storage-key-secret")
                .doesNotContain("D:\\private\\audio.webm")
                .doesNotContain("/practice/speaking-media/private")
                .doesNotContain("user@example.com")
                .doesNotContain("api-secret");
        assertThat(requestString)
                .doesNotContain("저는 학생")
                .doesNotContain("자기소개")
                .doesNotContain("api-secret")
                .doesNotContain("storage-key-secret");
    }

    @Test
    void systemPromptRequiresContextualEvidenceInsteadOfFrequencyOrChecklistScoring() {
        String prompt = builder.systemPrompt(request(false));

        assertThat(prompt)
                .contains("Số lần một từ/cụm xuất hiện chỉ là tín hiệu để kiểm tra")
                .contains("Không tự động coi một từ xuất hiện hai lần")
                .contains("nhấn mạnh có chủ đích")
                .contains("operation=REDUNDANT")
                .contains("gắn evidence vào đúng occurrence dư thừa")
                .contains("allowed_subcriteria là danh mục được phép")
                .contains("Nếu còn hai cách hiểu hợp lý")
                .contains("không được lặp tên chip")
                .contains("không dùng lời khuyên chung chung");
    }

    @Test
    @SuppressWarnings("unchecked")
    void responseFormatUsesStrictJsonSchemaAndSpeakingCriterionIds() {
        Map<String, Object> responseFormat = builder.responseFormat(request(false));
        Map<String, Object> jsonSchema = (Map<String, Object>) responseFormat.get("json_schema");
        Map<String, Object> schema = (Map<String, Object>) jsonSchema.get("schema");

        assertThat(responseFormat.get("type")).isEqualTo("json_schema");
        assertThat(jsonSchema.get("strict")).isEqualTo(Boolean.TRUE);
        assertThat(schema.toString())
                .contains("S_CONTENT_TASK_FULFILLMENT")
                .contains("S_GRAMMAR_SENTENCE_CONTROL")
                .contains("S_VOCABULARY_EXPRESSIONS")
                .contains("S_COHERENCE_ORGANIZATION")
                .contains("overall_summary")
                .contains("task_achievement_summary")
                .contains("action_plan")
                .contains("criterion_feedback")
                .contains("strengths")
                .contains("needs_improvement")
                .contains("confidence_notes")
                .contains("const=false")
                .contains("minItems=4")
                .contains("maxItems=4")
                .contains("subcriteria")
                .contains("transcript_annotations")
                .contains("finding_id")
                .contains("evidence_id")
                .contains("evidence_ids")
                .contains("start_offset")
                .contains("end_offset")
                .contains("occurrence_index")
                .contains("occurrence_count")
                .contains("UTF16_EXACT_V1")
                .contains("source_hash")
                .contains("operation")
                .contains("evidence_scope")
                .contains("suggestion_ko")
                .contains("S_GRAMMAR_HONORIFIC_REGISTER")
                .doesNotContain("S_FLUENCY")
                .doesNotContain("S_PRONUNCIATION_DELIVERY")
                .doesNotContain("AUDIO_METADATA")
                .doesNotContain("WHOLE_ANSWER")
                .doesNotContain("W_CONTENT");
        assertThat(TRANSCRIPT_SUBCRITERIA)
                .allSatisfy(subcriterion ->
                        assertThat(schema.toString())
                                .contains(subcriterion));
    }

    static SpeakingEvaluationRequest request(boolean textFallback) {
        return request(textFallback, null);
    }

    static SpeakingEvaluationRequest request(boolean textFallback, AiImageEvidence imageEvidence) {
        return new SpeakingEvaluationRequest(
                10L,
                11L,
                101L,
                "자기소개를 하세요.",
                "f".repeat(64),
                "speaking-prompt-version-context-v1",
                "자기소개를 하세요.",
                "TOPIK II",
                "Hãy nói bạn là ai và đang học gì.",
                imageEvidence,
                textFallback ? null : 12L,
                textFallback ? null : 13L,
                textFallback ? null : "audio/webm",
                textFallback ? null : 12345L,
                textFallback ? null : 3200L,
                textFallback ? "text" : "openai",
                textFallback ? null : "gpt-4o-mini-transcribe",
                "ko",
                "저는 학생 이에요",
                "저는 학생이에요.",
                "저는 학생이에요.",
                "Học viên đang tự giới thiệu.",
                new BigDecimal("0.81"),
                textFallback,
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION,
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                SpeakingEvaluatorCapability
                        .TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION);
    }
}
