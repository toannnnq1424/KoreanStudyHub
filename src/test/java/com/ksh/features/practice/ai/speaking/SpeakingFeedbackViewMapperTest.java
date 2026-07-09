package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingFeedbackView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingFeedbackViewMapperTest {
    private final SpeakingFeedbackViewMapper mapper = new SpeakingFeedbackViewMapper();

    @Test
    void typedResultMapsToExistingSpeakingViewShape() {
        SpeakingEvaluationResult result = new SpeakingEvaluationResult(
                SpeakingEvaluationStatus.MOCK_EVALUATED,
                true,
                SpeakingEvaluationSource.MOCK,
                "mock-model",
                null,
                "p1",
                "r1",
                "s1",
                11L,
                2L,
                null,
                null,
                "heard",
                "intent",
                BigDecimal.ONE,
                BigDecimal.ONE,
                "LOW",
                new BigDecimal("75"),
                "Internal",
                "Overall summary",
                "Task achieved",
                List.of("Clear topic"),
                List.of("Add examples"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                        new BigDecimal("15"),
                        new BigDecimal("20"),
                        "Feedback")),
                List.of(),
                List.of(),
                List.of(),
                "Upgraded",
                "Sample",
                List.of(),
                List.of(),
                null,
                false);

        SpeakingFeedbackView view = mapper.map(result);

        assertEquals(new BigDecimal("75"), view.percentage());
        assertEquals(1, view.rubricScores().size());
        assertEquals(new BigDecimal("75.00"), view.rubricScores().get(0).percentage());
        assertEquals("Overall summary", view.overallSummary());
        assertEquals("Task achieved", view.taskAchievementSummary());
        assertEquals("heard", view.actuallyHeardTranscript());
        assertEquals("intent", view.interpretedIntent());
        assertEquals("Sample", view.sampleAnswer());
        assertEquals("Upgraded", view.correctedVersion());
        assertEquals("MOCK", view.source());
        assertTrue(view.scoreAvailable());
    }

    @Test
    void failureResultDoesNotExposeFabricatedPercentageOrPrivateFields() throws Exception {
        SpeakingEvaluationResult failure = new SpeakingEvaluationNormalizer()
                .contractFailure("INVALID_CONTRACT");

        SpeakingFeedbackView view = mapper.map(failure);
        String json = new ObjectMapper().writeValueAsString(view);

        assertNull(view.percentage());
        assertFalse(json.contains("storageKey"));
        assertFalse(json.contains("playbackPath"));
        assertFalse(json.contains("contentHash"));
        assertFalse(json.contains("userId"));
        assertFalse(json.contains("apiKey"));
    }
}
