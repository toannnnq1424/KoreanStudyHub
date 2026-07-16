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
        SpeakingEvaluationIdentity identity = SpeakingEvaluationIdentity.audio(
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

        assertThat(policy.decide(stored, SpeakingEvaluationIdentity.audio(
                1L, 2L, 99L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, SpeakingEvaluationIdentity.audio(
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

        assertThat(policy.decide(stored, SpeakingEvaluationIdentity.audio(
                1L, 2L, 12L, 3L, "gpt-4o-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, SpeakingEvaluationIdentity.audio(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-pro", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, SpeakingEvaluationIdentity.audio(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v2",
                "speaking-rubric-v1", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, SpeakingEvaluationIdentity.audio(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v2", "speaking-schema-v1"), true).reuse()).isFalse();
        assertThat(policy.decide(stored, SpeakingEvaluationIdentity.audio(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v2"), true).reuse()).isFalse();
    }

    @Test
    void legacyOrMockIsNotReusableWhenRealProviderIsEnabled() {
        SpeakingEvaluationIdentity identity = SpeakingEvaluationIdentity.audio(
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
        SpeakingEvaluationIdentity identity = SpeakingEvaluationIdentity.audio(
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
        SpeakingEvaluationIdentity identity = SpeakingEvaluationIdentity.audio(
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
        SpeakingEvaluationIdentity changedIdentity = SpeakingEvaluationIdentity.audio(
                1L, 2L, 99L, 1L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1");

        assertThat(policy.preserveSuccessOnTransientFailure(success, transientFailure, changedIdentity)).isSameAs(transientFailure);
    }

    @Test
    void textFallbackIdentityUsesNormalizedHashWithoutExposingAnswer() {
        SpeakingEvaluationIdentity identity = SpeakingEvaluationIdentity.textFallback(
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
        return new SpeakingEvaluationResult(
                status,
                scoreAvailable,
                source,
                evaluatorModel,
                transcriptionModel,
                promptVersion,
                rubricVersion,
                schemaVersion,
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
                List.of(),
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
}
