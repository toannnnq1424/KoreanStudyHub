package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpeakingEvaluationNormalizer {
    public static final String SCHEMA_VERSION = SpeakingPromptRules.SCHEMA_VERSION;
    public static final String RUBRIC_VERSION = SpeakingPromptRules.RUBRIC_VERSION;
    public static final String PROMPT_VERSION = SpeakingPromptRules.PROMPT_VERSION;
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.50");

    public SpeakingEvaluationResult normalize(JsonNode input) {
        try {
            if (input == null || !input.isObject()) {
                return contractFailure("PROVIDER_MALFORMED_JSON");
            }
            SpeakingEvaluationStatus status = enumValue(
                    SpeakingEvaluationStatus.class, text(input, "evaluation_status"));
            if (status == null) {
                return contractFailure("INVALID_EVALUATION_STATUS");
            }
            if (!status.scoreBearing()) {
                return unavailable(status, input);
            }

            BigDecimal transcriptConfidence = confidence(input, "transcript_confidence");
            if (transcriptConfidence == null && input.hasNonNull("transcript_confidence")) {
                return invalidProviderResult("INVALID_TRANSCRIPT_CONFIDENCE");
            }

            boolean lowConfidence = status == SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE
                    || transcriptConfidence != null
                    && transcriptConfidence.compareTo(LOW_CONFIDENCE) < 0;
            String actuallyHeardTranscript = text(input, "actually_heard_transcript");
            if (actuallyHeardTranscript == null) {
                return invalidProviderResult("MISSING_AUTHORITATIVE_TRANSCRIPT");
            }
            List<SpeakingEvaluationResult.RubricScore> rubrics = lowConfidence
                    ? List.of()
                    : rubrics(input.path("rubric_scores"));
            if (!lowConfidence && rubrics.size() != SpeakingRubricCriterion.values().length) {
                return contractFailure("INVALID_RUBRIC_CONTRACT");
            }

            List<SpeakingEvaluationResult.Evidence> evidence = evidence(
                    input.path("evidence"), actuallyHeardTranscript);
            List<String> recommendations = strings(input.path("recommendations"));
            if (lowConfidence) {
                recommendations = appendWarning(recommendations,
                        "Độ tin cậy của bản chép lời thấp; không tạo hồ sơ điểm ngôn ngữ từ bản chép lời này.");
                status = SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE;
            }

            return new SpeakingEvaluationResult(
                    status,
                    false,
                    source(input, status),
                    text(input, "model"),
                    text(input, "transcription_model"),
                    defaultText(input, "prompt_version", PROMPT_VERSION),
                    defaultText(input, "rubric_version", RUBRIC_VERSION),
                    defaultText(input, "schema_version", SCHEMA_VERSION),
                    SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                    SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                    SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                    SpeakingContractTrust.CURRENT_VERIFIED,
                    longValue(input, "audio_media_id"),
                    longValue(input, "media_version"),
                    text(input, "transcript"),
                    text(input, "normalized_transcript"),
                    actuallyHeardTranscript,
                    null,
                    null,
                    transcriptConfidence,
                    null,
                    null,
                    null,
                    transcriptGroundedText(text(input, "overall_summary")),
                    transcriptGroundedText(text(input, "task_achievement_summary")),
                    transcriptGroundedStrings(input.path("major_strengths")),
                    transcriptGroundedStrings(input.path("major_needs_improvement")),
                    actionPlan(input.path("action_plan")),
                    lowConfidence ? List.of() : criterionFeedback(input.path("criterion_feedback"), rubrics),
                    transcriptAnnotations(input.path("transcript_annotations"), actuallyHeardTranscript),
                    feedbackItems(input.path("strengths"), actuallyHeardTranscript, true),
                    feedbackItems(input.path("needs_improvement"), actuallyHeardTranscript, false),
                    transcriptGroundedText(text(input, "confidence_notes")),
                    rubrics,
                    // The legacy generic finding has no evidence scope or span and
                    // therefore cannot be promoted to CURRENT_VERIFIED output.
                    List.of(),
                    evidence,
                    recommendations.stream().filter(this::transcriptGroundedClaim).toList(),
                    text(input, "upgraded_answer"),
                    text(input, "sample_answer"),
                    List.of(),
                    List.of(),
                    null,
                    input.path("retryable").asBoolean(false));
        } catch (RuntimeException ex) {
            return contractFailure("PROVIDER_CONTRACT_INVALID");
        }
    }

    private SpeakingEvaluationResult unavailable(SpeakingEvaluationStatus status, JsonNode input) {
        return emptyResult(
                status,
                source(input, status),
                text(input, "error_category"),
                input.path("retryable").asBoolean(true));
    }

    public SpeakingEvaluationResult contractFailure(String errorCategory) {
        return emptyResult(
                SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                SpeakingEvaluationSource.SYSTEM,
                errorCategory,
                true);
    }

    public SpeakingEvaluationResult invalidProviderResult(String errorCategory) {
        return emptyResult(
                SpeakingEvaluationStatus.INVALID_PROVIDER_RESULT,
                SpeakingEvaluationSource.SYSTEM,
                errorCategory,
                true);
    }

    private SpeakingEvaluationResult emptyResult(
            SpeakingEvaluationStatus status,
            SpeakingEvaluationSource source,
            String errorCategory,
            boolean retryable
    ) {
        return new SpeakingEvaluationResult(
                status, false, source, null, null,
                PROMPT_VERSION, RUBRIC_VERSION, SCHEMA_VERSION,
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                SpeakingContractTrust.CURRENT_VERIFIED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                errorCategory,
                retryable);
    }

    private List<SpeakingEvaluationResult.RubricScore> rubrics(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        Map<SpeakingRubricCriterion, SpeakingEvaluationResult.RubricScore> values =
                new EnumMap<>(SpeakingRubricCriterion.class);
        for (JsonNode node : array) {
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion"));
            if (criterion == null) {
                criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            }
            if (criterion == null) {
                criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterionId"));
            }
            if (criterion == null) {
                return List.of();
            }
            if (criterion.requiresAcousticEvidence()) {
                // Compatibility input may still contain six rows. Acoustic rows
                // are never accepted as measurements by this capability.
                continue;
            }
            BigDecimal score = decimal(node, "score");
            BigDecimal suppliedMax = decimal(node, "max_score");
            if (score == null
                    || score.compareTo(BigDecimal.ZERO) < 0
                    || score.compareTo(criterion.maxScore()) > 0
                    || (suppliedMax != null && suppliedMax.compareTo(criterion.maxScore()) != 0)
                    || values.containsKey(criterion)) {
                return List.of();
            }
            values.put(criterion, new SpeakingEvaluationResult.RubricScore(
                    criterion,
                    score.setScale(2, RoundingMode.HALF_UP),
                    criterion.maxScore(),
                    transcriptGroundedText(text(node, "feedback"))));
        }
        List<SpeakingEvaluationResult.RubricScore> ordered = new ArrayList<>();
        for (SpeakingRubricCriterion criterion : SpeakingRubricCriterion.values()) {
            if (criterion.requiresAcousticEvidence()) {
                ordered.add(new SpeakingEvaluationResult.RubricScore(
                        criterion,
                        null,
                        null,
                        "Chưa chấm: evaluator không nhận bằng chứng âm thanh.",
                        SpeakingCriterionAvailability.NOT_SCORABLE));
            } else {
                if (!values.containsKey(criterion)) {
                    return List.of();
                }
                ordered.add(values.get(criterion));
            }
        }
        return List.copyOf(ordered);
    }

    private List<SpeakingEvaluationResult.Evidence> evidence(
            JsonNode array,
            String actuallyHeardTranscript
    ) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.Evidence> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingEvidenceSource source = enumValue(
                    SpeakingEvidenceSource.class, text(node, "source"));
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion"));
            BigDecimal confidence = confidence(node, "confidence");
            String excerpt = rawText(node, "excerpt");
            boolean transcriptExcerptValid = source != SpeakingEvidenceSource.TRANSCRIPT
                    || exactTranscriptSpan(excerpt, actuallyHeardTranscript);
            if (source != null && criterion != null
                    && criterion.transcriptGrounded()
                    && evidenceAllowed(source, criterion)
                    && transcriptExcerptValid) {
                rows.add(new SpeakingEvaluationResult.Evidence(
                        source, criterion, excerpt, confidence));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.CriterionFeedback> criterionFeedback(
            JsonNode array,
            List<SpeakingEvaluationResult.RubricScore> rubrics
    ) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.CriterionFeedback> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingRubricCriterion parsedCriterion =
                    SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            if (parsedCriterion == null) {
                parsedCriterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion"));
            }
            final SpeakingRubricCriterion criterion = parsedCriterion;
            if (criterion != null && criterion.transcriptGrounded()
                    && transcriptGroundedClaim(text(node, "summary"))) {
                SpeakingEvaluationResult.RubricScore score = rubrics.stream()
                        .filter(row -> row.criterion() == criterion && row.scored())
                        .findFirst()
                        .orElse(null);
                if (score == null) {
                    continue;
                }
                rows.add(new SpeakingEvaluationResult.CriterionFeedback(
                        criterion,
                        text(node, "display_name"),
                        score.score(),
                        score.maxScore(),
                        text(node, "level_label"),
                        transcriptGroundedText(text(node, "summary")),
                        transcriptGroundedStrings(node.path("strengths")),
                        transcriptGroundedStrings(node.path("needs_improvement")),
                        subcriteria(node.path("subcriteria"), criterion)));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.SubCriterionFeedback> subcriteria(
            JsonNode array,
            SpeakingRubricCriterion parent
    ) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.SubCriterionFeedback> rows = new ArrayList<>();
        for (JsonNode node : array) {
            String id = text(node, "sub_criterion_id");
            if (id == null) {
                id = text(node, "subCriterionId");
            }
            if (parent != null && parent.ownsSubcriterion(id)
                    && transcriptGroundedClaim(text(node, "summary"))) {
                rows.add(new SpeakingEvaluationResult.SubCriterionFeedback(
                        id,
                        text(node, "display_name"),
                        text(node, "level_label"),
                        transcriptGroundedText(text(node, "summary")),
                        transcriptGroundedStrings(node.path("strengths")),
                        transcriptGroundedStrings(node.path("needs_improvement"))));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.TranscriptAnnotation> transcriptAnnotations(
            JsonNode array,
            String actuallyHeardTranscript
    ) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.TranscriptAnnotation> rows = new ArrayList<>();
        Map<String, Integer> nextSpanSearch = new HashMap<>();
        for (JsonNode node : array) {
            SpeakingEvidenceSource source = enumValue(SpeakingEvidenceSource.class, text(node, "evidence_source"));
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            if (source != null && criterion != null && criterion.transcriptGrounded()
                    && evidenceAllowed(source, criterion)
                    && criterion.ownsSubcriterion(text(node, "sub_criterion_id"))
                    && transcriptGroundedClaim(firstText(node, "explanation", "explanation_vi"))) {
                ValidatedEvidence validatedEvidence = validateEvidence(
                        firstText(node, "evidence_scope", "evidenceScope"),
                        rawText(node, "evidence"),
                        source,
                        actuallyHeardTranscript,
                        nextSpanSearch);
                if (validatedEvidence == null) {
                    continue;
                }
                rows.add(new SpeakingEvaluationResult.TranscriptAnnotation(
                        text(node, "annotation_type"),
                        text(node, "category"),
                        criterion,
                        text(node, "sub_criterion_id"),
                        validatedEvidence.textSpan() ? validatedEvidence.evidence() : null,
                        firstText(node, "replacement", "suggestion_ko"),
                        validatedEvidence.startOffset(),
                        validatedEvidence.endOffset(),
                        firstText(node, "explanation", "explanation_vi"),
                        text(node, "severity"),
                        source,
                        validatedEvidence.scope(),
                        validatedEvidence.evidence(),
                        firstText(node, "explanation_vi", "explanationVi"),
                        firstText(node, "suggestion_ko", "suggestionKo"),
                        confidence(node, "confidence")));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.FeedbackItem> feedbackItems(
            JsonNode array,
            String actuallyHeardTranscript,
            boolean strengths
    ) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.FeedbackItem> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            if (criterion == null) {
                criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterionId"));
            }
            SpeakingEvidenceSource source = enumValue(SpeakingEvidenceSource.class, text(node, "evidence_source"));
            if (source == null) {
                source = enumValue(SpeakingEvidenceSource.class, text(node, "evidenceSource"));
            }
            ValidatedEvidence validatedEvidence = validateEvidence(
                    firstText(node, "evidence_scope", "evidenceScope"),
                    rawText(node, "evidence"),
                    source,
                    actuallyHeardTranscript,
                    new HashMap<>());
            if (criterion != null && criterion.transcriptGrounded()
                    && source != null && evidenceAllowed(source, criterion)
                    && criterion.ownsSubcriterion(firstText(node, "sub_criterion_id", "subCriterionId"))
                    && transcriptGroundedClaim(firstText(node, "explanation_vi", "explanationVi"))
                    && (!strengths || "".equals(rawText(node, "correction")))
                    && validatedEvidence != null) {
                rows.add(new SpeakingEvaluationResult.FeedbackItem(
                        criterion,
                        firstText(node, "sub_criterion_id", "subCriterionId"),
                        validatedEvidence.scope(),
                        validatedEvidence.evidence(),
                        source,
                        firstText(node, "explanation_vi", "explanationVi"),
                        node.has("correction") && node.get("correction").isTextual()
                                ? node.get("correction").asText()
                                : null));
            }
        }
        return List.copyOf(rows);
    }

    private ValidatedEvidence validateEvidence(
            String scope,
            String evidence,
            SpeakingEvidenceSource source,
            String actuallyHeardTranscript,
            Map<String, Integer> nextSpanSearch
    ) {
        if (scope == null || source == null) {
            return null;
        }
        return switch (scope.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "TEXT_SPAN" -> validatedTextSpan(
                    evidence, source, actuallyHeardTranscript, nextSpanSearch);
            case "WHOLE_ANSWER" -> source == SpeakingEvidenceSource.TRANSCRIPT
                    && evidence != null && evidence.isEmpty()
                    ? new ValidatedEvidence("WHOLE_ANSWER", "", null, null)
                    : null;
            // This normalizer receives no independently authenticated task-metadata
            // envelope. Provider-authored TASK_METADATA can therefore never become
            // CURRENT_VERIFIED evidence here.
            case "TASK_METADATA" -> null;
            default -> null;
        };
    }

    private ValidatedEvidence validatedTextSpan(
            String evidence,
            SpeakingEvidenceSource source,
            String actuallyHeardTranscript,
            Map<String, Integer> nextSpanSearch
    ) {
        if (source != SpeakingEvidenceSource.TRANSCRIPT
                || !exactTranscriptSpan(evidence, actuallyHeardTranscript)) {
            return null;
        }
        int searchFrom = nextSpanSearch.getOrDefault(evidence, 0);
        int startOffset = actuallyHeardTranscript.indexOf(evidence, searchFrom);
        if (startOffset < 0) {
            return null;
        }
        int endOffset = startOffset + evidence.length();
        nextSpanSearch.put(evidence, endOffset);
        return new ValidatedEvidence("TEXT_SPAN", evidence, startOffset, endOffset);
    }

    private boolean exactTranscriptSpan(String evidence, String actuallyHeardTranscript) {
        return evidence != null && !evidence.isBlank()
                && actuallyHeardTranscript != null
                && actuallyHeardTranscript.contains(evidence);
    }

    private record ValidatedEvidence(
            String scope,
            String evidence,
            Integer startOffset,
            Integer endOffset
    ) {
        private boolean textSpan() {
            return "TEXT_SPAN".equals(scope);
        }
    }

    private List<SpeakingEvaluationResult.ActionPlanItem> actionPlan(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.ActionPlanItem> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            String subcriterion = text(node, "sub_criterion_id");
            if (criterion != null && criterion.transcriptGrounded()
                    && criterion.ownsSubcriterion(subcriterion)
                    && transcriptGroundedClaim(text(node, "title"))
                    && transcriptGroundedClaim(text(node, "instruction"))
                    && transcriptGroundedClaim(text(node, "reason"))) {
                rows.add(new SpeakingEvaluationResult.ActionPlanItem(
                        criterion,
                        subcriterion,
                        text(node, "title"),
                        text(node, "instruction"),
                        text(node, "reason"),
                        text(node, "priority")));
            }
        }
        return List.copyOf(rows);
    }

    private boolean evidenceAllowed(
            SpeakingEvidenceSource source,
            SpeakingRubricCriterion criterion
    ) {
        // The backend authoritatively injects only the transcript into the
        // normalized provider envelope. Provider-authored prompt/intent claims
        // cannot become CURRENT_VERIFIED evidence at this boundary.
        return source == SpeakingEvidenceSource.TRANSCRIPT
                && criterion != null && criterion.transcriptGrounded();
    }

    private List<String> transcriptGroundedStrings(JsonNode array) {
        return strings(array).stream().filter(this::transcriptGroundedClaim).toList();
    }

    private String transcriptGroundedText(String value) {
        return transcriptGroundedClaim(value) ? value : null;
    }

    private boolean transcriptGroundedClaim(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return java.util.stream.Stream.of(
                        "pronunciation", "delivery", "fluency", "hesitation", "pacing",
                        "pause", "rhythm", "intonation", "listener burden", "linking",
                        "batchim", "phoneme", "phát âm", "độ lưu loát", "lưu loát",
                        "ngập ngừng", "nhịp điệu", "ngữ điệu", "nối âm", "tốc độ nói",
                        "gánh nặng người nghe", "발음", "유창", "억양", "리듬", "받침")
                .noneMatch(normalized::contains);
    }

    private List<String> appendWarning(List<String> values, String warning) {
        List<String> result = new ArrayList<>(values);
        result.add(warning);
        return List.copyOf(result);
    }

    private SpeakingEvaluationSource source(JsonNode input, SpeakingEvaluationStatus status) {
        SpeakingEvaluationSource parsed = enumValue(
                SpeakingEvaluationSource.class, text(input, "source"));
        if (parsed != null) {
            return parsed;
        }
        return switch (status) {
            case MOCK_EVALUATED -> SpeakingEvaluationSource.MOCK;
            case LEGACY_RESULT -> SpeakingEvaluationSource.LEGACY;
            case TEXT_FALLBACK_EVALUATED -> SpeakingEvaluationSource.TEXT_FALLBACK;
            default -> SpeakingEvaluationSource.PROVIDER;
        };
    }

    private BigDecimal confidence(JsonNode input, String field) {
        BigDecimal value = decimal(input, field);
        return value != null
                && value.compareTo(BigDecimal.ZERO) >= 0
                && value.compareTo(BigDecimal.ONE) <= 0 ? value : null;
    }

    private BigDecimal decimal(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private Long longValue(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value != null && value.canConvertToLong() ? value.longValue() : null;
    }

    private String defaultText(JsonNode input, String field, String fallback) {
        String value = text(input, field);
        return value == null ? fallback : value;
    }

    private String text(JsonNode input, String field) {
        if (input == null) {
            return null;
        }
        JsonNode value = input.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private String rawText(JsonNode input, String field) {
        if (input == null) {
            return null;
        }
        JsonNode value = input.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private String firstText(JsonNode input, String first, String second) {
        String value = text(input, first);
        return value == null ? text(input, second) : value;
    }

    private List<String> strings(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        array.forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) {
                values.add(node.asText().trim());
            }
        });
        return List.copyOf(values);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
