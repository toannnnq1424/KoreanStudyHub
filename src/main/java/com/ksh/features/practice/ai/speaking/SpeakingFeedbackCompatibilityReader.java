package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpeakingFeedbackCompatibilityReader {
    private final ObjectMapper objectMapper;
    private final SpeakingEvaluationNormalizer normalizer;

    public SpeakingFeedbackCompatibilityReader() {
        this(new ObjectMapper(), new SpeakingEvaluationNormalizer());
    }

    public SpeakingFeedbackCompatibilityReader(ObjectMapper objectMapper, SpeakingEvaluationNormalizer normalizer) {
        this.objectMapper = objectMapper;
        this.normalizer = normalizer;
    }

    public SpeakingEvaluationResult read(JsonNode legacy) {
        if (legacy == null || !legacy.isObject()) {
            return legacyResult(null, List.of(), null, null, null);
        }
        if (legacy.has("evaluationStatus")) {
            try {
                SpeakingEvaluationResult parsed = objectMapper.treeToValue(legacy, SpeakingEvaluationResult.class);
                return parsed.currentEvidenceContract()
                        && rawTypedRubricValuesAreSafe(legacy)
                        ? parsed : legacyUnverified(parsed);
            } catch (Exception ex) {
                return normalizer.contractFailure("SPEAKING_FEEDBACK_JSON_INVALID");
            }
        }
        if (legacy.has("evaluation_status")) {
            // Current application writes use the typed camelCase envelope. A
            // stored snake_case provider payload predates the capability proof
            // and must never be silently upgraded to the current contract.
            return legacyUnverified(
                    normalizer.normalize(legacy),
                    namedLegacyRubricIdentities(legacy.path("rubric_scores")));
        }
        BigDecimal percentage = number(legacy, "percentage");
        if (percentage == null) {
            BigDecimal band = number(legacy, "score");
            percentage = band == null ? null : legacyBandPercentage(band);
        }
        String summary = firstText(legacy, "summary_vi", "summary");
        List<SpeakingEvaluationResult.RubricScore> rubrics = legacyRubrics(legacy.path("rubric_scores"));
        SpeakingEvaluationStatus status = "practice_speaking_mock".equals(text(legacy, "source"))
                ? SpeakingEvaluationStatus.MOCK_EVALUATED
                : SpeakingEvaluationStatus.LEGACY_RESULT;
        return new SpeakingEvaluationResult(
                status,
                percentage != null,
                status == SpeakingEvaluationStatus.MOCK_EVALUATED
                        ? SpeakingEvaluationSource.MOCK : SpeakingEvaluationSource.LEGACY,
                text(legacy, "engine"),
                null,
                null,
                null,
                "legacy-speaking-feedback",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                percentage,
                null,
                summary,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                rubrics,
                List.of(),
                List.of(),
                List.of(),
                text(legacy, "corrected_version"),
                text(legacy, "sample_answer"),
                List.of(),
                List.of(),
                null,
                false);
    }

    private SpeakingEvaluationResult legacyResult(
            BigDecimal score,
            List<SpeakingEvaluationResult.RubricScore> rubrics,
            String upgraded,
            String sample,
            String error
    ) {
        return new SpeakingEvaluationResult(
                SpeakingEvaluationStatus.LEGACY_RESULT,
                score != null,
                SpeakingEvaluationSource.LEGACY,
                null, null, null, null, "legacy-speaking-feedback",
                null, null, null, null, null, null,
                null, null, null, score, null,
                null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null,
                rubrics, List.of(), List.of(), List.of(),
                upgraded, sample, List.of(), List.of(), error, false);
    }

    private SpeakingEvaluationResult legacyUnverified(SpeakingEvaluationResult value) {
        return legacyUnverified(
                value,
                value == null ? List.of() : value.rubricScores());
    }

    private SpeakingEvaluationResult legacyUnverified(
            SpeakingEvaluationResult value,
            List<SpeakingEvaluationResult.RubricScore> identifiedRows
    ) {
        if (value == null) {
            return legacyResult(
                    null,
                    identifiedRows,
                    null,
                    null,
                    "LEGACY_SPEAKING_CONTRACT_UNVERIFIED");
        }
        List<SpeakingEvaluationResult.RubricScore> legacyRows = identifiedRows.stream()
                .map(row -> new SpeakingEvaluationResult.RubricScore(
                        row.criterion(),
                        null,
                        null,
                        row.feedback(),
                        SpeakingCriterionAvailability.LEGACY_UNVERIFIED))
                .toList();
        List<SpeakingEvaluationResult.CriterionFeedback> legacyCriterionFeedback =
                value.criterionFeedback().stream()
                        .map(row -> new SpeakingEvaluationResult.CriterionFeedback(
                                row.criterion(),
                                row.displayName(),
                                null,
                                null,
                                row.levelLabel(),
                                row.summary(),
                                row.strengths(),
                                row.needsImprovement(),
                                row.subcriteria()))
                        .toList();
        return new SpeakingEvaluationResult(
                SpeakingEvaluationStatus.LEGACY_RESULT,
                false,
                SpeakingEvaluationSource.LEGACY,
                value.model(),
                value.transcriptionModel(),
                value.promptVersion(),
                value.rubricVersion(),
                value.schemaVersion(),
                SpeakingEvaluatorCapability.LEGACY_UNKNOWN,
                SpeakingEvidenceMode.UNKNOWN,
                null,
                SpeakingContractTrust.LEGACY_UNVERIFIED,
                value.audioMediaId(),
                value.mediaVersion(),
                value.transcript(),
                value.normalizedTranscript(),
                value.actuallyHeardTranscript(),
                value.interpretedIntent(),
                value.intentConfidence(),
                value.transcriptConfidence(),
                null,
                null,
                null,
                value.overallSummary(),
                value.taskAchievementSummary(),
                value.majorStrengths(),
                value.majorNeedsImprovement(),
                value.actionPlan(),
                legacyCriterionFeedback,
                value.transcriptAnnotations(),
                value.strengths(),
                value.needsImprovement(),
                value.confidenceNotes(),
                legacyRows,
                value.findings(),
                value.evidence(),
                value.recommendations(),
                value.upgradedAnswer(),
                value.sampleAnswer(),
                List.of(),
                List.of(),
                value.errorCategory() == null
                        ? "LEGACY_SPEAKING_CONTRACT_UNVERIFIED" : value.errorCategory(),
                false);
    }

    private boolean rawTypedRubricValuesAreSafe(JsonNode typed) {
        JsonNode rows = typed.get("rubricScores");
        if (rows == null || !rows.isArray()) {
            return true;
        }
        for (JsonNode row : rows) {
            SpeakingRubricCriterion criterion = SpeakingRubricCriterion.fromExternalId(
                    text(row, "criterion"));
            String availability = text(row, "availability");
            boolean carriesNumber = row.hasNonNull("score") || row.hasNonNull("maxScore");
            if (availability != null
                    && !SpeakingCriterionAvailability.SCORED.name().equals(availability)
                    && carriesNumber) {
                return false;
            }
            if (criterion != null && criterion.requiresAcousticEvidence()
                    && (!SpeakingCriterionAvailability.NOT_SCORABLE.name().equals(availability)
                    || carriesNumber)) {
                return false;
            }
        }
        return true;
    }

    private List<SpeakingEvaluationResult.RubricScore> legacyRubrics(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<SpeakingEvaluationResult.RubricScore> rows = new ArrayList<>();
        int index = 0;
        for (JsonNode node : array) {
            if (index >= SpeakingRubricCriterion.values().length) {
                break;
            }
            BigDecimal percentage = number(node, "percentage");
            if (percentage == null) {
                BigDecimal band = number(node, "score");
                percentage = band == null ? null : legacyBandPercentage(band);
            }
            if (percentage != null) {
                SpeakingRubricCriterion criterion = SpeakingRubricCriterion.values()[index];
                rows.add(new SpeakingEvaluationResult.RubricScore(
                        criterion, null, null, text(node, "feedback"),
                        SpeakingCriterionAvailability.LEGACY_UNVERIFIED));
            }
            index++;
        }
        return List.copyOf(rows);
    }

    private List<SpeakingEvaluationResult.RubricScore> namedLegacyRubricIdentities(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        java.util.EnumSet<SpeakingRubricCriterion> seen =
                java.util.EnumSet.noneOf(SpeakingRubricCriterion.class);
        List<SpeakingEvaluationResult.RubricScore> rows = new ArrayList<>();
        for (JsonNode node : array) {
            String externalId = firstText(node, "criterion_id", "criterionId");
            if (externalId == null) {
                externalId = text(node, "criterion");
            }
            SpeakingRubricCriterion criterion =
                    SpeakingRubricCriterion.fromExternalId(externalId);
            if (criterion == null || !seen.add(criterion)) {
                continue;
            }
            rows.add(new SpeakingEvaluationResult.RubricScore(
                    criterion,
                    null,
                    null,
                    null,
                    SpeakingCriterionAvailability.LEGACY_UNVERIFIED));
        }
        return List.copyOf(rows);
    }

    private BigDecimal legacyBandPercentage(BigDecimal band) {
        BigDecimal clamped = band.max(BigDecimal.ONE).min(BigDecimal.valueOf(9));
        return clamped.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(9), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText().trim() : null;
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value == null ? text(node, second) : value;
    }
}
