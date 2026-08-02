package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Practice-owned, task-bounded Korean Writing diagnostic contract.
 *
 * <p>Findings remain diagnostic evidence below the stable task-native score
 * criteria. They never create score rows or compute points by finding count.</p>
 */
public final class WritingDiagnosticContract {

    public static final String VERSION = "korean-writing-diagnostics-v3";

    private static final Map<String, List<String>> SUBTYPES = Map.of(
            "TASK_CONTENT", List.of(
                    "REQUIREMENT_COVERAGE", "CONTENT_RELEVANCE", "DATA_ACCURACY",
                    "THESIS_MAIN_IDEA", "CONTENT_DEVELOPMENT", "SUPPORT"),
            "DISCOURSE", List.of(
                    "GLOBAL_ORGANIZATION", "PARAGRAPHING", "LOGICAL_RELATION",
                    "CONNECTIVES", "COHESION", "REFERENCE_ELLIPSIS",
                    "TRANSITION_USE", "REDUNDANCY"),
            "MORPHOSYNTAX", List.of(
                    "MORPHOLOGY_PARTICLES", "ENDINGS_CONJUGATION",
                    "SPEECH_LEVEL_FORMS", "TENSE_ASPECT_MODALITY_NEGATION",
                    "PREDICATE_VALENCY_AGREEMENT", "WORD_ORDER",
                    "ADNOMINAL_RELATIVE_EMBEDDED_CLAUSE",
                    "QUOTATION_NOMINALIZATION", "PASSIVE_CAUSATIVE",
                    "SENTENCE_COMPLETENESS"),
            "LEXICO_SEMANTIC", List.of(
                    "VOCABULARY_SENSE_CONTEXT", "WORD_CHOICE", "COLLOCATION",
                    "IDIOMATICITY", "DOMAIN_SINO_KOREAN", "PRECISION",
                    "NATURALNESS", "REPETITION", "RANGE"),
            "SOCIOLINGUISTIC_PRAGMATIC", List.of(
                    "REGISTER", "HONORIFIC_SPEECH_LEVEL", "STANCE",
                    "GENRE_CONVENTION", "POLITENESS_INDIRECTNESS"),
            "ORTHOGRAPHY", List.of(
                    "SPELLING_JAMO", "SPACING", "PUNCTUATION",
                    "NUMERAL_UNIT_RENDERING"),
            "LENGTH_FORMAT", List.of(
                    "TASK_LENGTH", "PARAGRAPH_CONSTRAINT",
                    "ANSWER_SHEET_FORMAT"));

    private static final Set<String> IMPACTS =
            Set.of("MINOR", "MODERATE", "MAJOR", "BLOCKING");
    private static final Set<String> OBSERVABILITY =
            Set.of("DIRECT", "INFERRED_BOUNDED");

    private WritingDiagnosticContract() {
    }

    public static String categoryCode(WritingRubricCriterion criterion) {
        if (criterion == WritingRubricCriterion.W_CLOZE_CONTEXT_FIT) {
            return "TASK_CONTENT";
        }
        if (criterion == WritingRubricCriterion.W_CONNECTIVE_ENDING_ACCURACY
                || criterion == WritingRubricCriterion.W_CLOZE_GRAMMAR_COMPATIBILITY) {
            return "MORPHOSYNTAX";
        }
        if (criterion == WritingRubricCriterion.W_SENTENCE_COMPLETION_NATURALNESS) {
            return "LEXICO_SEMANTIC";
        }
        if (criterion == WritingRubricCriterion.W_CLOZE_REGISTER_MATCH) {
            return "SOCIOLINGUISTIC_PRAGMATIC";
        }
        return switch (criterion.category()) {
            case CONTENT -> "TASK_CONTENT";
            case ORGANIZATION -> "DISCOURSE";
            case GRAMMAR -> "MORPHOSYNTAX";
            case VOCABULARY, GENERAL_LANGUAGE -> "LEXICO_SEMANTIC";
            case REGISTER -> "SOCIOLINGUISTIC_PRAGMATIC";
            case SPELLING_SPACING -> "ORTHOGRAPHY";
            case LENGTH, FORMAT -> "LENGTH_FORMAT";
            case CLOZE -> switch (criterion) {
                case W_CLOZE_CONTEXT_FIT -> "TASK_CONTENT";
                case W_CLOZE_REGISTER_MATCH ->
                        "SOCIOLINGUISTIC_PRAGMATIC";
                case W_SENTENCE_COMPLETION_NATURALNESS ->
                        "LEXICO_SEMANTIC";
                default -> "MORPHOSYNTAX";
            };
        };
    }

    public static List<String> allowedSubtypes(WritingRubricCriterion criterion) {
        return SUBTYPES.getOrDefault(categoryCode(criterion), List.of());
    }

    public static boolean ledgerEligible(
            WritingRubricCriterion criterion) {
        return criterion != null
                && criterion.activeForProvider()
                && (criterion.polarity()
                == WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT
                || criterion.supports(
                WritingRubricCriterion.EvidenceScope.TEXT_SPAN)
                || criterion.supports(
                WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER));
    }

    public static String expectedParentCriterionId(
            WritingRubricCriterion criterion,
            String taskType
    ) {
        List<String> allowed = allowedParentCriterionIds(
                criterion, taskType);
        return allowed.size() == 1 ? allowed.get(0) : null;
    }

    public static List<String> allowedParentCriterionIds(
            WritingRubricCriterion criterion,
            String taskType
    ) {
        if (criterion == null) {
            return List.of();
        }
        if (isClozeTask(taskType)) {
            String suffix = switch (categoryCode(criterion)) {
                case "TASK_CONTENT" -> "CONTEXT";
                case "MORPHOSYNTAX" -> "GRAMMAR";
                case "LEXICO_SEMANTIC",
                        "SOCIOLINGUISTIC_PRAGMATIC",
                        "ORTHOGRAPHY" -> "EXPRESSION";
                default -> null;
            };
            return suffix == null
                    ? List.of()
                    : List.of(
                            "W_CLOZE_BLANK_1_" + suffix,
                            "W_CLOZE_BLANK_2_" + suffix);
        }
        String parent = switch (categoryCode(criterion)) {
            case "TASK_CONTENT" -> "W_CONTENT_TASK_ACHIEVEMENT";
            case "DISCOURSE" -> "W_ORGANIZATION_COHERENCE";
            case "MORPHOSYNTAX", "LEXICO_SEMANTIC",
                    "SOCIOLINGUISTIC_PRAGMATIC", "ORTHOGRAPHY" ->
                    "W_LANGUAGE_EXPRESSION";
            case "LENGTH_FORMAT" -> null;
            default -> null;
        };
        return parent == null ? List.of() : List.of(parent);
    }

    public static String expectedParentCriterionId(
            WritingRubricCriterion criterion,
            String taskType,
            List<String> requirementIds
    ) {
        if (!isClozeTask(taskType)) {
            return expectedParentCriterionId(criterion, taskType);
        }
        List<String> allowed = allowedParentCriterionIds(
                criterion, taskType);
        if (allowed.isEmpty() || requirementIds == null) {
            return null;
        }
        boolean blankOne = requirementIds.contains(
                "CLOZE_BLANK_1_CONTEXT");
        boolean blankTwo = requirementIds.contains(
                "CLOZE_BLANK_2_CONTEXT");
        if (blankOne == blankTwo) {
            return null;
        }
        return blankOne ? allowed.get(0) : allowed.get(1);
    }

    public static boolean validProviderMetadata(
            JsonNode finding,
            WritingRubricCriterion criterion,
            String taskType,
            WritingRubricCriterion.EvidenceScope evidenceScope
    ) {
        if (finding == null || criterion == null || evidenceScope == null) {
            return false;
        }
        String subtype = textual(finding.get("subtype"));
        String impact = textual(finding.get("impact"));
        String observability = textual(finding.get("observability"));
        JsonNode frequency = finding.get("frequency");
        JsonNode confidence = finding.get("confidence");
        JsonNode suppliedParent = finding.get("scoringCriterionId");
        String expectedParent =
                expectedParentCriterionId(
                        criterion,
                        taskType,
                        stringValues(finding.get("requirementIds")));
        if ((expectedParent == null
                && (suppliedParent == null || !suppliedParent.isNull()))
                || (expectedParent != null
                && (suppliedParent == null
                || !suppliedParent.isTextual()
                || !expectedParent.equals(suppliedParent.asText())))) {
            return false;
        }
        String suppliedParentValue = suppliedParent != null
                && suppliedParent.isTextual()
                ? suppliedParent.asText()
                : null;
        return frequency != null
                && frequency.isIntegralNumber()
                && frequency.canConvertToInt()
                && confidence != null
                && confidence.isNumber()
                && validProviderMetadata(
                        subtype,
                        suppliedParentValue,
                        impact,
                        frequency.intValue(),
                        confidence.decimalValue(),
                        observability,
                        criterion,
                        taskType,
                        evidenceScope,
                        stringValues(finding.get("requirementIds")));
    }

    public static boolean validProviderMetadata(
            String subtype,
            String suppliedParentCriterionId,
            String impact,
            Integer frequency,
            BigDecimal confidence,
            String observability,
            WritingRubricCriterion criterion,
            String taskType,
            WritingRubricCriterion.EvidenceScope evidenceScope
    ) {
        return validProviderMetadata(
                subtype,
                suppliedParentCriterionId,
                impact,
                frequency,
                confidence,
                observability,
                criterion,
                taskType,
                evidenceScope,
                List.of());
    }

    public static boolean validProviderMetadata(
            String subtype,
            String suppliedParentCriterionId,
            String impact,
            Integer frequency,
            BigDecimal confidence,
            String observability,
            WritingRubricCriterion criterion,
            String taskType,
            WritingRubricCriterion.EvidenceScope evidenceScope,
            List<String> requirementIds
    ) {
        if (criterion == null
                || evidenceScope == null
                || frequency == null
                || confidence == null) {
            return false;
        }
        String expectedParent =
                expectedParentCriterionId(
                        criterion, taskType, requirementIds);
        return java.util.Objects.equals(
                        expectedParent, suppliedParentCriterionId)
                && allowedSubtypes(criterion).contains(subtype)
                && IMPACTS.contains(impact)
                && frequency >= 1
                && confidence.compareTo(BigDecimal.ZERO) >= 0
                && confidence.compareTo(BigDecimal.ONE) <= 0
                && OBSERVABILITY.contains(observability)
                && observabilityMatches(evidenceScope, observability);
    }

    private static boolean observabilityMatches(
            WritingRubricCriterion.EvidenceScope scope,
            String observability
    ) {
        return switch (scope) {
            case TEXT_SPAN -> "DIRECT".equals(observability);
            case WHOLE_ANSWER -> "DIRECT".equals(observability)
                    || "INFERRED_BOUNDED".equals(observability);
            case TASK_METADATA -> false;
        };
    }

    private static boolean isClozeTask(String taskType) {
        return "Q51".equals(taskType)
                || "Q52".equals(taskType)
                || "Q51_52".equals(taskType);
    }

    private static String textual(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static List<String> stringValues(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual() || value.asText().isBlank()) {
                return List.of();
            }
            values.add(value.asText());
        }
        return List.copyOf(values);
    }
}
