package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SpeakingEvaluationNormalizer {
    public static final String SCHEMA_VERSION = "speaking-evaluation-v1";
    public static final String RUBRIC_VERSION = "speaking-rubric-v1";
    public static final String PROMPT_VERSION = "speaking-prompt-v1";
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

            BigDecimal overall = decimal(input, "overall_score");
            if (!percentage(overall)) {
                return invalidProviderResult("INVALID_OVERALL_SCORE");
            }
            List<SpeakingEvaluationResult.RubricScore> rubrics = rubrics(input.path("rubric_scores"));
            if (rubrics.size() != SpeakingRubricCriterion.values().length) {
                return contractFailure("INVALID_RUBRIC_CONTRACT");
            }

            List<SpeakingEvaluationResult.Evidence> evidence = evidence(input.path("evidence"));
            List<String> recommendations = strings(input.path("recommendations"));
            BigDecimal transcriptConfidence = confidence(input, "transcript_confidence");
            if (transcriptConfidence == null && input.hasNonNull("transcript_confidence")) {
                return invalidProviderResult("INVALID_TRANSCRIPT_CONFIDENCE");
            }

            boolean lowConfidence = transcriptConfidence != null
                    && transcriptConfidence.compareTo(LOW_CONFIDENCE) < 0;
            if (lowConfidence) {
                rubrics = applyLowConfidenceCaps(rubrics, evidence);
                recommendations = appendWarning(recommendations,
                        "Low transcript confidence: language and delivery scores are limited when grounded evidence is weak.");
                if (status == SpeakingEvaluationStatus.EVALUATED) {
                    status = SpeakingEvaluationStatus.TRANSCRIPTION_LOW_CONFIDENCE;
                }
            }
            if (status == SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED) {
                rubrics = applyTextFallbackCaps(rubrics);
                recommendations = appendWarning(recommendations,
                        "Text-only fallback: Pronunciation & Delivery is capped because no learner audio was evaluated.");
            }

            BigDecimal rubricTotal = rubrics.stream()
                    .map(SpeakingEvaluationResult.RubricScore::score)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            overall = rubricTotal;

            return new SpeakingEvaluationResult(
                    status,
                    true,
                    source(input, status),
                    text(input, "model"),
                    text(input, "transcription_model"),
                    defaultText(input, "prompt_version", PROMPT_VERSION),
                    defaultText(input, "rubric_version", RUBRIC_VERSION),
                    defaultText(input, "schema_version", SCHEMA_VERSION),
                    longValue(input, "audio_media_id"),
                    longValue(input, "media_version"),
                    text(input, "transcript"),
                    text(input, "normalized_transcript"),
                    text(input, "actually_heard_transcript"),
                    text(input, "interpreted_intent"),
                    confidence(input, "intent_confidence"),
                    transcriptConfidence,
                    text(input, "listener_burden"),
                    overall,
                    text(input, "level_label"),
                    text(input, "overall_summary"),
                    text(input, "task_achievement_summary"),
                    strings(input.path("major_strengths")),
                    strings(input.path("major_needs_improvement")),
                    actionPlan(input.path("action_plan")),
                    criterionFeedback(input.path("criterion_feedback")),
                    transcriptAnnotations(input.path("transcript_annotations")),
                    feedbackItems(input.path("strengths")),
                    feedbackItems(input.path("needs_improvement")),
                    text(input, "confidence_notes"),
                    rubrics,
                    findings(input.path("findings")),
                    evidence,
                    recommendations,
                    text(input, "upgraded_answer"),
                    text(input, "sample_answer"),
                    strings(input.path("pronunciation_advisory")),
                    strings(input.path("fluency_observations")),
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
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(),
                null, null, List.of(), List.of(), errorCategory, retryable);
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
            BigDecimal score = decimal(node, "score");
            if (criterion == null || score == null
                    || score.compareTo(BigDecimal.ZERO) < 0
                    || score.compareTo(criterion.maxScore()) > 0
                    || values.containsKey(criterion)) {
                return List.of();
            }
            values.put(criterion, new SpeakingEvaluationResult.RubricScore(
                    criterion,
                    score.setScale(2, RoundingMode.HALF_UP),
                    criterion.maxScore(),
                    text(node, "feedback")));
        }
        List<SpeakingEvaluationResult.RubricScore> ordered = new ArrayList<>();
        for (SpeakingRubricCriterion criterion : SpeakingRubricCriterion.values()) {
            if (!values.containsKey(criterion)) {
                return List.of();
            }
            ordered.add(values.get(criterion));
        }
        return List.copyOf(ordered);
    }

    private List<SpeakingEvaluationResult.Evidence> evidence(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.Evidence> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingEvidenceSource source = enumValue(
                    SpeakingEvidenceSource.class, text(node, "source"));
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion"));
            BigDecimal confidence = confidence(node, "confidence");
            if (source != null) {
                rows.add(new SpeakingEvaluationResult.Evidence(
                        source, criterion, text(node, "excerpt"), confidence));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.Finding> findings(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.Finding> rows = new ArrayList<>();
        for (JsonNode node : array) {
            if (node.isObject()) {
                rows.add(new SpeakingEvaluationResult.Finding(
                        text(node, "category"),
                        text(node, "message"),
                        text(node, "recommendation")));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.CriterionFeedback> criterionFeedback(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.CriterionFeedback> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            if (criterion == null) {
                criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion"));
            }
            if (criterion != null) {
                rows.add(new SpeakingEvaluationResult.CriterionFeedback(
                        criterion,
                        text(node, "display_name"),
                        decimal(node, "score"),
                        decimal(node, "max_score"),
                        text(node, "level_label"),
                        text(node, "summary"),
                        strings(node.path("strengths")),
                        strings(node.path("needs_improvement")),
                        subcriteria(node.path("subcriteria"))));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.SubCriterionFeedback> subcriteria(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.SubCriterionFeedback> rows = new ArrayList<>();
        for (JsonNode node : array) {
            String id = text(node, "sub_criterion_id");
            if (id == null) {
                id = text(node, "subCriterionId");
            }
            if (id != null) {
                rows.add(new SpeakingEvaluationResult.SubCriterionFeedback(
                        id,
                        text(node, "display_name"),
                        text(node, "level_label"),
                        text(node, "summary"),
                        strings(node.path("strengths")),
                        strings(node.path("needs_improvement"))));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.TranscriptAnnotation> transcriptAnnotations(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.TranscriptAnnotation> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingEvidenceSource source = enumValue(SpeakingEvidenceSource.class, text(node, "evidence_source"));
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            if (source != null) {
                rows.add(new SpeakingEvaluationResult.TranscriptAnnotation(
                        text(node, "annotation_type"),
                        text(node, "category"),
                        criterion,
                        text(node, "sub_criterion_id"),
                        firstText(node, "original_span", "evidence"),
                        firstText(node, "replacement", "suggestion_ko"),
                        firstInt(node, "start_offset", "startOffset"),
                        firstInt(node, "end_offset", "endOffset"),
                        firstText(node, "explanation", "explanation_vi"),
                        text(node, "severity"),
                        source,
                        firstText(node, "evidence_scope", "evidenceScope"),
                        text(node, "evidence"),
                        firstText(node, "explanation_vi", "explanationVi"),
                        firstText(node, "suggestion_ko", "suggestionKo"),
                        confidence(node, "confidence")));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.FeedbackItem> feedbackItems(JsonNode array) {
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
            if (criterion != null && source != null) {
                rows.add(new SpeakingEvaluationResult.FeedbackItem(
                        criterion,
                        firstText(node, "sub_criterion_id", "subcriterionId"),
                        firstText(node, "evidence_scope", "evidenceScope"),
                        text(node, "evidence"),
                        source,
                        firstText(node, "explanation_vi", "explanationVi"),
                        node.has("correction") && node.get("correction").isTextual()
                                ? node.get("correction").asText()
                                : null));
            }
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.ActionPlanItem> actionPlan(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.ActionPlanItem> rows = new ArrayList<>();
        for (JsonNode node : array) {
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(text(node, "criterion_id"));
            rows.add(new SpeakingEvaluationResult.ActionPlanItem(
                    criterion,
                    text(node, "sub_criterion_id"),
                    text(node, "title"),
                    text(node, "instruction"),
                    text(node, "reason"),
                    text(node, "priority")));
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.RubricScore> applyLowConfidenceCaps(
            List<SpeakingEvaluationResult.RubricScore> rubrics,
            List<SpeakingEvaluationResult.Evidence> evidence
    ) {
        return rubrics.stream().map(row -> {
            if (!requiresGroundedEvidence(row.criterion()) || hasGroundedEvidence(evidence, row.criterion())) {
                return row;
            }
            BigDecimal cap = row.maxScore().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            return new SpeakingEvaluationResult.RubricScore(
                    row.criterion(),
                    row.score().min(cap),
                    row.maxScore(),
                    row.feedback());
        }).toList();
    }

    private List<SpeakingEvaluationResult.RubricScore> applyTextFallbackCaps(
            List<SpeakingEvaluationResult.RubricScore> rubrics
    ) {
        return rubrics.stream().map(row -> {
            if (row.criterion() != SpeakingRubricCriterion.PRONUNCIATION_DELIVERY) {
                return row;
            }
            BigDecimal cap = row.maxScore().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            return new SpeakingEvaluationResult.RubricScore(
                    row.criterion(),
                    row.score().min(cap),
                    row.maxScore(),
                    row.feedback());
        }).toList();
    }

    private boolean requiresGroundedEvidence(SpeakingRubricCriterion criterion) {
        return criterion == SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL
                || criterion == SpeakingRubricCriterion.FLUENCY
                || criterion == SpeakingRubricCriterion.PRONUNCIATION_DELIVERY;
    }

    private boolean hasGroundedEvidence(
            List<SpeakingEvaluationResult.Evidence> evidence,
            SpeakingRubricCriterion criterion
    ) {
        return evidence.stream().anyMatch(row -> row.criterion() == criterion
                && (row.source() == SpeakingEvidenceSource.TRANSCRIPT
                || row.source() == SpeakingEvidenceSource.AUDIO_METADATA));
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

    private boolean percentage(BigDecimal value) {
        return value != null
                && value.compareTo(BigDecimal.ZERO) >= 0
                && value.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    private BigDecimal decimal(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private Long longValue(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value != null && value.canConvertToLong() ? value.longValue() : null;
    }

    private Integer intValue(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value != null && value.canConvertToInt() ? value.intValue() : null;
    }

    private Integer firstInt(JsonNode input, String first, String second) {
        Integer value = intValue(input, first);
        return value == null ? intValue(input, second) : value;
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
