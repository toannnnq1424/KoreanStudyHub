package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpeakingPromptProviderAdapterTest {

    @Test
    void springUsesTheProductionConstructorForBothProviderAdapters()
            throws NoSuchMethodException {
        assertThat(OpenAiSpeakingPromptSttAdapter.class.getConstructor(
                SpeakingPromptAuthoringAiProperties.class,
                ObjectMapper.class,
                PracticeAiMetrics.class).isAnnotationPresent(Autowired.class))
                .isTrue();
        assertThat(OpenAiSpeakingPromptTtsAdapter.class.getConstructor(
                SpeakingPromptAuthoringAiProperties.class,
                SpeakingPromptAudioVerifier.class,
                PracticeAiMetrics.class).isAnnotationPresent(Autowired.class))
                .isTrue();
    }

    @Test
    void oneSttAdapterInvocationMakesAtMostOneTransportCall() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        AtomicInteger calls = new AtomicInteger();
        OpenAiSpeakingPromptSttAdapter adapter =
                new OpenAiSpeakingPromptSttAdapter(
                        properties,
                        new ObjectMapper(),
                        mock(PracticeAiMetrics.class),
                        (request, config) -> {
                            calls.incrementAndGet();
                            return new OpenAiSpeakingPromptSttAdapter.ProviderResponse(
                                    200,
                                    "{\"text\":\"전사 결과\",\"confidence\":0.91}",
                                    "private-provider-request");
                        });

        SpeakingPromptAiContract.SttResult result =
                adapter.transcribe(sttRequest());

        assertThat(calls).hasValue(1);
        assertThat(result.providerTranscript()).isEqualTo("전사 결과");
        assertThat(result.toString())
                .doesNotContain("전사 결과")
                .doesNotContain("private-provider-request");
    }

    @Test
    void retryableSttProviderFailureIsMappedWithoutAdapterRetry() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        AtomicInteger calls = new AtomicInteger();
        OpenAiSpeakingPromptSttAdapter adapter =
                new OpenAiSpeakingPromptSttAdapter(
                        properties,
                        new ObjectMapper(),
                        mock(PracticeAiMetrics.class),
                        (request, config) -> {
                            calls.incrementAndGet();
                            return new OpenAiSpeakingPromptSttAdapter.ProviderResponse(
                                    429, "{}", "private-provider-request");
                        });

        assertThatThrownBy(() -> adapter.transcribe(sttRequest()))
                .isInstanceOf(SpeakingPromptAiContract.ProviderFailure.class)
                .satisfies(failure -> {
                    SpeakingPromptAiContract.ProviderFailure mapped =
                            (SpeakingPromptAiContract.ProviderFailure) failure;
                    assertThat(mapped.publicCategory()).isEqualTo(
                            SpeakingPromptAiContract.PublicErrorCategory.RATE_LIMIT);
                    assertThat(mapped.retryable()).isTrue();
                    assertThat(mapped.toString())
                            .doesNotContain("private-provider-request");
                });
        assertThat(calls).hasValue(1);
    }

    @Test
    void oneTtsAdapterInvocationMakesAtMostOneTransportCallAndVerifiesOutput() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger verifications = new AtomicInteger();
        byte[] generated = "verified-generated-audio".getBytes(
                StandardCharsets.UTF_8);
        SpeakingPromptAudioVerifier verifier = new SpeakingPromptAudioVerifier() {
            @Override
            public SpeakingPromptAiContract.VerifiedAudio verifySttInput(
                    byte[] bytes,
                    String filename,
                    String declaredMimeType,
                    String expectedSha256) {
                throw new AssertionError("TTS must not use the STT verifier path");
            }

            @Override
            public SpeakingPromptAiContract.VerifiedAudio verifyTtsOutput(
                    byte[] bytes,
                    String filename,
                    String declaredMimeType) {
                verifications.incrementAndGet();
                assertThat(bytes).isEqualTo(generated);
                return new SpeakingPromptAiContract.VerifiedAudio(
                        bytes,
                        filename,
                        declaredMimeType,
                        SpeakingPromptAiContract.exactBytesSha256(bytes),
                        2_000L);
            }
        };
        OpenAiSpeakingPromptTtsAdapter adapter =
                new OpenAiSpeakingPromptTtsAdapter(
                        properties,
                        verifier,
                        mock(PracticeAiMetrics.class),
                        (request, config) -> {
                            calls.incrementAndGet();
                            return new OpenAiSpeakingPromptTtsAdapter.ProviderResponse(
                                    200,
                                    generated,
                                    "audio/mpeg; charset=binary",
                                    "private-provider-request");
                        });

        SpeakingPromptAiContract.TtsResult result =
                adapter.synthesize(ttsRequest());

        assertThat(calls).hasValue(1);
        assertThat(verifications).hasValue(1);
        assertThat(result.generatedAudio().bytes()).isEqualTo(generated);
        assertThat(result.toString())
                .doesNotContain("private-provider-request")
                .doesNotContain(new String(generated, StandardCharsets.UTF_8));
    }

    @Test
    void ttsAdapterHonorsBoundedPerRequestVoiceSpeedAndFormatSnapshot() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        AtomicInteger calls = new AtomicInteger();
        byte[] generated = "verified-wav-audio".getBytes(StandardCharsets.UTF_8);
        SpeakingPromptAudioVerifier verifier = new SpeakingPromptAudioVerifier() {
            @Override
            public SpeakingPromptAiContract.VerifiedAudio verifySttInput(
                    byte[] bytes,
                    String filename,
                    String declaredMimeType,
                    String expectedSha256) {
                throw new AssertionError("TTS must not use the STT verifier path");
            }

            @Override
            public SpeakingPromptAiContract.VerifiedAudio verifyTtsOutput(
                    byte[] bytes,
                    String filename,
                    String declaredMimeType) {
                assertThat(bytes).isEqualTo(generated);
                assertThat(filename).isEqualTo("speaking-prompt-ai.wav");
                assertThat(declaredMimeType).isEqualTo("audio/wav");
                return new SpeakingPromptAiContract.VerifiedAudio(
                        bytes,
                        filename,
                        declaredMimeType,
                        SpeakingPromptAiContract.exactBytesSha256(bytes),
                        2_000L);
            }
        };
        OpenAiSpeakingPromptTtsAdapter adapter =
                new OpenAiSpeakingPromptTtsAdapter(
                        properties,
                        verifier,
                        mock(PracticeAiMetrics.class),
                        (request, config) -> {
                            calls.incrementAndGet();
                            assertThat(request.voiceCode()).isEqualTo("verse");
                            assertThat(request.speed())
                                    .isEqualByComparingTo("1.25");
                            assertThat(request.outputFormat()).isEqualTo("wav");
                            return new OpenAiSpeakingPromptTtsAdapter.ProviderResponse(
                                    200,
                                    generated,
                                    "audio/wav",
                                    "private-provider-request");
                        });
        String prompt = "질문을 듣고 답하세요.";
        SpeakingPromptAiContract.TtsRequest request =
                new SpeakingPromptAiContract.TtsRequest(
                        prompt,
                        SpeakingPromptAiContract.unicodeNfcUtf8Sha256(prompt),
                        "ko",
                        "verse",
                        new BigDecimal("1.25"),
                        "wav",
                        SpeakingPromptAiContract.CONTRACT_VERSION);

        SpeakingPromptAiContract.TtsResult result = adapter.synthesize(request);

        assertThat(calls).hasValue(1);
        assertThat(result.voiceCode()).isEqualTo("verse");
        assertThat(result.speed()).isEqualByComparingTo("1.25");
        assertThat(result.outputFormat()).isEqualTo("wav");
    }

    @Test
    void sttTimeoutAndTransportFailuresRemainDistinctAndRetryable() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        OpenAiSpeakingPromptSttAdapter timeout =
                new OpenAiSpeakingPromptSttAdapter(
                        properties,
                        new ObjectMapper(),
                        mock(PracticeAiMetrics.class),
                        (request, config) -> {
                            throw new ResourceAccessException(
                                    "private timeout",
                                    new SocketTimeoutException("private"));
                        });
        OpenAiSpeakingPromptSttAdapter transport =
                new OpenAiSpeakingPromptSttAdapter(
                        properties,
                        new ObjectMapper(),
                        mock(PracticeAiMetrics.class),
                        (request, config) -> {
                            throw new ResourceAccessException(
                                    "private transport",
                                    new IOException("private"));
                        });

        assertProviderFailure(
                () -> timeout.transcribe(sttRequest()),
                SpeakingPromptAiContract.PublicErrorCategory.TIMEOUT,
                true);
        assertProviderFailure(
                () -> transport.transcribe(sttRequest()),
                SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                true);
    }

    @Test
    void authoringProvider4xxAnd5xxFailClosedWithCorrectRetryability() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        OpenAiSpeakingPromptSttAdapter rejected =
                new OpenAiSpeakingPromptSttAdapter(
                        properties,
                        new ObjectMapper(),
                        mock(PracticeAiMetrics.class),
                        (request, config) ->
                                new OpenAiSpeakingPromptSttAdapter.ProviderResponse(
                                        400,
                                        "{\"private\":\"provider detail\"}",
                                        "private-provider-request"));
        OpenAiSpeakingPromptTtsAdapter unavailable =
                new OpenAiSpeakingPromptTtsAdapter(
                        properties,
                        mock(SpeakingPromptAudioVerifier.class),
                        mock(PracticeAiMetrics.class),
                        (request, config) ->
                                new OpenAiSpeakingPromptTtsAdapter.ProviderResponse(
                                        503,
                                        "private".getBytes(StandardCharsets.UTF_8),
                                        "text/plain",
                                        "private-provider-request"));

        assertProviderFailure(
                () -> rejected.transcribe(sttRequest()),
                SpeakingPromptAiContract.PublicErrorCategory.PROVIDER_REJECTED,
                false);
        assertProviderFailure(
                () -> unavailable.synthesize(ttsRequest()),
                SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                true);
    }

    @Test
    void sttEmptyAndMalformedProviderOutputsFailClosed() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        OpenAiSpeakingPromptSttAdapter empty =
                new OpenAiSpeakingPromptSttAdapter(
                        properties,
                        new ObjectMapper(),
                        mock(PracticeAiMetrics.class),
                        (request, config) ->
                                new OpenAiSpeakingPromptSttAdapter.ProviderResponse(
                                        200, "{}", "private-provider-request"));
        OpenAiSpeakingPromptSttAdapter malformed =
                new OpenAiSpeakingPromptSttAdapter(
                        properties,
                        new ObjectMapper(),
                        mock(PracticeAiMetrics.class),
                        (request, config) ->
                                new OpenAiSpeakingPromptSttAdapter.ProviderResponse(
                                        200, "{", "private-provider-request"));

        assertProviderFailure(
                () -> empty.transcribe(sttRequest()),
                SpeakingPromptAiContract.PublicErrorCategory.EMPTY_OUTPUT,
                false);
        assertProviderFailure(
                () -> malformed.transcribe(sttRequest()),
                SpeakingPromptAiContract.PublicErrorCategory.MALFORMED_OUTPUT,
                false);
    }

    @Test
    void ttsEmptyMalformedAndInvalidGeneratedMediaFailClosed() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        OpenAiSpeakingPromptTtsAdapter empty =
                new OpenAiSpeakingPromptTtsAdapter(
                        properties,
                        mock(SpeakingPromptAudioVerifier.class),
                        mock(PracticeAiMetrics.class),
                        (request, config) ->
                                new OpenAiSpeakingPromptTtsAdapter.ProviderResponse(
                                        200,
                                        new byte[0],
                                        "audio/mpeg",
                                        "private-provider-request"));
        OpenAiSpeakingPromptTtsAdapter malformed =
                new OpenAiSpeakingPromptTtsAdapter(
                        properties,
                        mock(SpeakingPromptAudioVerifier.class),
                        mock(PracticeAiMetrics.class),
                        (request, config) ->
                                new OpenAiSpeakingPromptTtsAdapter.ProviderResponse(
                                        200,
                                        "not-audio".getBytes(StandardCharsets.UTF_8),
                                        "text/plain",
                                        "private-provider-request"));
        SpeakingPromptAudioVerifier rejectingVerifier =
                mock(SpeakingPromptAudioVerifier.class);
        when(rejectingVerifier.verifyTtsOutput(
                any(byte[].class), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException(
                        "private corrupt generated media"));
        OpenAiSpeakingPromptTtsAdapter invalidMedia =
                new OpenAiSpeakingPromptTtsAdapter(
                        properties,
                        rejectingVerifier,
                        mock(PracticeAiMetrics.class),
                        (request, config) ->
                                new OpenAiSpeakingPromptTtsAdapter.ProviderResponse(
                                        200,
                                        "invalid-generated".getBytes(
                                                StandardCharsets.UTF_8),
                                        "audio/mpeg",
                                        "private-provider-request"));

        assertProviderFailure(
                () -> empty.synthesize(ttsRequest()),
                SpeakingPromptAiContract.PublicErrorCategory.EMPTY_OUTPUT,
                false);
        assertProviderFailure(
                () -> malformed.synthesize(ttsRequest()),
                SpeakingPromptAiContract.PublicErrorCategory.MALFORMED_OUTPUT,
                false);
        assertProviderFailure(
                () -> invalidMedia.synthesize(ttsRequest()),
                SpeakingPromptAiContract.PublicErrorCategory.MALFORMED_OUTPUT,
                false);
    }

    @Test
    void disabledAdapterFailsBeforeTransportCall() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        AtomicInteger calls = new AtomicInteger();
        OpenAiSpeakingPromptTtsAdapter adapter =
                new OpenAiSpeakingPromptTtsAdapter(
                        properties,
                        mock(SpeakingPromptAudioVerifier.class),
                        mock(PracticeAiMetrics.class),
                        (request, config) -> {
                            calls.incrementAndGet();
                            return new OpenAiSpeakingPromptTtsAdapter.ProviderResponse(
                                    200, new byte[] {1}, "audio/mpeg", null);
                        });

        assertThatThrownBy(() -> adapter.synthesize(ttsRequest()))
                .isInstanceOf(SpeakingPromptAiContract.ProviderFailure.class)
                .satisfies(failure -> assertThat(
                        ((SpeakingPromptAiContract.ProviderFailure) failure)
                                .publicCategory())
                        .isEqualTo(
                                SpeakingPromptAiContract.PublicErrorCategory
                                        .CONFIGURATION));
        assertThat(calls).hasValue(0);
    }

    private static void assertProviderFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable invocation,
            SpeakingPromptAiContract.PublicErrorCategory category,
            boolean retryable) {
        assertThatThrownBy(invocation)
                .isInstanceOf(SpeakingPromptAiContract.ProviderFailure.class)
                .satisfies(failure -> {
                    SpeakingPromptAiContract.ProviderFailure mapped =
                            (SpeakingPromptAiContract.ProviderFailure) failure;
                    assertThat(mapped.publicCategory()).isEqualTo(category);
                    assertThat(mapped.retryable()).isEqualTo(retryable);
                    assertThat(mapped.toString())
                            .doesNotContain("private-provider-request")
                            .doesNotContain("private provider")
                            .doesNotContain("private corrupt");
                });
    }

    private static SpeakingPromptAiContract.SttRequest sttRequest() {
        byte[] audio = "verified-original-audio".getBytes(StandardCharsets.UTF_8);
        return new SpeakingPromptAiContract.SttRequest(
                new SpeakingPromptAiContract.VerifiedAudio(
                        audio,
                        "original.mp3",
                        "audio/mpeg",
                        SpeakingPromptAiContract.exactBytesSha256(audio),
                        2_000L),
                "ko",
                SpeakingPromptAiContract.CONTRACT_VERSION);
    }

    private static SpeakingPromptAiContract.TtsRequest ttsRequest() {
        String prompt = "질문을 듣고 답하세요.";
        return new SpeakingPromptAiContract.TtsRequest(
                prompt,
                SpeakingPromptAiContract.unicodeNfcUtf8Sha256(prompt),
                "ko",
                "alloy",
                BigDecimal.ONE,
                "mp3",
                SpeakingPromptAiContract.CONTRACT_VERSION);
    }

    private static SpeakingPromptAuthoringAiProperties configuredProperties() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        properties.getStt().setEnabled(true);
        properties.getStt().setProvider("openai");
        properties.getStt().setBaseUrl("https://provider.invalid");
        properties.getStt().setApiKey("stt-secret");
        properties.getStt().setModel("transcribe-model");
        properties.getTts().setEnabled(true);
        properties.getTts().setProvider("openai");
        properties.getTts().setBaseUrl("https://provider.invalid");
        properties.getTts().setApiKey("tts-secret");
        properties.getTts().setModel("speech-model");
        properties.getTts().setVoice("alloy");
        return properties;
    }
}
