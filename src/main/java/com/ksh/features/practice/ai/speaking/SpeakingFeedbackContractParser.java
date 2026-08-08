package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SpeakingFeedbackContractParser {
    private final ObjectMapper objectMapper;
    private final SpeakingEvaluationNormalizer normalizer;

    public SpeakingFeedbackContractParser() {
        this(new ObjectMapper(), new SpeakingEvaluationNormalizer());
    }

    public SpeakingFeedbackContractParser(
            ObjectMapper objectMapper,
            SpeakingEvaluationNormalizer normalizer) {
        this.objectMapper = objectMapper;
        this.normalizer = normalizer;
    }

    public SpeakingEvaluationResult read(JsonNode typed) {
        if (typed == null || !typed.isObject()
                || !typed.has("evaluationStatus")) {
            return normalizer.contractFailure(
                    "SPEAKING_FEEDBACK_CURRENT_CONTRACT_REQUIRED");
        }
        try {
            SpeakingEvaluationResult parsed = objectMapper.treeToValue(
                    typed, SpeakingEvaluationResult.class);
            if (!parsed.currentEvidenceContract()
                    || !rawTypedRubricValuesAreSafe(typed)) {
                return normalizer.contractFailure(
                        "SPEAKING_FEEDBACK_CURRENT_CONTRACT_INVALID");
            }
            return parsed;
        } catch (Exception exception) {
            return normalizer.contractFailure(
                    "SPEAKING_FEEDBACK_JSON_INVALID");
        }
    }

    private boolean rawTypedRubricValuesAreSafe(JsonNode typed) {
        JsonNode rows = typed.get("rubricScores");
        if (rows == null || !rows.isArray()) {
            return true;
        }
        for (JsonNode row : rows) {
            SpeakingRubricCriterion criterion =
                    SpeakingRubricCriterion.fromExternalId(
                            text(row, "criterion"));
            String availability = text(row, "availability");
            boolean carriesNumber = row.hasNonNull("score")
                    || row.hasNonNull("maxScore");
            if (availability != null
                    && !SpeakingCriterionAvailability.SCORED.name()
                    .equals(availability)
                    && carriesNumber) {
                return false;
            }
            if (criterion != null && criterion.requiresAcousticEvidence()
                    && (!SpeakingCriterionAvailability.NOT_SCORABLE.name()
                    .equals(availability) || carriesNumber)) {
                return false;
            }
        }
        return true;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual()
                && !value.asText().isBlank()
                ? value.asText().trim() : null;
    }
}
