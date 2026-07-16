package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionClient;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionMediaResolver;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionProperties;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionRequest;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionResult;
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
    void dtoToStringDoesNotExposeTranscriptOrUserIdentity() {
        SpeakingEvaluationApplicationService.EvaluationInput input = input(null, "저는 학생입니다.");

        assertThat(input.toString())
                .doesNotContain("저는 학생입니다")
                .doesNotContain("77");
    }

    private Fixture fixture(boolean transcriptionEnabled,
                            boolean evaluatorEnabled,
                            boolean textFallbackEnabled,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> rows) {
        return fixture(transcriptionEnabled, evaluatorEnabled, textFallbackEnabled, rows, rows);
    }

    private Fixture fixture(boolean transcriptionEnabled,
                            boolean evaluatorEnabled,
                            boolean textFallbackEnabled,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> firstRows,
                            List<PracticeSpeakingMediaRepository.TranscriptionAuthorizationProjection> secondRows) {
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
        SpeakingTranscriptionProperties transcriptionProperties = transcriptionProperties(transcriptionEnabled);
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
        SpeakingEvaluatorProperties evaluatorProperties = evaluatorProperties(evaluatorEnabled);
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
                textFallbackEnabled);
        return new Fixture(service, transcriptionClient, evaluationClient, transcriptionCalls);
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
        return new SpeakingTranscriptionProperties(
                enabled,
                "openai",
                "https://api.openai.com/v1",
                "secret-key",
                "gpt-4o-mini-transcribe",
                "ko",
                26214400L,
                Duration.ofSeconds(30),
                0,
                true,
                "audio/webm,audio/mp4");
    }

    private SpeakingEvaluatorProperties evaluatorProperties(boolean enabled) {
        return new SpeakingEvaluatorProperties(
                enabled,
                "openai-compatible",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "secret-key",
                "models/gemini-2.5-flash",
                Duration.ofSeconds(30),
                0,
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1");
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
        return new SpeakingEvaluationResult(
                status,
                scoreAvailable,
                source,
                "models/gemini-2.5-flash",
                "gpt-4o-mini-transcribe",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
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

        @Override
        public SpeakingEvaluationProviderResult evaluate(SpeakingEvaluationRequest request) {
            calls++;
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
    }
}
