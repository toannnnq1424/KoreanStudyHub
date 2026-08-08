package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingEvaluationReusePolicyTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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
        assertThat(policy.decide(
                stored,
                SpeakingEvaluationIdentity.audio(
                        1L, 2L, null, null, null,
                        12L, 3L, "gpt-4o-mini-transcribe",
                        "models/gemini-2.5-flash",
                        SpeakingPromptRules.PROMPT_VERSION,
                        SpeakingPromptRules.RUBRIC_VERSION,
                        SpeakingPromptRules.SCHEMA_VERSION,
                        "STALE_BUNDLE"),
                true).reuse()).isFalse();
    }

    @Test
    void changedFullPolicyFingerprintForcesReevaluationWhenConciseVersionsMatch() {
        SpeakingEvaluationResult stored = withPolicyBundleFingerprint(
                result(
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
                        "저는 학생입니다."),
                "0".repeat(64));
        SpeakingEvaluationIdentity currentIdentity = audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1");

        assertThat(stored.policyBundleId())
                .isEqualTo(SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(stored.policyBundleFingerprint())
                .isNotEqualTo(SpeakingAssessmentPolicyBundle.fingerprint());
        assertThat(stored.currentEvidenceContract()).isFalse();
        assertThat(policy.decide(stored, currentIdentity, true).reuse())
                .isFalse();
    }

    @Test
    void mockIsNotReusableWhenRealProviderIsEnabled() {
        SpeakingEvaluationIdentity identity = audioIdentity(
                1L, 2L, 12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash", "speaking-eval-v1",
                "speaking-rubric-v1", "speaking-schema-v1");

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

    @Test
    void immutableQuestionVersionContextIdentityControlsReuse() {
        SpeakingEvaluationResult stored = withPromptContext(
                result(
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
                        "저는 학생입니다."),
                101L,
                "a".repeat(64),
                "speaking-prompt-version-context-v1");
        SpeakingEvaluationIdentity exact = SpeakingEvaluationIdentity.audio(
                1L, 2L, 101L, "a".repeat(64),
                "speaking-prompt-version-context-v1",
                12L, 3L, "gpt-4o-mini-transcribe",
                "models/gemini-2.5-flash",
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION);

        assertThat(policy.decide(stored, exact, true).reuse()).isTrue();
        assertThat(policy.decide(
                stored,
                SpeakingEvaluationIdentity.audio(
                        1L, 2L, 202L, "a".repeat(64),
                        "speaking-prompt-version-context-v1",
                        12L, 3L, "gpt-4o-mini-transcribe",
                        "models/gemini-2.5-flash",
                        SpeakingPromptRules.PROMPT_VERSION,
                        SpeakingPromptRules.RUBRIC_VERSION,
                        SpeakingPromptRules.SCHEMA_VERSION),
                true).reuse()).isFalse();
        assertThat(policy.decide(
                stored,
                SpeakingEvaluationIdentity.audio(
                        1L, 2L, 101L, "b".repeat(64),
                        "speaking-prompt-version-context-v1",
                        12L, 3L, "gpt-4o-mini-transcribe",
                        "models/gemini-2.5-flash",
                        SpeakingPromptRules.PROMPT_VERSION,
                        SpeakingPromptRules.RUBRIC_VERSION,
                        SpeakingPromptRules.SCHEMA_VERSION),
                true).reuse()).isFalse();
    }

    @Test
    void configuredDefaultPromptIdentityIsCurrentAndReusable()
            throws Exception {
        String applicationProperties = Files.readString(
                Path.of("src/main/resources/application.properties"));
        SpeakingEvaluatorProperties defaults =
                new SpeakingEvaluatorProperties(
                        false,
                        "openai-compatible",
                        "https://example.invalid",
                        "",
                        "models/gemini-2.5-flash",
                        Duration.ofSeconds(30),
                        0,
                        "",
                        "",
                        "");

        assertThat(applicationProperties).contains(
                "PRACTICE_SPEAKING_EVALUATOR_PROMPT_VERSION:}");
        assertThat(defaults.promptVersion())
                .isEqualTo(SpeakingPromptRules.PROMPT_VERSION);

        SpeakingEvaluationResult stored = withPromptContext(
                result(
                        SpeakingEvaluationStatus.EVALUATED,
                        SpeakingEvaluationSource.PROVIDER,
                        true,
                        false,
                        12L,
                        3L,
                        "gpt-4o-mini-transcribe",
                        defaults.model(),
                        defaults.promptVersion(),
                        defaults.rubricVersion(),
                        defaults.schemaVersion(),
                        "저는 학생입니다."),
                101L,
                "a".repeat(64),
                "speaking-prompt-version-context-v1");
        SpeakingEvaluationIdentity identity =
                SpeakingEvaluationIdentity.audio(
                        1L,
                        2L,
                        101L,
                        "a".repeat(64),
                        "speaking-prompt-version-context-v1",
                        12L,
                        3L,
                        "gpt-4o-mini-transcribe",
                        defaults.model(),
                        defaults.promptVersion(),
                        defaults.rubricVersion(),
                        defaults.schemaVersion());

        assertThat(stored.currentEvidenceContract()).isTrue();
        assertThat(policy.decide(stored, identity, true).reuse()).isTrue();
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
        boolean nonCurrent = status == SpeakingEvaluationStatus.MOCK_EVALUATED
                || source == SpeakingEvaluationSource.MOCK;
        boolean successfulCurrentContract =
                status == SpeakingEvaluationStatus.EVALUATED
                        || status
                        == SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED;
        if (!nonCurrent && successfulCurrentContract) {
            String effectiveTranscript = actuallyHeardTranscript == null
                    ? "저는 학생입니다." : actuallyHeardTranscript;
            String finalPromptVersion = promptVersion;
            String finalRubricVersion = rubricVersion;
            String finalSchemaVersion = schemaVersion;
            return SpeakingEvaluationTestFixtures.currentResult(
                    OBJECT_MAPPER,
                    effectiveTranscript,
                    new BigDecimal("16"),
                    json -> {
                        json.put("evaluation_status", status.name());
                        json.put("source", source.name());
                        json.put("model", evaluatorModel);
                        if (transcriptionModel == null) {
                            json.putNull("transcription_model");
                        } else {
                            json.put("transcription_model",
                                    transcriptionModel);
                        }
                        json.put("prompt_version", finalPromptVersion);
                        json.put("rubric_version", finalRubricVersion);
                        json.put("schema_version", finalSchemaVersion);
                        if (mediaId == null) {
                            json.putNull("audio_media_id");
                        } else {
                            json.put("audio_media_id", mediaId);
                        }
                        if (mediaVersion == null) {
                            json.putNull("media_version");
                        } else {
                            json.put("media_version", mediaVersion);
                        }
                        json.put("retryable", retryable);
                    });
        }
        return new SpeakingEvaluationResult(
                status,
                scoreAvailable,
                source,
                evaluatorModel,
                transcriptionModel,
                promptVersion,
                rubricVersion,
                schemaVersion,
                nonCurrent ? null
                        : SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                nonCurrent ? SpeakingEvaluatorCapability.LEGACY_UNKNOWN
                        : SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                nonCurrent ? SpeakingEvidenceMode.UNKNOWN : SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                nonCurrent ? null : SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                nonCurrent ? SpeakingContractTrust.LEGACY_UNVERIFIED
                        : SpeakingContractTrust.CURRENT_VERIFIED,
                null,
                null,
                null,
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

    private SpeakingEvaluationResult withPromptContext(
            SpeakingEvaluationResult result,
            Long questionVersionId,
            String fingerprint,
            String contractIdentity) {
        return new SpeakingEvaluationResult(
                result.evaluationStatus(),
                result.scoreAvailable(),
                result.source(),
                result.model(),
                result.transcriptionModel(),
                result.promptVersion(),
                result.rubricVersion(),
                result.schemaVersion(),
                result.policyBundleId(),
                result.evaluatorCapability(),
                result.evidenceMode(),
                result.evidenceContractVersion(),
                result.contractTrust(),
                questionVersionId,
                fingerprint,
                contractIdentity,
                result.audioMediaId(),
                result.mediaVersion(),
                result.transcript(),
                result.normalizedTranscript(),
                result.actuallyHeardTranscript(),
                result.interpretedIntent(),
                result.intentConfidence(),
                result.transcriptConfidence(),
                result.listenerBurden(),
                result.overallScore(),
                result.levelLabel(),
                result.overallSummary(),
                result.taskAchievementSummary(),
                result.majorStrengths(),
                result.majorNeedsImprovement(),
                result.actionPlan(),
                result.criterionFeedback(),
                result.transcriptAnnotations(),
                result.strengths(),
                result.needsImprovement(),
                result.confidenceNotes(),
                result.rubricScores(),
                result.findings(),
                result.evidence(),
                result.recommendations(),
                result.upgradedAnswer(),
                result.sampleAnswer(),
                result.pronunciationAdvisory(),
                result.fluencyObservations(),
                result.errorCategory(),
                result.retryable(),
                result.policyBundleFingerprint());
    }

    private SpeakingEvaluationResult withPolicyBundleFingerprint(
            SpeakingEvaluationResult result,
            String policyBundleFingerprint
    ) {
        return new SpeakingEvaluationResult(
                result.evaluationStatus(),
                result.scoreAvailable(),
                result.source(),
                result.model(),
                result.transcriptionModel(),
                result.promptVersion(),
                result.rubricVersion(),
                result.schemaVersion(),
                result.policyBundleId(),
                result.evaluatorCapability(),
                result.evidenceMode(),
                result.evidenceContractVersion(),
                result.contractTrust(),
                result.questionVersionId(),
                result.promptContextFingerprint(),
                result.promptContextContractIdentity(),
                result.audioMediaId(),
                result.mediaVersion(),
                result.transcript(),
                result.normalizedTranscript(),
                result.actuallyHeardTranscript(),
                result.interpretedIntent(),
                result.intentConfidence(),
                result.transcriptConfidence(),
                result.listenerBurden(),
                result.overallScore(),
                result.levelLabel(),
                result.overallSummary(),
                result.taskAchievementSummary(),
                result.majorStrengths(),
                result.majorNeedsImprovement(),
                result.actionPlan(),
                result.criterionFeedback(),
                result.transcriptAnnotations(),
                result.strengths(),
                result.needsImprovement(),
                result.confidenceNotes(),
                result.rubricScores(),
                result.findings(),
                result.evidence(),
                result.recommendations(),
                result.upgradedAnswer(),
                result.sampleAnswer(),
                result.pronunciationAdvisory(),
                result.fluencyObservations(),
                result.errorCategory(),
                result.retryable(),
                policyBundleFingerprint);
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
