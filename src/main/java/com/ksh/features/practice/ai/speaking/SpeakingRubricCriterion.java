package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum SpeakingRubricCriterion {
    CONTENT_TASK_FULFILLMENT(
            "S_CONTENT_TASK_FULFILLMENT", "Nội dung / Hoàn thành nhiệm vụ", 20),
    GRAMMAR_SENTENCE_CONTROL(
            "S_GRAMMAR_SENTENCE_CONTROL", "Ngữ pháp / Kiểm soát câu", 20),
    VOCABULARY_EXPRESSIONS(
            "S_VOCABULARY_EXPRESSIONS", "Từ vựng / Biểu đạt", 15),
    COHERENCE_ORGANIZATION(
            "S_COHERENCE_ORGANIZATION", "Mạch lạc / Tổ chức ý", 15),
    FLUENCY("S_FLUENCY", "Độ lưu loát", 15),
    PRONUNCIATION_DELIVERY(
            "S_PRONUNCIATION_DELIVERY", "Phát âm / Cách thể hiện", 15);

    private final String id;
    private final String label;
    private final BigDecimal maxScore;

    SpeakingRubricCriterion(String id, String label, int maxScore) {
        this.id = id;
        this.label = label;
        this.maxScore = BigDecimal.valueOf(maxScore);
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public BigDecimal maxScore() {
        return maxScore;
    }

    public boolean transcriptGrounded() {
        return this != FLUENCY && this != PRONUNCIATION_DELIVERY;
    }

    public boolean requiresAcousticEvidence() {
        return !transcriptGrounded();
    }

    /**
     * Phase-13 closed parent/child contract. Unknown or cross-parent identifiers
     * fail closed; PRE-14 may replace this bounded list with a versioned registry.
     */
    public boolean ownsSubcriterion(String subcriterionId) {
        if (subcriterionId == null || subcriterionId.isBlank()) {
            return false;
        }
        return allowedSubcriteria().contains(subcriterionId.trim());
    }

    private Set<String> allowedSubcriteria() {
        return switch (this) {
            case CONTENT_TASK_FULFILLMENT -> Set.of(
                    "S_CONTENT_RELEVANCE",
                    "S_CONTENT_PROMPT_COVERAGE",
                    "S_CONTENT_SPECIFICITY_EXAMPLES");
            case VOCABULARY_EXPRESSIONS -> Set.of(
                    "S_VOCAB_TOPIC_WORDS",
                    "S_VOCAB_NATURAL_EXPRESSIONS",
                    "S_VOCAB_REPETITION_CONTROL",
                    "S_VOCAB_WORD_CHOICE");
            case GRAMMAR_SENTENCE_CONTROL -> Set.of(
                    "S_GRAMMAR_PARTICLES",
                    "S_GRAMMAR_TENSE_ASPECT",
                    "S_GRAMMAR_ENDINGS",
                    "S_GRAMMAR_SENTENCE_STRUCTURE",
                    "S_GRAMMAR_HONORIFIC_REGISTER",
                    "S_GRAMMAR_CONNECTORS");
            case COHERENCE_ORGANIZATION -> Set.of(
                    "S_COHERENCE_ORGANIZATION",
                    "S_COHERENCE_LOGICAL_FLOW",
                    "S_COHERENCE_DISCOURSE_MARKERS");
            case FLUENCY, PRONUNCIATION_DELIVERY -> Set.of();
        };
    }

    public static List<SpeakingRubricCriterion> transcriptGroundedCriteria() {
        return Arrays.stream(values())
                .filter(SpeakingRubricCriterion::transcriptGrounded)
                .toList();
    }

    public static BigDecimal totalWeight() {
        return Arrays.stream(values())
                .map(SpeakingRubricCriterion::maxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @JsonCreator
    public static SpeakingRubricCriterion fromExternalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (SpeakingRubricCriterion criterion : values()) {
            if (criterion.id.equals(normalized) || criterion.name().equals(normalized)) {
                return criterion;
            }
        }
        return null;
    }
}
