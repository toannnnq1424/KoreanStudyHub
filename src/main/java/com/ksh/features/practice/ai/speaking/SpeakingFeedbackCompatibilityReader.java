package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpeakingFeedbackCompatibilityReader {

    public SpeakingEvaluationResult read(JsonNode legacy) {
        if (legacy == null || !legacy.isObject()) {
            return legacyResult(null, List.of(), null, null, null);
        }
        BigDecimal percentage = number(legacy, "percentage");
        if (percentage == null) {
            BigDecimal band = number(legacy, "score");
            percentage = band == null ? null : legacyBandPercentage(band);
        }
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
                rubrics, List.of(), List.of(), List.of(),
                upgraded, sample, List.of(), List.of(), error, false);
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
                BigDecimal score = percentage.multiply(criterion.maxScore())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                rows.add(new SpeakingEvaluationResult.RubricScore(
                        criterion, score, criterion.maxScore(), text(node, "feedback")));
            }
            index++;
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
}
