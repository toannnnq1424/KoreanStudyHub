package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

/**
 * Production-shaped Speaking v4 fixtures.
 *
 * <p>Tests that claim CURRENT_VERIFIED must use this ledger-backed fixture.
 * Historical constructor-only fixtures intentionally remain untrusted so
 * future contract retirement scans can find and remove them.</p>
 */
public final class SpeakingEvaluationTestFixtures {
    private SpeakingEvaluationTestFixtures() {
    }

    public static ObjectNode providerJson(
            ObjectMapper mapper,
            String transcript
    ) {
        return providerJson(mapper, transcript, new BigDecimal("15"));
    }

    public static ObjectNode providerJson(
            ObjectMapper mapper,
            String transcript,
            BigDecimal contentScore
    ) {
        String source = transcript == null || transcript.isBlank()
                ? "저는 학생이에요." : transcript;
        String hash = sourceHash(source);
        ObjectNode root = mapper.createObjectNode();
        root.put("evaluation_status", "EVALUATED");
        root.put("score_available", false);
        root.put("source", "PROVIDER");
        root.put("model", "assessment-model");
        root.put("transcription_model", "transcription-model");
        root.put("prompt_version", SpeakingPromptRules.PROMPT_VERSION);
        root.put("rubric_version", SpeakingPromptRules.RUBRIC_VERSION);
        root.put("schema_version", SpeakingPromptRules.SCHEMA_VERSION);
        root.put("policy_bundle_id",
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        root.put("policy_bundle_fingerprint",
                SpeakingAssessmentPolicyBundle.fingerprint());
        root.put("actually_heard_transcript", source);
        root.put("transcript", source);
        root.put("normalized_transcript", source);
        root.put("transcript_confidence", 0.91);
        root.putNull("interpreted_intent");
        root.putNull("intent_confidence");
        root.putNull("overall_score");
        root.putNull("level_label");
        root.put("overall_summary",
                "Hồ sơ ngôn ngữ được tổng hợp từ bằng chứng bản chép lời.");
        root.put("task_achievement_summary",
                "Câu trả lời có bằng chứng nội dung đã xác minh.");
        root.put("confidence_notes", "");
        root.put("upgraded_answer", source);
        root.put("sample_answer", source);
        root.put("error_category", "");
        root.put("retryable", false);
        root.putArray("major_strengths");
        root.putArray("major_needs_improvement");
        root.putArray("action_plan");
        root.putArray("strengths");
        root.putArray("needs_improvement");
        root.putArray("findings");
        root.putArray("recommendations");

        ArrayNode evidence = root.putArray("evidence");
        addEvidence(evidence, "SEV-CONTENT-1",
                SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                "S_CONTENT_RELEVANCE", source, hash);
        addEvidence(evidence, "SEV-GRAMMAR-1",
                SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                "S_GRAMMAR_SENTENCE_STRUCTURE", source, hash);
        addEvidence(evidence, "SEV-VOCAB-1",
                SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                "S_VOCAB_WORD_CHOICE", source, hash);
        addEvidence(evidence, "SEV-COHERENCE-1",
                SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                "S_COHERENCE_LOGICAL_FLOW", source, hash);

        ArrayNode rubrics = root.putArray("rubric_scores");
        addRubric(rubrics, SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                contentScore, "SEV-CONTENT-1");
        addRubric(rubrics, SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                new BigDecimal("16"), "SEV-GRAMMAR-1");
        addRubric(rubrics, SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                new BigDecimal("12"), "SEV-VOCAB-1");
        addRubric(rubrics, SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                new BigDecimal("12"), "SEV-COHERENCE-1");

        ArrayNode annotations = root.putArray("transcript_annotations");
        annotations.addObject()
                .put("finding_id", "SF-CONTENT-1")
                .put("evidence_id", "SEV-CONTENT-1")
                .put("criterion_id",
                        SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT.id())
                .put("sub_criterion_id", "S_CONTENT_RELEVANCE")
                .put("evidence_source", "TRANSCRIPT")
                .put("annotation_type", "strength")
                .put("operation", "KEEP")
                .put("category", "CONTENT")
                .put("severity", "LOW")
                .put("confidence", 0.91)
                .put("explanation_vi",
                        "Đoạn trả lời thể hiện nội dung có thể xác minh.")
                .put("suggestion_ko", "");

        ArrayNode criterionFeedback = root.putArray("criterion_feedback");
        ObjectNode content = criterionFeedback.addObject()
                .put("criterion_id",
                        SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT.id())
                .put("display_name", "Provider-owned English heading")
                .put("score", contentScore)
                .put("max_score",
                        SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT
                                .maxScore())
                .put("level_label", "GOOD")
                .put("summary", "Bằng chứng nội dung đã xác minh.");
        content.putArray("strengths").add(
                "Nội dung có bằng chứng.");
        content.putArray("needs_improvement");
        ObjectNode sub = content.putArray("subcriteria").addObject()
                .put("sub_criterion_id", "S_CONTENT_RELEVANCE")
                .put("display_name",
                        "Provider-owned subcriterion heading")
                .put("level_label", "DEVELOPING")
                .put("summary", "Bám đúng nội dung có thể xác minh.");
        sub.putArray("strengths").add("Bám chủ đề.");
        sub.putArray("needs_improvement");
        return root;
    }

    public static SpeakingEvaluationResult currentResult(
            ObjectMapper mapper,
            String transcript,
            BigDecimal contentScore
    ) {
        return new SpeakingEvaluationNormalizer().normalize(
                providerJson(mapper, transcript, contentScore));
    }

    public static SpeakingEvaluationResult currentResult(
            ObjectMapper mapper,
            String transcript,
            BigDecimal contentScore,
            Consumer<ObjectNode> customize
    ) {
        ObjectNode provider =
                providerJson(mapper, transcript, contentScore);
        customize.accept(provider);
        return new SpeakingEvaluationNormalizer().normalize(provider);
    }

    public static SpeakingEvaluationResult currentResultWithFindings(
            ObjectMapper mapper,
            String transcript,
            BigDecimal contentScore,
            List<FindingFixture> findings
    ) {
        ObjectNode provider = providerJson(mapper, transcript, contentScore);
        provider.putArray("transcript_annotations");
        for (FindingFixture finding : findings) {
            addEvidenceSpan(
                    provider.withArray("evidence"),
                    finding.evidenceId(),
                    finding.criterion(),
                    finding.subcriterionId(),
                    transcript,
                    finding.exactText(),
                    finding.startOffset());
            provider.withArray("transcript_annotations").addObject()
                    .put("finding_id", finding.findingId())
                    .put("evidence_id", finding.evidenceId())
                    .put("criterion_id", finding.criterion().id())
                    .put("sub_criterion_id", finding.subcriterionId())
                    .put("evidence_source", "TRANSCRIPT")
                    .put("annotation_type", finding.annotationType())
                    .put("operation", finding.operation())
                    .put("category", finding.category())
                    .put("severity", finding.severity())
                    .put("confidence", finding.confidence())
                    .put("explanation_vi", finding.explanationVi())
                    .put("suggestion_ko", finding.suggestionKo());
        }
        return new SpeakingEvaluationNormalizer().normalize(provider);
    }

    private static void addEvidence(
            ArrayNode target,
            String evidenceId,
            SpeakingRubricCriterion criterion,
            String subcriterionId,
            String source,
            String hash
    ) {
        target.addObject()
                .put("evidence_id", evidenceId)
                .put("source", "TRANSCRIPT")
                .put("criterion_id", criterion.id())
                .put("sub_criterion_id", subcriterionId)
                .put("evidence_scope", "TEXT_SPAN")
                .put("exact_text", source)
                .put("start_offset", 0)
                .put("end_offset", source.length())
                .put("occurrence_index", 1)
                .put("occurrence_count", 1)
                .put("normalization", "UTF16_EXACT_V1")
                .put("source_hash", hash)
                .put("confidence", 0.91);
    }

    private static void addEvidenceSpan(
            ArrayNode target,
            String evidenceId,
            SpeakingRubricCriterion criterion,
            String subcriterionId,
            String source,
            String exactText,
            int startOffset
    ) {
        if (startOffset < 0
                || startOffset + exactText.length() > source.length()
                || !source.startsWith(exactText, startOffset)) {
            throw new IllegalArgumentException(
                    "Fixture offset must identify the exact selected span");
        }
        int occurrenceCount = 0;
        int occurrenceIndex = 0;
        for (int offset = 0;
             offset <= source.length() - exactText.length();
             offset++) {
            if (source.startsWith(exactText, offset)) {
                occurrenceCount++;
                if (offset == startOffset) {
                    occurrenceIndex = occurrenceCount;
                }
            }
        }
        if (occurrenceIndex < 1) {
            throw new IllegalArgumentException(
                    "Fixture occurrence is not present");
        }
        target.addObject()
                .put("evidence_id", evidenceId)
                .put("source", "TRANSCRIPT")
                .put("criterion_id", criterion.id())
                .put("sub_criterion_id", subcriterionId)
                .put("evidence_scope", "TEXT_SPAN")
                .put("exact_text", exactText)
                .put("start_offset", startOffset)
                .put("end_offset", startOffset + exactText.length())
                .put("occurrence_index", occurrenceIndex)
                .put("occurrence_count", occurrenceCount)
                .put("normalization", "UTF16_EXACT_V1")
                .put("source_hash", sourceHash(source))
                .put("confidence", 0.91);
    }

    private static void addRubric(
            ArrayNode target,
            SpeakingRubricCriterion criterion,
            BigDecimal score,
            String evidenceId
    ) {
        ObjectNode row = target.addObject()
                .put("criterion", criterion.id())
                .put("score", score)
                .put("max_score", criterion.maxScore())
                .put("feedback", criterionFeedback(criterion));
        row.putArray("evidence_ids").add(evidenceId);
    }

    private static String criterionFeedback(SpeakingRubricCriterion criterion) {
        return switch (criterion) {
            case CONTENT_TASK_FULFILLMENT ->
                    "Câu trả lời bám chủ đề và nêu được mục tiêu học; mức phát triển ý được phản ánh từ đúng nội dung trong bản chép lời.";
            case GRAMMAR_SENTENCE_CONTROL ->
                    "Đuôi câu và cấu trúc được kiểm soát nhất quán, giúp các quan hệ điều kiện, nguyên nhân và kế hoạch dễ theo dõi.";
            case VOCABULARY_EXPRESSIONS ->
                    "Từ vựng phù hợp chủ đề và các cụm diễn đạt được dùng đúng ngữ cảnh, không chỉ lặp lại từ khóa của đề.";
            case COHERENCE_ORGANIZATION ->
                    "Các dấu hiệu mở ý, kết quả và kết đoạn tạo trình tự rõ ràng cho toàn bộ câu trả lời.";
            case FLUENCY, PRONUNCIATION_DELIVERY ->
                    "Chưa chấm: bộ đánh giá không nhận bằng chứng âm thanh trực tiếp.";
        };
    }

    private static String sourceHash(String source) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record FindingFixture(
            String findingId,
            String evidenceId,
            SpeakingRubricCriterion criterion,
            String subcriterionId,
            String exactText,
            int startOffset,
            String annotationType,
            String operation,
            String category,
            String severity,
            BigDecimal confidence,
            String explanationVi,
            String suggestionKo
    ) {
    }
}
