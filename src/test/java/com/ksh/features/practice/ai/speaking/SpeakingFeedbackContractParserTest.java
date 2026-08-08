package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingFeedbackContractParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeakingFeedbackContractParser parser =
            new SpeakingFeedbackContractParser(
                    objectMapper, new SpeakingEvaluationNormalizer());

    @Test
    void readsCurrentTypedTranscriptEnvelope() {
        SpeakingEvaluationResult current =
                SpeakingEvaluationTestFixtures.currentResult(
                        objectMapper, "현재 전사", new BigDecimal("16"));

        SpeakingEvaluationResult parsed = parser.read(
                objectMapper.valueToTree(current));

        assertThat(parsed.currentEvidenceContract()).isTrue();
        assertThat(parsed.evidenceMode())
                .isEqualTo(SpeakingEvidenceMode.TRANSCRIPT_ONLY);
    }

    @Test
    void flatAndSnakeCasePayloadsFailClosed() {
        ObjectNode flat = objectMapper.createObjectNode()
                .put("score", 8).put("summary", "old");
        ObjectNode snake = objectMapper.createObjectNode()
                .put("evaluation_status", "EVALUATED")
                .put("score_available", true);

        assertContractFailure(parser.read(flat));
        assertContractFailure(parser.read(snake));
    }

    @Test
    void malformedTypedPayloadFailsClosed() {
        ObjectNode malformed = objectMapper.createObjectNode()
                .put("evaluationStatus", "EVALUATED")
                .put("contractTrust", "CURRENT_VERIFIED");

        assertContractFailure(parser.read(malformed));
    }

    @Test
    void reservedDirectAudioContractFailsClosed() {
        ObjectNode reserved = objectMapper.valueToTree(
                SpeakingEvaluationTestFixtures.currentResult(
                        objectMapper, "현재 전사", new BigDecimal("16")));
        reserved.put("evaluatorCapability", "AUDIO_DIRECT_FULL_RESERVED");
        reserved.put("evidenceMode", "DIRECT_AUDIO_AND_TRANSCRIPT");

        assertContractFailure(parser.read(reserved));
    }

    @Test
    void numericAcousticRowsInTranscriptOnlyPayloadFailClosed() {
        ObjectNode unsafe = objectMapper.valueToTree(
                SpeakingEvaluationTestFixtures.currentResult(
                        objectMapper, "현재 전사", new BigDecimal("16")));
        unsafe.withArray("rubricScores").addObject()
                .put("criterion", "S_FLUENCY")
                .put("score", 10)
                .put("maxScore", 15)
                .put("availability", "SCORED");

        assertContractFailure(parser.read(unsafe));
    }

    private static void assertContractFailure(
            SpeakingEvaluationResult result) {
        assertThat(result.profileAvailable()).isFalse();
        assertThat(result.scoreAvailable()).isFalse();
        assertThat(result.currentEvidenceContract()).isTrue();
        assertThat(result.evaluationStatus()).isEqualTo(
                SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED);
    }
}
