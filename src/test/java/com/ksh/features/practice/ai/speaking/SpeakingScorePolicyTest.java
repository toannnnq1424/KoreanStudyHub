package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpeakingScorePolicyTest {

    @Test
    void rubricWeightsSumToOneHundred() {
        assertEquals(new BigDecimal("100"), SpeakingRubricCriterion.totalWeight());
        assertEquals(new BigDecimal("70"), SpeakingRubricCriterion.transcriptGroundedCriteria().stream()
                .map(SpeakingRubricCriterion::maxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void configuredPointsInvariantUsesOverallPercentage() {
        BigDecimal first = SpeakingScorePolicy.earnedQuestionPoints(
                new BigDecimal("20"), new BigDecimal("75"));
        BigDecimal second = SpeakingScorePolicy.earnedQuestionPoints(
                new BigDecimal("30"), new BigDecimal("50"));
        BigDecimal attemptScore = SpeakingScorePolicy.attemptScore(List.of(first, second));

        assertEquals(new BigDecimal("15.00"), first);
        assertEquals(new BigDecimal("15.00"), second);
        assertEquals(new BigDecimal("30.00"), attemptScore);
        assertEquals(new BigDecimal("60.00"), SpeakingScorePolicy.attemptPercentage(
                attemptScore, new BigDecimal("50")));
    }

    @Test
    void transcriptLanguageProfileCannotProduceQuestionPoints() throws Exception {
        SpeakingEvaluationResult result = new SpeakingEvaluationNormalizer().normalize(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                        OpenAiCompatibleSpeakingEvaluationClientTest.validEvaluationJson()));

        assertNull(SpeakingScorePolicy.earnedQuestionPoints(BigDecimal.TEN, result));
        assertNull(result.overallScore());
    }

    @Test
    void scoreOutsideInternalRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                SpeakingScorePolicy.earnedQuestionPoints(BigDecimal.TEN, new BigDecimal("100.01")));
        assertThrows(IllegalArgumentException.class, () ->
                SpeakingScorePolicy.earnedQuestionPoints(BigDecimal.TEN, new BigDecimal("-1")));
    }
}
