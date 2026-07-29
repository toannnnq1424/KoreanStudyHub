package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionClient;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionMediaResolver;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionProperties;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionResult;
import com.ksh.features.practice.manage.speaking.SpeakingPromptEvaluationContextService;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class SpeakingEvaluationApplicationServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void disabledGatesSkipProviderPipeline() {
        Fixture fixture = fixture(false, true, false, List.of(row(12L, 3L, "audio/webm")));

        SpeakingEvaluationApplicationService.Evaluation evaluation = fixture.service.evaluateQuestion(input(null, "저는 학생입니다."));

        assertThat(evaluation.skipped()).isTrue();
        assertThat(fixture.transcriptionCalls.get()).isZero();
        assertThat(fixture.evaluationClient.calls()).isZero();
    }

    @Test
    void incompleteOrUnsupportedProviderConfigurationSkipsEntirePipeline() {
        List<PracticeSpeakingMediaRepository
                .TranscriptionAuthorizationProjection> rows =
                List.of(row(12L, 3L, "audio/webm"));
        Fixture missingEvaluatorKey = fixture(
                true,
                true,
                false,
                rows,
                rows,
                true,
                "openai",
                "stt-key",
                "openai-compatible",
                "");
        Fixture unsupportedEvaluator = fixture(
                true,
                true,
                false,
                rows,
                rows,
                true,
                "openai",
                "stt-key",
                "unsupported",
                "evaluator-key");

        for (Fixture fixture :
                List.of(missingEvaluatorKey, unsupportedEvaluator)) {
            assertThat(fixture.service.enabled()).isFalse();
            assertThat(fixture.service.evaluateQuestion(
                    input(null, "저는 학생입니다.")).skipped())
                    .isTrue();
            assertThat(fixture.transcriptionCalls.get()).isZero();
            assertThat(fixture.evaluationClient.calls()).isZero();
        }
        assertThat(missingEvaluatorKey.service
                .evaluationContractIdentity())
                .isNotEqualTo(unsupportedEvaluator.service
                        .evaluationContractIdentity());
    }

    @Test
    void matchingStoredAudioResultIsReusedWithoutProviderCalls() {
        SpeakingEvaluationResult stored = storedResult(SpeakingEvaluationStatus.EVALUATED,
                SpeakingEvaluationSource.PROVIDER, true, false, 12L, 3L, "저는 학생입니다.");
        Fixture fixture = fixture(true, true, false, List.of(row(12L, 3L, "audio/webm")));

        SpeakingEvaluationApplicationService.Evaluation evaluation = fixture.service.evaluateQuestion(input(stored, "저는 학생입니다."));

        assertThat(evaluation.reused()).isTrue();
        assertThat(evaluation.result()).isSameAs(stored);
        assertThat(fixture.transcriptionCalls.get()).isZero();
        assertThat(fixture.evaluationClient.calls()).isZero();
    }

    @Test
    void changedAudioIdentityEvaluatesAndPersistsNewIdentity() {
        SpeakingEvaluationResult stored = storedResult(SpeakingEvaluationStatus.EVALUATED,
                SpeakingEvaluationSource.PROVIDER, true, false, 12L, 3L, "저는 학생입니다.");
        Fixture fixture = fixture(true, true, false, List.of(row(99L, 1L, "audio/webm")));

        SpeakingEvaluationApplicationService.Evaluation evaluation = fixture.service.evaluateQuestion(input(stored, "저는 학생입니다."));

        assertThat(evaluation.reused()).isFalse();
        assertThat(evaluation.result().audioMediaId()).isEqualTo(99L);
        assertThat(evaluation.result().mediaVersion()).isEqualTo(1L);
        assertThat(evaluation.result().scoreAvailable()).isFalse();
        assertThat(evaluation.result().overallScore()).isNull();
        assertThat(evaluation.result().profileAvailable()).isTrue();
        assertThat(evaluation.result().transcriptConfidence()).isEqualByComparingTo("0.82");
        assertThat(evaluation.result().promptContextFingerprint()).isNotBlank();
        assertThat(evaluation.result().promptContextContractIdentity())
                .isEqualTo(SpeakingPromptEvaluationContextService
                        .LEGACY_CONTRACT_IDENTITY);
        assertThat(evaluation.result().policyBundleId()).isEqualTo(
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(evaluation.result().policyBundleFingerprint()).isEqualTo(
                SpeakingAssessmentPolicyBundle.fingerprint());
        assertThat(fixture.transcriptionCalls.get()).isEqualTo(1);
        assertThat(fixture.evaluationClient.calls()).isEqualTo(1);
    }

    @Test
    void staleAudioIdentityAfterProviderResultPersistsSafeUnavailableFailure() {
        Fixture fixture = fixture(
                true,
                true,
                false,
                List.of(row(12L, 3L, "audio/webm")),
                List.of(row(99L, 1L, "audio/webm")));

        SpeakingEvaluationApplicationService.Evaluation evaluation = fixture.service.evaluateQuestion(input(null, "저는 학생입니다."));

        assertThat(evaluation.reused()).isFalse();
        assertThat(evaluation.result().evaluationStatus()).isEqualTo(SpeakingEvaluationStatus.AUDIO_UNAVAILABLE);
        assertThat(evaluation.result().scoreAvailable()).isFalse();
        assertThat(evaluation.result().audioMediaId()).isEqualTo(12L);
        assertThat(evaluation.result().mediaVersion()).isEqualTo(3L);
        assertThat(evaluation.result().errorCategory()).isEqualTo("STALE_AUDIO_IDENTITY");
        assertThat(evaluation.result().retryable()).isTrue();
        assertThat(fixture.transcriptionCalls.get()).isEqualTo(1);
        assertThat(fixture.evaluationClient.calls()).isEqualTo(1);
    }

    @Test
    void matchingSuccessAvoidsTransientProviderFailureByReusingStoredResult() {
        SpeakingEvaluationResult stored = storedResult(SpeakingEvaluationStatus.EVALUATED,
                SpeakingEvaluationSource.PROVIDER, true, false, 12L, 3L, "저는 학생입니다.");
        Fixture fixture = fixture(true, true, false, List.of(row(12L, 3L, "audio/webm")));
        fixture.evaluationClient.nextResult = SpeakingEvaluationProviderResult.failure(
                SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                "openai-compatible",
                "models/gemini-2.5-flash",
                "PROVIDER_TRANSPORT_ERROR",
                true,
                5L);

        SpeakingEvaluationApplicationService.Evaluation evaluation = fixture.service.evaluateQuestion(input(stored, "저는 학생입니다."));

        assertThat(evaluation.result()).isSameAs(stored);
        assertThat(evaluation.reused()).isTrue();
        assertThat(fixture.transcriptionCalls.get()).isZero();
        assertThat(fixture.evaluationClient.calls()).isZero();
    }

    @Test
    void noReadyAudioWithTextFallbackEnabledEvaluatesTextFallback() {
        Fixture fixture = fixture(true, true, true, List.of());

        SpeakingEvaluationApplicationService.Evaluation evaluation = fixture.service.evaluateQuestion(input(null, "  저는   학생입니다. "));

        assertThat(evaluation.result().evaluationStatus()).isEqualTo(SpeakingEvaluationStatus.TEXT_FALLBACK_EVALUATED);
        assertThat(evaluation.result().source()).isEqualTo(SpeakingEvaluationSource.TEXT_FALLBACK);
        assertThat(evaluation.result().audioMediaId()).isNull();
        assertThat(evaluation.result().scoreAvailable()).isFalse();
        assertThat(evaluation.result().overallScore()).isNull();
        assertThat(evaluation.result().profileAvailable()).isTrue();
        assertThat(fixture.transcriptionCalls.get()).isZero();
        assertThat(fixture.evaluationClient.calls()).isEqualTo(1);
    }

    @Test
    void noReadyAudioWithoutTextFallbackPersistsAudioMissingFailure() {
        Fixture fixture = fixture(true, true, false, List.of());

        SpeakingEvaluationApplicationService.Evaluation evaluation = fixture.service.evaluateQuestion(input(null, "저는 학생입니다."));

        assertThat(evaluation.result().evaluationStatus()).isEqualTo(SpeakingEvaluationStatus.AUDIO_MISSING);
        assertThat(evaluation.result().scoreAvailable()).isFalse();
        assertThat(fixture.transcriptionCalls.get()).isZero();
        assertThat(fixture.evaluationClient.calls()).isZero();
    }

    @Test
    void v2EvaluatorKeepsImmutablePromptContextSeparateFromLearnerTranscript() {
        Fixture fixture = fixture(
                true,
                true,
                false,
                List.of(row(12L, 3L, "audio/webm")));
        SpeakingEvaluationApplicationService.EvaluationInput input =
                new SpeakingEvaluationApplicationService.EvaluationInput(
                        77L,
                        10L,
                        11L,
                        101L,
                        "question-content-v2",
                        "",
                        null,
                        null,
                        null,
                        "",
                        null);

        SpeakingEvaluationApplicationService.Evaluation evaluation =
                fixture.service.evaluateQuestion(input);

        assertThat(fixture.evaluationClient.lastRequest()
                .promptContext()).isEqualTo("질문을 듣고 대답하세요.");
        assertThat(fixture.evaluationClient.lastRequest()
                .actuallyHeardTranscript()).isEqualTo("저는 학생입니다.");
        assertThat(fixture.evaluationClient.lastRequest()
                .promptContext()).isNotEqualTo(
                        fixture.evaluationClient.lastRequest()
                                .actuallyHeardTranscript());
        assertThat(evaluation.result().questionVersionId()).isEqualTo(101L);
        assertThat(evaluation.result().promptContextFingerprint())
                .isEqualTo("c".repeat(64));
    }

    @Test
    void dtoToStringDoesNotExposeTranscriptOrUserIdentity() {
        SpeakingEvaluationApplicationService.EvaluationInput input = input(null, "저는 학생입니다.");

        assertThat(input.toString())
                .doesNotContain("저는 학생입니다")
                .doesNotContain("77");
    }

    @Test
    void contractIdentityChangesWhenLogprobClassificationPolicyChanges() {
        Fixture withLogprobs = fixture(
                true,
                true,
                false,
                List.of(),
                List.of(),
                true);
        Fixture withoutLogprobs = fixture(
                true,
                true,
                false,
                List.of(),
                List.of(),
                false);

        assertThat(withLogprobs.service.evaluationContractIdentity())
                .startsWith("ksh-speaking-evaluation-v2|sha256|")
                .isNotEqualTo(
                        withoutLogprobs.service
                                .evaluationContractIdentity());
    }

    private Fixture fixture(boolean transcriptionEnabled,
                            boolean evaluatorEnabled,
                            boolean textFallbackEnabled,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> rows) {
        return fixture(
                transcriptionEnabled,
                evaluatorEnabled,
                textFallbackEnabled,
                rows,
                rows,
                true);
    }

    private Fixture fixture(boolean transcriptionEnabled,
                            boolean evaluatorEnabled,
                            boolean textFallbackEnabled,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> firstRows,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> secondRows) {
        return fixture(
                transcriptionEnabled,
                evaluatorEnabled,
                textFallbackEnabled,
                firstRows,
                secondRows,
                true);
    }

    private Fixture fixture(boolean transcriptionEnabled,
                            boolean evaluatorEnabled,
                            boolean textFallbackEnabled,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> firstRows,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> secondRows,
                            boolean includeLogprobs) {
        return fixture(
                transcriptionEnabled,
                evaluatorEnabled,
                textFallbackEnabled,
                firstRows,
                secondRows,
                includeLogprobs,
                "openai",
                "secret-key",
                "openai-compatible",
                "secret-key");
    }

    private Fixture fixture(boolean transcriptionEnabled,
                            boolean evaluatorEnabled,
                            boolean textFallbackEnabled,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> firstRows,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> secondRows,
                            boolean includeLogprobs,
                            String transcriptionProvider,
                            String transcriptionApiKey,
                            String evaluatorProvider,
                            String evaluatorApiKey) {
        PracticeSpeakingMediaRepository repository = mock(PracticeSpeakingMediaRepository.class);
        SpeakingAudioStorage storage = mock(SpeakingAudioStorage.class);
        when(repository.findAuthorizedTranscriptionCandidates(77L, 10L, 11L, PracticeSpeakingMediaStatus.READY))
                .thenReturn(firstRows, secondRows);
        for (PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection row : concat(firstRows, secondRows)) {
            when(storage.exists(row.getStorageKey())).thenReturn(true);
            try {
                when(storage.open(row.getStorageKey())).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
        SpeakingTranscriptionProperties transcriptionProperties =
                transcriptionProperties(
                        transcriptionEnabled,
                        includeLogprobs,
                        transcriptionProvider,
                        transcriptionApiKey);
        SpeakingTranscriptionMediaResolver resolver =
                new SpeakingTranscriptionMediaResolver(repository, storage, transcriptionProperties);
        AtomicInteger transcriptionCalls = new AtomicInteger();
        SpeakingTranscriptionClient transcriptionClient = request -> {
            transcriptionCalls.incrementAndGet();
            return new SpeakingTranscriptionResult(
                    SpeakingEvaluationStatus.EVALUATED,
                    SpeakingEvaluationSource.PROVIDER,
                    "openai",
                    "gpt-4o-mini-transcribe",
                    "ko",
                    "저는 학생입니다.",
                    "저는 학생입니다.",
                    new BigDecimal("0.82"),
                    null,
                    1200L,
                    20L,
                    null,
                    false);
        };
        FakeEvaluationClient evaluationClient = new FakeEvaluationClient();
        SpeakingEvaluatorProperties evaluatorProperties =
                evaluatorProperties(
                        evaluatorEnabled,
                        evaluatorProvider,
                        evaluatorApiKey);
        SpeakingEvaluationOrchestrator orchestrator = new SpeakingEvaluationOrchestrator(
                evaluationClient,
                new SpeakingEvaluationNormalizer(),
                evaluatorProperties,
                objectMapper);
        SpeakingEvaluationApplicationService service = new SpeakingEvaluationApplicationService(
                resolver,
                transcriptionClient,
                orchestrator,
                new SpeakingEvaluationReusePolicy(),
                transcriptionProperties,
                evaluatorProperties,
                null,
                promptContextService(),
                textFallbackEnabled);
        return new Fixture(service, transcriptionClient, evaluationClient, transcriptionCalls);
    }

    private SpeakingPromptEvaluationContextService promptContextService() {
        SpeakingPromptEvaluationContextService service =
                mock(SpeakingPromptEvaluationContextService.class);
        when(service.resolve(
                any(), any(), anyString())).thenAnswer(invocation -> {
            Long questionVersionId = invocation.getArgument(0);
            String schema = invocation.getArgument(1);
            String prompt = invocation.getArgument(2);
            if ("question-content-v2".equals(schema)) {
                return new SpeakingPromptEvaluationContextService
                        .EvaluatorContext(
                                questionVersionId,
                                "질문을 듣고 대답하세요.",
                                "c".repeat(64),
                                "speaking-prompt-version-context-v1");
            }
            return SpeakingPromptEvaluationContextService.legacy(
                    questionVersionId, prompt);
        });
        return service;
    }

    private List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> concat(
            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> first,
            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> second
    ) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    private SpeakingEvaluationApplicationService.EvaluationInput input(SpeakingEvaluationResult stored, String answer) {
        return new SpeakingEvaluationApplicationService.EvaluationInput(
                77L,
                10L,
                11L,
                "자기소개를 하세요.",
                null,
                null,
                answer,
                stored);
    }

    private SpeakingTranscriptionProperties transcriptionProperties(boolean enabled) {
        return transcriptionProperties(enabled, true);
    }

    private SpeakingTranscriptionProperties transcriptionProperties(
            boolean enabled,
            boolean includeLogprobs) {
        return transcriptionProperties(
                enabled,
                includeLogprobs,
                "openai",
                "secret-key");
    }

    private SpeakingTranscriptionProperties transcriptionProperties(
            boolean enabled,
            boolean includeLogprobs,
            String provider,
            String apiKey) {
        return new SpeakingTranscriptionProperties(
                enabled,
                provider,
                "https://api.openai.com/v1",
                apiKey,
                "gpt-4o-mini-transcribe",
                "ko",
                26214400L,
                Duration.ofSeconds(30),
                0,
                includeLogprobs,
                "audio/webm,audio/mp4");
    }

    private SpeakingEvaluatorProperties evaluatorProperties(boolean enabled) {
        return evaluatorProperties(
                enabled,
                "openai-compatible",
                "secret-key");
    }

    private SpeakingEvaluatorProperties evaluatorProperties(
            boolean enabled,
            String provider,
            String apiKey) {
        return new SpeakingEvaluatorProperties(
                enabled,
                provider,
                "https://generativelanguage.googleapis.com/v1beta/openai",
                apiKey,
                "models/gemini-2.5-flash",
                Duration.ofSeconds(30),
                0,
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION);
    }

    private PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection row(Long mediaId, Long version, String mimeType) {
        return new TestProjection(mediaId, version, mimeType);
    }

    private SpeakingEvaluationResult storedResult(
            SpeakingEvaluationStatus status,
            SpeakingEvaluationSource source,
            boolean scoreAvailable,
            boolean retryable,
            Long mediaId,
            Long mediaVersion,
            String transcript
    ) {
        SpeakingPromptEvaluationContextService.EvaluatorContext context =
                SpeakingPromptEvaluationContextService.legacy(
                        null, "자기소개를 하세요.");
        return new SpeakingEvaluationResult(
                status,
                scoreAvailable,
                source,
                "models/gemini-2.5-flash",
                "gpt-4o-mini-transcribe",
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION,
                SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                SpeakingContractTrust.CURRENT_VERIFIED,
                context.questionVersionId(),
                context.promptContextFingerprint(),
                context.promptContextContractIdentity(),
                mediaId,
                mediaVersion,
                transcript,
                transcript,
                transcript,
                null,
                null,
                null,
                null,
                scoreAvailable ? new BigDecimal("78") : null,
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

    private record Fixture(
            SpeakingEvaluationApplicationService service,
            SpeakingTranscriptionClient transcriptionClient,
            FakeEvaluationClient evaluationClient,
            AtomicInteger transcriptionCalls
    ) {}

    private record TestProjection(Long mediaId, Long lockVersion, String mimeType)
            implements PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection {
        @Override public Long getMediaId() { return mediaId; }
        @Override public Long getAttemptId() { return 10L; }
        @Override public Long getQuestionId() { return 11L; }
        @Override public Long getLockVersion() { return lockVersion; }
        @Override public PracticeSpeakingStorageProvider getStorageProvider() { return PracticeSpeakingStorageProvider.LOCAL; }
        @Override public String getStorageKey() { return "learner-speaking/test-" + mediaId + ".webm"; }
        @Override public String getMimeType() { return mimeType; }
        @Override public Long getByteSize() { return 100L; }
        @Override public Long getDurationMs() { return 1200L; }
    }

    private class FakeEvaluationClient implements SpeakingEvaluationClient {
        private int calls;
        private SpeakingEvaluationProviderResult nextResult;
        private SpeakingEvaluationRequest lastRequest;

        @Override
        public SpeakingEvaluationProviderResult evaluate(SpeakingEvaluationRequest request) {
            calls++;
            lastRequest = request;
            if (nextResult != null) {
                return nextResult;
            }
            try {
                JsonNode json = objectMapper.readTree(OpenAiCompatibleSpeakingEvaluationClientTest.validEvaluationJson());
                return SpeakingEvaluationProviderResult.success(json, "openai-compatible", "models/gemini-2.5-flash", 5L);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        int calls() {
            return calls;
        }

        SpeakingEvaluationRequest lastRequest() {
            return lastRequest;
        }
    }
}
