package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingEvaluationReusePolicyTest {
    private final SpeakingEvaluationReusePolicy policy = new SpeakingEvaluationReusePolicy();

    @Test
    void reusesMatchingAudioIdentityAndVersions() {
        SpeakingEvaluationResult stored = result(
                SpeakingEvaluationStatus.EVALUATED,
                SpeakingEvaluationSource.PROVIDER,
                true,
                false,
                12L,
                3L,
                "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
                "저는 학생입니다.");
        SpeakingEvaluationIdentity identity = audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1");

        assertThat(policy.decide(stored, identity, true).reuse()).isTrue();
    }

    @Test
    void invalidatesWhenAudioMediaIdOrVersionChanges() {
        SpeakingEvaluationResult stored = result(
                SpeakingEvaluationStatus.EVALUATED,
                SpeakingEvaluationSource.PROVIDER,
                true,
                false,
                12L,
                3L,
                "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
                "저는 학생입니다.");

        assertThat(policy.decide(stored, audioIdentity(
                1L, 2L, 99L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, audioIdentity(
                1L, 2L, 12L, 4L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1"), true).reuse()).isFalse();
    }

    @Test
    void invalidatesWhenPromptRubricSchemaOrModelChanges() {
        SpeakingEvaluationResult stored = result(
                SpeakingEvaluationStatus.EVALUATED,
                SpeakingEvaluationSource.PROVIDER,
                true,
                false,
                12L,
                3L,
                "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
                "저는 학생입니다.");

        assertThat(policy.decide(stored, audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-pro", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v2",
                "speaking-rubric-v1", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v2", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v2"), true).reuse()).isFalse();
    }

    @Test
    void legacyOrMockIsNotReusableWhenRealProviderIsEnabled() {
        SpeakingEvaluationIdentity identity = audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1");

        assertThat(policy.decide(result(SpeakingEvaluationStatus.LEGACY_RESULT,
                SpeakingEvaluationSource.LEGACY, true, false, 12L, 3L,
                "gpt-4o-mini-transcribe", "models/gemini-2.5-flash",
                "speaking-eval-v1", "speaking-rubric-v1", "speaking-schema-v1",
                "저는 학생입니다."), identity, true).reuse()).isFalse();
        assertThat(policy.decide(result(SpeakingEvaluationStatus.MOCK_EVALUATED,
                SpeakingEvaluationSource.MOCK, true, false, 12L, 3L,
                "gpt-4o-mini-transcribe", "models/gemini-2.5-flash",
                "speaking-eval-v1", "speaking-rubric-v1", "speaking-schema-v1",
                "저는 학생입니다."), identity, true).reuse()).isFalse();
    }

    @Test
    void retryableFailureIsNotReusableButNonRetryableMatchingFailureIsReusable() {
        SpeakingEvaluationIdentity identity = audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1");

        assertThat(policy.decide(result(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                SpeakingEvaluationSource.PROVIDER, false, true, 12L, 3L,
                "gpt-4o-mini-transcribe", "models/gemini-2.5-flash",
                "speaking-eval-v1", "speaking-rubric-v1", "speaking-schema-v1",
                null), identity, true).reuse()).isFalse();
        assertThat(policy.decide(result(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                SpeakingEvaluationSource.PROVIDER, false, false, 12L, 3L,
                "gpt-4o-mini-transcribe", "models/gemini-2.5-flash",
                "speaking-eval-v1", "speaking-rubric-v1", "speaking-schema-v1",
                null), identity, true).reuse()).isTrue();
    }

    @Test
    void preservesPriorSuccessWhenTransientFailureHasSameIdentity() {
        SpeakingEvaluationResult success = result(
                SpeakingEvaluationStatus.EVALUATED,
                SpeakingEvaluationSource.PROVIDER,
                true,
                false,
                12L,
                3L,
                "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
                "저는 학생입니다.");
        SpeakingEvaluationResult transientFailure = result(
                SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                SpeakingEvaluationSource.PROVIDER,
                false,
                true,
                12L,
                3L,
                "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
                null);
        SpeakingEvaluationIdentity identity = audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1");

        assertThat(policy.preserveSuccessOnTransientFailure(success, transientFailure, identity)).isSameAs(success);
    }

    @Test
    void doesNotPreservePriorSuccessWhenIdentityChanged() {
        SpeakingEvaluationResult success = result(
                SpeakingEvaluationStatus.EVALUATED,
                SpeakingEvaluationSource.PROVIDER,
                true,
                false,
                12L,
                3L,
                "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
                "저는 학생입니다.");
        SpeakingEvaluationResult transientFailure = result(
                SpeakingEvaluationStatus.AUDIO_UNAVAILABLE,
                SpeakingEvaluationSource.PROVIDER,
                false,
                true,
                99L,
                1L,
                "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
                null);
        SpeakingEvaluationIdentity changedIdentity = audioIdentity(
                1L, 2L, 99L, 1L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1");

        assertThat(policy.preserveSuccessOnTransientFailure(success, transientFailure, changedIdentity)).isSameAs(transientFailure);
    }

    @Test
    void textFallbackIdentityUsesNormalizedHashWithoutExposingAnswer() {
        SpeakingEvaluationIdentity identity = textFallbackIdentity(
                1L, 2L, "  저는   학생입니다.  ", "models/gemini-2.5-flash",
                "speaking-eval-v1", "speaking-rubric-v1", "speaking-schema-v1");
        SpeakingEvaluationResult stored = result(
                SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED,
                SpeakingEvaluationSource.TEXT_FALLBACK,
                true,
                false,
                null,
                null,
                null,
                "models/gemini-2.5-flash",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
                "저는 학생입니다.");

        assertThat(policy.decide(stored, identity, true).reuse()).isTrue();
        assertThat(identity.toString()).doesNotContain("저는 학생입니다");
    }

    private SpeakingEvaluationResult result(
            SpeakingEvaluationStatus status,
            SpeakingEvaluationSource source,
            boolean scoreAvailable,
            boolean retryable,
            Long mediaId,
            Long mediaVersion,
            String transcriptionModel,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion,
            String actuallyHeardTranscript
    ) {
        promptVersion = currentPromptVersion(promptVersion);
        rubricVersion = currentRubricVersion(rubricVersion);
        schemaVersion = currentSchemaVersion(schemaVersion);
        boolean legacy = status == SpeakingEvaluationStatus.LEGACY_RESULT
                || status == SpeakingEvaluationStatus.MOCK_EVALUATED
                || source == SpeakingEvaluationSource.LEGACY
                || source == SpeakingEvaluationSource.MOCK;
        return new SpeakingEvaluationResult(
                status,
                scoreAvailable,
                source,
                evaluatorModel,
                transcriptionModel,
                promptVersion,
                rubricVersion,
                schemaVersion,
                legacy ? SpeakingEvaluatorCapability.LEGACY_UNKNOWN
                        : SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                legacy ? SpeakingEvidenceMode.UNKNOWN : SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                legacy ? null : SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                legacy ? SpeakingContractTrust.LEGACY_UNVERIFIED
                        : SpeakingContractTrust.CURRENT_VERIFIED,
                mediaId,
                mediaVersion,
                actuallyHeardTranscript,
                actuallyHeardTranscript,
                actuallyHeardTranscript,
                null,
                null,
                null,
                null,
                scoreAvailable ? new BigDecimal("80") : null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                scoreAvailable ? languageProfileRubrics() : List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                scoreAvailable ? null : status.name(),
                retryable);
    }

    private SpeakingEvaluationIdentity audioIdentity(
            Long attemptId,
            Long questionId,
            Long audioMediaId,
            Long mediaVersion,
            String transcriptionModel,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion
    ) {
        return SpeakingEvaluationIdentity.audio(
                attemptId, questionId, audioMediaId, mediaVersion,
                transcriptionModel, evaluatorModel,
                currentPromptVersion(promptVersion),
                currentRubricVersion(rubricVersion),
                currentSchemaVersion(schemaVersion));
    }

    private SpeakingEvaluationIdentity textFallbackIdentity(
            Long attemptId,
            Long questionId,
            String answer,
            String evaluatorModel,
            String promptVersion,
            String rubricVersion,
            String schemaVersion
    ) {
        return SpeakingEvaluationIdentity.textFallback(
                attemptId, questionId, answer, evaluatorModel,
                currentPromptVersion(promptVersion),
                currentRubricVersion(rubricVersion),
                currentSchemaVersion(schemaVersion));
    }

    private String currentPromptVersion(String value) {
        return "speaking-eval-v1".equals(value) ? SpeakingPromptRules.PROMPT_VERSION : value;
    }

    private String currentRubricVersion(String value) {
        return "speaking-rubric-v1".equals(value) ? SpeakingPromptRules.RUBRIC_VERSION : value;
    }

    private String currentSchemaVersion(String value) {
        return "speaking-schema-v1".equals(value) ? SpeakingPromptRules.SCHEMA_VERSION : value;
    }

    private List<SpeakingEvaluationResult.RubricScore> languageProfileRubrics() {
        return List.of(
                new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                        new BigDecimal("16"), new BigDecimal("20"), "Content"),
                new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                        new BigDecimal("16"), new BigDecimal("20"), "Grammar"),
                new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                        new BigDecimal("12"), new BigDecimal("15"), "Vocabulary"),
                new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                        new BigDecimal("12"), new BigDecimal("15"), "Coherence"),
                new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.FLUENCY, null, null, "No audio",
                        SpeakingCriterionAvailability.NOT_SCORABLE),
                new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.PRONUNCIATION_DELIVERY, null, null, "No audio",
                        SpeakingCriterionAvailability.NOT_SCORABLE));
    }
}
