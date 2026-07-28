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
    void currentPartialLanguageProfileMapsCapabilityAndAvailabilityWithoutHolisticScore() {
        SpeakingEvaluationResult result = currentResult(profileRows());

        SpeakingFeedbackView view = mapper.map(result);

        assertNull(view.percentage());
        assertEquals(6, view.rubricScores().size());
        assertEquals(new BigDecimal("75.00"), view.rubricScores().get(0).percentage());
        assertEquals("Overall summary", view.overallSummary());
        assertEquals("Task achieved", view.taskAchievementSummary());
        assertEquals("heard", view.actuallyHeardTranscript());
        assertNull(view.interpretedIntent());
        assertEquals("Sample", view.sampleAnswer());
        assertEquals("Upgraded", view.correctedVersion());
        assertEquals("PROVIDER", view.source());
        assertFalse(view.scoreAvailable());
        assertTrue(view.profileAvailable());
        assertFalse(view.holisticScoreAvailable());
        assertEquals("TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION", view.evaluatorCapability());
        assertEquals("TRANSCRIPT_ONLY", view.evidenceMode());
        assertEquals("CURRENT_VERIFIED", view.contractTrust());
        assertEquals("NOT_SCORABLE", view.rubricScores().get(4).availability());
        assertNull(view.rubricScores().get(4).score());
        assertNull(view.rubricScores().get(4).percentage());
    }

    @Test
    void speakingCriterionHeadingsUseBackendOwnedVietnameseLabels() {
        SpeakingFeedbackView view = mapper.map(currentResult(profileRows()));

        assertEquals(List.of(
                        "Nội dung và hoàn thành yêu cầu",
                        "Ngữ pháp và kiểm soát câu",
                        "Từ vựng và cách diễn đạt",
                        "Mạch lạc và tổ chức ý",
                        "Độ lưu loát",
                        "Phát âm và cách thể hiện"),
                view.rubricScores().stream().map(row -> row.name()).toList());
        assertFalse(view.rubricScores().stream().map(row -> row.name()).anyMatch(List.of(
                "Content / Task Fulfillment",
                "Grammar & Sentence Control",
                "Vocabulary & Expressions",
                "Coherence & Organization",
                "Fluency",
                "Pronunciation & Delivery")::contains));
        assertEquals("Nội dung và hoàn thành yêu cầu",
                view.criterionFeedback().get(0).name());
        assertNotEquals("Provider-owned English heading",
                view.criterionFeedback().get(0).name());
        assertEquals("S_CONTENT_RELEVANCE",
                view.criterionFeedback().get(0).subcriteria().get(0).subcriterionId());
        assertEquals("Mức độ liên quan đến đề",
                view.criterionFeedback().get(0).subcriteria().get(0).name());
        assertEquals("Đang phát triển",
                view.criterionFeedback().get(0).subcriteria().get(0).levelLabel());
        assertNotEquals("Provider-owned subcriterion heading",
                view.criterionFeedback().get(0).subcriteria().get(0).name());
    }

    @Test
    void preCapabilityConstructorNeverUpgradesProviderStatusToCurrentTrust() {
        SpeakingEvaluationResult result = preCapabilityResult(profileRows());

        SpeakingFeedbackView view = mapper.map(result);

        assertFalse(result.currentEvidenceContract());
        assertEquals(SpeakingEvaluatorCapability.LEGACY_UNKNOWN, result.evaluatorCapability());
        assertEquals(SpeakingEvidenceMode.UNKNOWN, result.evidenceMode());
        assertNull(result.evidenceContractVersion());
        assertEquals(SpeakingContractTrust.LEGACY_UNVERIFIED, result.contractTrust());
        assertNull(view.rubricScores().get(0).score());
        assertNull(view.rubricScores().get(0).maxScore());
        assertNull(view.rubricScores().get(0).percentage());
        assertEquals("LEGACY_UNVERIFIED", view.rubricScores().get(0).availability());
    }

    @Test
    void canonicalEnvelopeStillRequiresExplicitCurrentTrust() {
        SpeakingEvaluationResult result = result(
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION.contractVersion(),
                null,
                profileRows());

        SpeakingFeedbackView view = mapper.map(result);

        assertFalse(result.currentEvidenceContract());
        assertEquals(SpeakingContractTrust.LEGACY_UNVERIFIED, result.contractTrust());
        assertNull(view.rubricScores().get(0).score());
        assertNull(view.rubricScores().get(0).percentage());
    }

    @Test
    void unknownCriterionInTypedEnvelopeCannotCreateTrustedScoreUi() {
        SpeakingEvaluationResult result = currentResult(List.of(
                new SpeakingEvaluationResult.RubricScore(
                        null, new BigDecimal("10"), new BigDecimal("20"), "Unknown")));

        SpeakingFeedbackView view = mapper.map(result);

        assertEquals(1, view.rubricScores().size());
        assertNull(view.rubricScores().get(0).criterionId());
        assertEquals("Tiêu chí không xác định", view.rubricScores().get(0).name());
        assertNull(view.rubricScores().get(0).score());
        assertNull(view.rubricScores().get(0).maxScore());
        assertNull(view.rubricScores().get(0).percentage());
        assertFalse(result.currentEvidenceContract());
        assertEquals("LEGACY_UNVERIFIED", view.rubricScores().get(0).availability());
    }

    @Test
    void reservedDirectAudioEnvelopeIsUntrustedAndCannotExposeNumbers() {
        SpeakingEvaluationResult result = result(
                SpeakingEvaluatorCapability.AUDIO_DIRECT_FULL_RESERVED,
                SpeakingEvidenceMode.DIRECT_AUDIO_AND_TRANSCRIPT,
                SpeakingEvaluatorCapability.AUDIO_DIRECT_FULL_RESERVED.contractVersion(),
                SpeakingContractTrust.CURRENT_VERIFIED,
                profileRows());

        SpeakingFeedbackView view = mapper.map(result);

        assertFalse(result.currentEvidenceContract());
        assertEquals(SpeakingContractTrust.LEGACY_UNVERIFIED, result.contractTrust());
        assertFalse(result.scoreAvailable());
        assertNull(view.rubricScores().get(0).score());
        assertNull(view.rubricScores().get(0).percentage());
        assertEquals("LEGACY_UNVERIFIED", view.rubricScores().get(0).availability());
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

    private SpeakingEvaluationResult currentResult(
            List<SpeakingEvaluationResult.RubricScore> rows
    ) {
        return result(
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION.contractVersion(),
                SpeakingContractTrust.CURRENT_VERIFIED,
                rows);
    }

    private SpeakingEvaluationResult result(
            SpeakingEvaluatorCapability capability,
            SpeakingEvidenceMode evidenceMode,
            String evidenceContractVersion,
            SpeakingContractTrust trust,
            List<SpeakingEvaluationResult.RubricScore> rows
    ) {
        return new SpeakingEvaluationResult(
                SpeakingEvaluationStatus.EVALUATED, false, SpeakingEvaluationSource.PROVIDER,
                "provider-model", null,
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION,
                capability, evidenceMode, evidenceContractVersion, trust,
                11L, 2L, null, null, "heard", "intent", BigDecimal.ONE,
                BigDecimal.ONE, null, null, null, "Overall summary", "Task achieved",
                List.of("Clear topic"), List.of("Add examples"), List.of(),
                List.of(new SpeakingEvaluationResult.CriterionFeedback(
                        SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                        "Provider-owned English heading",
                        new BigDecimal("15"), new BigDecimal("20"), "GOOD", "Summary",
                        List.of(), List.of(), List.of(
                                new SpeakingEvaluationResult.SubCriterionFeedback(
                                        "S_CONTENT_RELEVANCE",
                                        "Provider-owned subcriterion heading",
                                        "Developing",
                                        "Summary", List.of(), List.of())))),
                List.of(), List.of(), List.of(), null, rows, List.of(), List.of(),
                List.of(), "Upgraded", "Sample", List.of(), List.of(), null, false);
    }

    private SpeakingEvaluationResult preCapabilityResult(
            List<SpeakingEvaluationResult.RubricScore> rows
    ) {
        return new SpeakingEvaluationResult(
                SpeakingEvaluationStatus.EVALUATED, false, SpeakingEvaluationSource.PROVIDER,
                "provider-model", null,
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION,
                11L, 2L, null, null, "heard", "intent", BigDecimal.ONE,
                BigDecimal.ONE, null, null, null, "Overall summary", "Task achieved",
                List.of("Clear topic"), List.of("Add examples"), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, rows, List.of(), List.of(),
                List.of(), "Upgraded", "Sample", List.of(), List.of(), null, false);
    }

    private List<SpeakingEvaluationResult.RubricScore> profileRows() {
        return List.of(
                scored(SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT, "15", "20"),
                scored(SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL, "16", "20"),
                scored(SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS, "12", "15"),
                scored(SpeakingRubricCriterion.COHERENCE_ORGANIZATION, "12", "15"),
                unavailable(SpeakingRubricCriterion.FLUENCY),
                unavailable(SpeakingRubricCriterion.PRONUNCIATION_DELIVERY));
    }

    private SpeakingEvaluationResult.RubricScore scored(
            SpeakingRubricCriterion criterion,
            String score,
            String max
    ) {
        return new SpeakingEvaluationResult.RubricScore(
                criterion, new BigDecimal(score), new BigDecimal(max), "Feedback");
    }

    private SpeakingEvaluationResult.RubricScore unavailable(SpeakingRubricCriterion criterion) {
        return new SpeakingEvaluationResult.RubricScore(
                criterion, null, null, "No audio",
                SpeakingCriterionAvailability.NOT_SCORABLE);
    }
}
