package com.ksh.features.practice;

import com.ksh.entities.PracticeAttempt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAttemptScoreContractTest {

    @Test
    void objectiveAttemptStoresCanonicalEarnedPointsAndPercentage() {
        PracticeAttempt attempt = attempt("READING");

        attempt.markSubmitted(new BigDecimal("2"), new BigDecimal("4"), "{}");

        assertThat(attempt.getScoreUnit()).isEqualTo("EARNED_POINTS");
        assertThat(attempt.getEarnedPoints()).isEqualByComparingTo("2");
        assertThat(attempt.getScorePercentage()).isEqualByComparingTo("50.00");
    }

    @Test
    void objectiveCanonicalEarnedPointsCannotExceedPossiblePoints() {
        PracticeAttempt attempt = attempt("LISTENING");

        attempt.markSubmitted(new BigDecimal("8"), new BigDecimal("4"), "{}");

        assertThat(attempt.getEarnedPoints()).isEqualByComparingTo("4");
        assertThat(attempt.getScorePercentage()).isEqualByComparingTo("100");
    }

    @Test
    void writingPercentageCompatibilityAlwaysProducesCanonicalEarnedPoints() {
        PracticeAttempt attempt = attempt("WRITING");

        attempt.markGraded(new BigDecimal("80"), new BigDecimal("25"), "{}", "{}");

        assertThat(attempt.getScoreUnit()).isEqualTo("PERCENTAGE");
        assertThat(attempt.getScorePercentage()).isEqualByComparingTo("80");
        assertThat(attempt.getEarnedPoints()).isEqualByComparingTo("20.00");
    }

    @Test
    void speakingPercentageIsClampedBeforeScalingToPoints() {
        PracticeAttempt attempt = attempt("SPEAKING");

        attempt.markGraded(new BigDecimal("120"), new BigDecimal("10"), "{}", "{}");

        assertThat(attempt.getScorePercentage()).isEqualByComparingTo("100");
        assertThat(attempt.getEarnedPoints()).isEqualByComparingTo("10.00");
    }

    private static PracticeAttempt attempt(String skill) {
        return new PracticeAttempt(1L, 2L, 3L, skill, 4L);
    }
}
