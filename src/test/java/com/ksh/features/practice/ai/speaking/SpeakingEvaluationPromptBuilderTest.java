package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.media.AiImageEvidence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingEvaluationPromptBuilderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingEvaluationPromptBuilder builder = new SpeakingEvaluationPromptBuilder(objectMapper);

    @Test
    void userPayloadIncludesTaskMetadataAndTranscriptFieldsSeparately() throws Exception {
        String payload = builder.userPayload(request(false));
        JsonNode root = objectMapper.readTree(payload);

        assertThat(root.path("task").path("question_text").asText()).isEqualTo("자기소개를 하세요.");
        assertThat(root.path("task").path("target_level").asText()).isEqualTo("TOPIK II");
        assertThat(root.path("transcription").path("transcript").asText()).isEqualTo("저는 학생 이에요");
        assertThat(root.path("transcription").path("normalized_transcript").asText()).isEqualTo("저는 학생이에요.");
        assertThat(root.path("transcription").path("actually_heard_transcript").asText()).isEqualTo("저는 학생이에요.");
        assertThat(root.path("transcription").has("interpreted_intent")).isFalse();
        assertThat(root.path("transcription").path("transcript_confidence").decimalValue()).isEqualByComparingTo("0.81");
        assertThat(root.path("allowed_rubric").toString()).contains("S_CONTENT_TASK_FULFILLMENT");
        assertThat(root.path("allowed_rubric").toString())
                .doesNotContain("S_FLUENCY", "S_PRONUNCIATION_DELIVERY");
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
                .contains("evidence_scope")
                .contains("suggestion_ko")
                .contains("S_GRAMMAR_HONORIFIC_REGISTER")
                .doesNotContain("S_FLUENCY")
                .doesNotContain("S_PRONUNCIATION_DELIVERY")
                .doesNotContain("AUDIO_METADATA")
                .doesNotContain("W_CONTENT");
    }

    static SpeakingEvaluationRequest request(boolean textFallback) {
        return request(textFallback, null);
    }

    static SpeakingEvaluationRequest request(boolean textFallback, AiImageEvidence imageEvidence) {
        return new SpeakingEvaluationRequest(
                10L,
                11L,
                "자기소개를 하세요.",
                "TOPIK II",
                "Say who you are and what you study.",
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
                "The learner introduces themself.",
                new BigDecimal("0.81"),
                textFallback,
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION);
    }
}
