package com.ksh.features.practice.ai.speaking;

import java.math.BigDecimal;
import java.util.Arrays;

public enum SpeakingRubricCriterion {
    CONTENT_TASK_FULFILLMENT("Content / Task Fulfillment", 20),
    GRAMMAR_SENTENCE_CONTROL("Grammar & Sentence Control", 20),
    VOCABULARY_EXPRESSIONS("Vocabulary & Expressions", 15),
    COHERENCE_ORGANIZATION("Coherence & Organization", 15),
    FLUENCY("Fluency", 15),
    PRONUNCIATION_DELIVERY("Pronunciation & Delivery", 15);

    private final String label;
    private final BigDecimal maxScore;

    SpeakingRubricCriterion(String label, int maxScore) {
        this.label = label;
        this.maxScore = BigDecimal.valueOf(maxScore);
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
}
