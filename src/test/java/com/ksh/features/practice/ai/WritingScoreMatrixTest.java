package com.ksh.features.practice.ai;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingScoreMatrixTest {

    @ParameterizedTest
    @CsvSource({
            "Q51,10.0",
            "Q52,10.0",
            "Q51_52,10.0",
            "Q53,30.0",
            "Q54,50.0",
            "GENERAL,100.0",
            ",100.0"
    })
    void rawScoreMaxIsTaskSpecificAndStable(String taskType, double expectedMax) {
        assertEquals(expectedMax, WritingScoreMatrix.rawScoreMax(taskType));
    }

    @ParameterizedTest
    @CsvSource({
            "Q51,1.0,1.1",
            "Q51,5.0,5.6",
            "Q51,9.0,10.0",
            "Q52,1.0,1.1",
            "Q52,5.0,5.6",
            "Q52,9.0,10.0",
            "Q51_52,1.0,1.1",
            "Q51_52,5.0,5.6",
            "Q51_52,9.0,10.0",
            "Q53,1.0,3.3",
            "Q53,5.0,16.7",
            "Q53,9.0,30.0",
            "Q54,1.0,5.6",
            "Q54,5.0,27.8",
            "Q54,9.0,50.0",
            "GENERAL,1.0,11.1",
            "GENERAL,5.0,55.6",
            "GENERAL,9.0,100.0"
    })
    void normalizedScoreMapsToExpectedRawScore(String taskType, double normalizedScore, double expectedRawScore) {
        assertEquals(expectedRawScore, WritingScoreMatrix.rawScoreFromNormalized(normalizedScore, taskType));
    }

    @ParameterizedTest
    @CsvSource({
            "Q51",
            "Q52",
            "Q53",
            "Q54",
            "GENERAL"
    })
    void validProviderMinimumScoreIsLowButNonZero(String taskType) {
        assertTrue(WritingScoreMatrix.rawScoreFromNormalized(1.0, taskType) > 0.0);
    }
}
