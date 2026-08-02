package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Production-shaped Writing v8 fixtures.
 *
 * <p>Tests must provide offsets explicitly. This helper calculates only the
 * occurrence identity for an already selected offset and never chooses a
 * matching occurrence on behalf of a test.</p>
 */
public final class WritingContractTestFixtures {

    private WritingContractTestFixtures() {
    }

    public static ObjectNode zeroEnvelope(
            ObjectMapper mapper,
            String taskType,
            String learnerAnswer) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion",
                WritingPromptRules.EVALUATION_SCHEMA_VERSION);
        root.put("promptVersion", WritingPromptRules.PROMPT_VERSION);
        root.put("scoreAnchorVersion", WritingScoreAnchorPolicy.VERSION);
        root.put("taskRequirementVersion",
                WritingTaskRequirementPolicy.VERSION);
        ArrayNode scores = root.putArray("rubricScores");
        for (WritingScoringCriterion criterion
                : WritingScoringPolicy.rubricFor(taskType).criteria()) {
            ObjectNode row = scores.addObject();
            row.put("criterionId", criterion.criterionId());
            row.put("score", 0);
            row.put("maxScore", criterion.maxScore());
            row.putArray("evidenceIds");
            row.putArray("findingIds");
            ArrayNode requirementIds = row.putArray("requirementIds");
            WritingTaskRequirementPolicy.requirementsFor(taskType).stream()
                    .filter(requirement -> criterion.criterionId().equals(
                            requirement.scoringCriterionId()))
                    .map(WritingTaskRequirementPolicy.Requirement
                            ::requirementId)
                    .forEach(requirementIds::add);
        }
        ArrayNode coverage = root.putArray("taskCoverage");
        for (WritingTaskRequirementPolicy.Requirement requirement
                : WritingTaskRequirementPolicy.requirementsFor(taskType)) {
            ObjectNode row = coverage.addObject();
            row.put("requirementId", requirement.requirementId());
            row.put("status", "NOT_MET");
            row.putArray("evidenceIds");
        }
        root.putArray("evidenceLedger");
        root.putArray("findings");
        ObjectNode upgrade = root.putObject("upgradedAnswer");
        upgrade.put("content", "");
        upgrade.putArray("rewrites");
        return root;
    }

    public static ObjectNode addEvidence(
            ObjectNode envelope,
            String evidenceId,
            String learnerAnswer,
            String exactText,
            int startOffset) {
        if (startOffset < 0
                || startOffset + exactText.length() > learnerAnswer.length()
                || !learnerAnswer.startsWith(exactText, startOffset)) {
            throw new IllegalArgumentException(
                    "Fixture offset must identify the exact selected span");
        }
        List<Integer> occurrences = occurrences(learnerAnswer, exactText);
        int occurrenceIndex = 0;
        for (int index = 0; index < occurrences.size(); index++) {
            if (occurrences.get(index) == startOffset) {
                occurrenceIndex = index + 1;
                break;
            }
        }
        if (occurrenceIndex < 1) {
            throw new IllegalArgumentException(
                    "Fixture occurrence is not present");
        }
        ObjectNode row = envelope.withArray("evidenceLedger").addObject();
        row.put("evidenceId", evidenceId);
        row.put("sourceRole", WritingEvidenceLedgerVerifier.SOURCE_ROLE);
        row.put("exactText", exactText);
        row.put("startOffset", startOffset);
        row.put("endOffset", startOffset + exactText.length());
        row.put("occurrenceIndex", occurrenceIndex);
        row.put("occurrenceCount", occurrences.size());
        row.put("normalization",
                WritingEvidenceLedgerVerifier.SOURCE_NORMALIZATION);
        row.put("sourceHash",
                WritingEvidenceLedgerVerifier.sha256(learnerAnswer));
        return row;
    }

    public static ObjectNode addFinding(
            ObjectNode envelope,
            String findingId,
            String polarity,
            String operation,
            String criterionId,
            String subtype,
            String scoringCriterionId,
            String evidenceId,
            List<String> requirementIds,
            String explanationVi,
            String replacementKo,
            String impact) {
        WritingRubricCriterion criterion =
                WritingRubricCriterion.parse(criterionId);
        ObjectNode row = envelope.withArray("findings").addObject();
        row.put("findingId", findingId);
        row.put("polarity", polarity);
        row.put("operation", operation);
        row.put("criterionId", criterionId);
        row.put("subtype", subtype);
        if (scoringCriterionId == null) {
            row.putNull("scoringCriterionId");
        } else {
            row.put("scoringCriterionId", scoringCriterionId);
        }
        row.put("errorCategory",
                WritingDiagnosticContract.categoryCode(criterion));
        ArrayNode evidenceIds = row.putArray("evidenceIds");
        if (evidenceId != null) {
            evidenceIds.add(evidenceId);
        }
        ArrayNode requirements = row.putArray("requirementIds");
        requirementIds.forEach(requirements::add);
        row.put("explanationVi", explanationVi);
        row.put("replacementKo", replacementKo);
        row.put("impact", impact);
        row.put("frequency", 1);
        row.put("confidence", 0.95);
        row.put("observability",
                evidenceId == null ? "INFERRED_BOUNDED" : "DIRECT");
        return row;
    }

    public static ObjectNode rubric(
            ObjectNode envelope,
            String criterionId) {
        for (JsonNode row : envelope.withArray("rubricScores")) {
            if (criterionId.equals(row.path("criterionId").asText())) {
                return (ObjectNode) row;
            }
        }
        throw new IllegalArgumentException(
                "Unknown fixture rubric criterion");
    }

    public static ObjectNode coverage(
            ObjectNode envelope,
            String requirementId) {
        for (JsonNode row : envelope.withArray("taskCoverage")) {
            if (requirementId.equals(
                    row.path("requirementId").asText())) {
                return (ObjectNode) row;
            }
        }
        throw new IllegalArgumentException(
                "Unknown fixture task requirement");
    }

    public static void replaceIds(
            ObjectNode row,
            String field,
            String... ids) {
        ArrayNode values = row.putArray(field);
        for (String id : ids) {
            values.add(id);
        }
    }

    /**
     * Creates a deterministic learner answer whose declared deductions are
     * observable in the fixture itself.
     *
     * <p>The phrase {@code 어색} is intentionally an incomplete sentence so a
     * language-expression deduction can own an exact, truthful span. A zero
     * essay score uses a just-outside length so deterministic length coverage
     * does not contradict the zero content anchor.</p>
     */
    public static String scoreBearingLearnerAnswer(
            String taskType,
            int rawScore) {
        String base = "문장입니다. 어색";
        int length = switch (taskType) {
            case "Q53" -> rawScore == 0 ? 199 : 200;
            case "Q54" -> rawScore == 0 ? 599 : 600;
            default -> base.length();
        };
        if (length <= base.length()) {
            return base;
        }
        return base + "가".repeat(length - base.length());
    }

    /**
     * Applies a score to the strict V3 envelope without inventing unsupported
     * score authority.
     *
     * <p>Essay content deductions are explained by unmet task requirements.
     * Organization and language deductions own explicit findings. This generic
     * helper keeps cloze criteria full-or-zero; tests that exercise partial
     * cloze scores must add a finding linked to exactly one
     * {@code CLOZE_BLANK_n_CONTEXT} requirement.</p>
     */
    public static void applyRawScore(
            ObjectNode envelope,
            String taskType,
            String learnerAnswer,
            int rawScore) {
        List<WritingScoringCriterion> criteria =
                WritingScoringPolicy.rubricFor(taskType).criteria();
        int maximum = criteria.stream()
                .mapToInt(WritingScoringCriterion::maxScore)
                .sum();
        if (rawScore < 0 || rawScore > maximum) {
            throw new IllegalArgumentException(
                    "Fixture raw score is outside the task-native range");
        }

        int[] scores = isCloze(taskType)
                ? fullOrZeroClozeScores(criteria, rawScore)
                : greedyEssayScores(criteria, rawScore);
        boolean hasPositiveScore = java.util.Arrays.stream(scores)
                .anyMatch(score -> score > 0);
        if (hasPositiveScore) {
            addEvidence(
                    envelope,
                    "EV_SCORE",
                    learnerAnswer,
                    learnerAnswer.substring(0, 1),
                    0);
        }

        for (int index = 0; index < criteria.size(); index++) {
            WritingScoringCriterion criterion = criteria.get(index);
            ObjectNode rubric = rubric(
                    envelope, criterion.criterionId());
            rubric.put("score", scores[index]);
            if (scores[index] > 0) {
                replaceIds(rubric, "evidenceIds", "EV_SCORE");
            }
        }

        for (WritingTaskRequirementPolicy.Requirement requirement
                : WritingTaskRequirementPolicy.requirementsFor(taskType)) {
            ObjectNode row = coverage(
                    envelope, requirement.requirementId());
            boolean deterministicLength = requirement.requirementId()
                    .endsWith("_LENGTH_200_300")
                    || requirement.requirementId()
                    .endsWith("_LENGTH_600_700");
            boolean met;
            if (deterministicLength) {
                int length = learnerAnswer.strip().length();
                met = "Q53".equals(taskType)
                        ? length >= 200 && length <= 300
                        : length >= 600 && length <= 700;
            } else if (requirement.scoringCriterionId() == null) {
                met = false;
            } else {
                int criterionIndex = criterionIndex(
                        criteria, requirement.scoringCriterionId());
                met = criterionIndex >= 0
                        && scores[criterionIndex]
                        == criteria.get(criterionIndex).maxScore();
            }
            row.put("status", met ? "MET" : "NOT_MET");
            if (met && requirement.evidenceRequired()) {
                replaceIds(row, "evidenceIds", "EV_SCORE");
            }
        }

        if (!isCloze(taskType)) {
            addEssayDeductionFindings(
                    envelope, taskType, learnerAnswer, criteria, scores);
        }
    }

    private static void addEssayDeductionFindings(
            ObjectNode envelope,
            String taskType,
            String learnerAnswer,
            List<WritingScoringCriterion> criteria,
            int[] scores) {
        int organization = criterionIndex(
                criteria, "W_ORGANIZATION_COHERENCE");
        if (organization >= 0
                && scores[organization]
                < criteria.get(organization).maxScore()) {
            addFinding(
                    envelope,
                    "F_ORGANIZATION_DEDUCTION",
                    "IMPROVEMENT",
                    "MISSING",
                    "W_LOGICAL_FLOW_ISSUES",
                    "LOGICAL_RELATION",
                    "W_ORGANIZATION_COHERENCE",
                    null,
                    List.of(),
                    "Bài fixture chưa có mạch liên kết đủ rõ.",
                    "",
                    "MODERATE");
            replaceIds(
                    rubric(envelope, "W_ORGANIZATION_COHERENCE"),
                    "findingIds",
                    "F_ORGANIZATION_DEDUCTION");
        }

        int language = criterionIndex(
                criteria, "W_LANGUAGE_EXPRESSION");
        if (language >= 0
                && scores[language]
                < criteria.get(language).maxScore()) {
            int offset = learnerAnswer.indexOf("어색");
            if (offset < 0) {
                throw new IllegalArgumentException(
                        "A language deduction fixture must expose its exact span");
            }
            addEvidence(
                    envelope,
                    "EV_LANGUAGE_DEDUCTION",
                    learnerAnswer,
                    "어색",
                    offset);
            addFinding(
                    envelope,
                    "F_LANGUAGE_DEDUCTION",
                    "IMPROVEMENT",
                    "REPLACE",
                    "W_AWKWARD_UNNATURAL_EXPRESSIONS",
                    "NATURALNESS",
                    "W_LANGUAGE_EXPRESSION",
                    "EV_LANGUAGE_DEDUCTION",
                    List.of(),
                    "Cụm này là một câu chưa hoàn chỉnh.",
                    "어색합니다.",
                    "MODERATE");
            replaceIds(
                    rubric(envelope, "W_LANGUAGE_EXPRESSION"),
                    "findingIds",
                    "F_LANGUAGE_DEDUCTION");
        }
    }

    private static int[] greedyEssayScores(
            List<WritingScoringCriterion> criteria,
            int rawScore) {
        int[] scores = new int[criteria.size()];
        int remaining = rawScore;
        for (int index = 0; index < criteria.size(); index++) {
            scores[index] = Math.min(
                    remaining, criteria.get(index).maxScore());
            remaining -= scores[index];
        }
        return scores;
    }

    private static int[] fullOrZeroClozeScores(
            List<WritingScoringCriterion> criteria,
            int rawScore) {
        int[] scores = new int[criteria.size()];
        if (!chooseFullOrZero(
                criteria, scores, 0, rawScore)) {
            throw new IllegalArgumentException(
                    "Cloze fixture score cannot be represented without "
                            + "an unsupported partial blank criterion");
        }
        return scores;
    }

    private static boolean chooseFullOrZero(
            List<WritingScoringCriterion> criteria,
            int[] scores,
            int index,
            int remaining) {
        if (index == criteria.size()) {
            return remaining == 0;
        }
        int maximum = criteria.get(index).maxScore();
        if (remaining >= maximum) {
            scores[index] = maximum;
            if (chooseFullOrZero(
                    criteria, scores, index + 1,
                    remaining - maximum)) {
                return true;
            }
        }
        scores[index] = 0;
        return chooseFullOrZero(
                criteria, scores, index + 1, remaining);
    }

    private static int criterionIndex(
            List<WritingScoringCriterion> criteria,
            String criterionId) {
        for (int index = 0; index < criteria.size(); index++) {
            if (criterionId.equals(criteria.get(index).criterionId())) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isCloze(String taskType) {
        return "Q51".equals(taskType)
                || "Q52".equals(taskType)
                || "Q51_52".equals(taskType);
    }

    private static List<Integer> occurrences(
            String source,
            String exactText) {
        ArrayList<Integer> offsets = new ArrayList<>();
        for (int offset = 0;
             offset <= source.length() - exactText.length();
             offset++) {
            if (source.startsWith(exactText, offset)) {
                offsets.add(offset);
            }
        }
        return List.copyOf(offsets);
    }

    public static String normalizedFeedback(
            ObjectMapper mapper,
            String taskType,
            String learnerAnswer,
            Consumer<ObjectNode> customize
    ) {
        ObjectNode envelope = zeroEnvelope(
                mapper, taskType, learnerAnswer);
        customize.accept(envelope);
        try {
            return new WritingEvaluationNormalizer(mapper).normalize(
                    mapper.writeValueAsString(envelope),
                    taskType,
                    learnerAnswer,
                    null);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
