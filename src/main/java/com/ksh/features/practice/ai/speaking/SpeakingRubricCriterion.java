package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.util.Arrays;

public enum SpeakingRubricCriterion {
    CONTENT_TASK_FULFILLMENT("S_CONTENT_TASK_FULFILLMENT", "Content / Task Fulfillment", 20),
    GRAMMAR_SENTENCE_CONTROL("S_GRAMMAR_SENTENCE_CONTROL", "Grammar & Sentence Control", 20),
    VOCABULARY_EXPRESSIONS("S_VOCABULARY_EXPRESSIONS", "Vocabulary & Expressions", 15),
    COHERENCE_ORGANIZATION("S_COHERENCE_ORGANIZATION", "Coherence & Organization", 15),
    FLUENCY("S_FLUENCY", "Fluency", 15),
    PRONUNCIATION_DELIVERY("S_PRONUNCIATION_DELIVERY", "Pronunciation & Delivery", 15);

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
