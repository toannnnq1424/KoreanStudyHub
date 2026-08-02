package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.metrics.PracticeAiMetrics;
import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiCapabilitySet;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionAuditService;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionSnapshot;
import com.ksh.features.practice.ai.controlplane.PracticeAiLimits;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderTransport;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiResolvedBinding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingPromptProviderAdapterTest {

    @Test
    void springUsesTheProductionConstructorForBothProviderAdapters()
            throws NoSuchMethodException {
        assertThat(OpenAiSpeakingPromptSttAdapter.class.getConstructor(
                SpeakingPromptAuthoringAiProperties.class,
                ObjectMapper.class,
                PracticeAiMetrics.class,
                PracticeAiBindingResolver.class,
                PracticeAiExecutionAuditService.class,
                PracticeAiProviderTransport.class).isAnnotationPresent(Autowired.class))
                .isTrue();
        assertThat(OpenAiSpeakingPromptTtsAdapter.class.getConstructor(
                SpeakingPromptAuthoringAiProperties.class,
                SpeakingPromptAudioVerifier.class,
                PracticeAiMetrics.class,
                PracticeAiBindingResolver.class,
                PracticeAiExecutionAuditService.class,
                PracticeAiProviderTransport.class).isAnnotationPresent(Autowired.class))
                .isTrue();
    }

    @Test
    void productionSttUsesOnlyResolvedPracticeBindingAndCentralTransport() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_SPEAKING_STT);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits =
                mock(PracticeAiExecutionAuditService.class);
        properties.setBindingResolver(resolver);
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_SPEAKING_STT))
                .thenReturn(binding);
        doNothing().when(resolver).assertCurrent(binding.snapshot());
        when(audits.start(
                any(), anyString(), anyString(), anyString())).thenReturn(41L);
        AtomicInteger calls = new AtomicInteger();
        PracticeAiProviderTransport fakeTransport =
                (resolved, path, contentType, accept, body, headers) -> {
                    calls.incrementAndGet();
                    assertThat(resolved).isSameAs(binding);
                    assertThat(path).isEqualTo("/audio/transcriptions");
                    assertThat(String.valueOf(body))
                            .contains("db-purpose-model")
                            .doesNotContain("stt-secret", "legacy-model");
                    return new PracticeAiProviderTransport.ProviderResponse(
                            200,
                            "{\"text\":\"전사 결과\",\"confidence\":0.91}"
                                    .getBytes(StandardCharsets.UTF_8),
                            "application/json",
                            "fake-stt-request");
                };
        OpenAiSpeakingPromptSttAdapter adapter =
                new OpenAiSpeakingPromptSttAdapter(
                        properties,
                        new ObjectMapper(),
                        mock(PracticeAiMetrics.class),
                        resolver,
                        audits,
                        fakeTransport);

        SpeakingPromptAiContract.SttResult result =
                adapter.transcribe(sttRequest());

        assertThat(result.providerCode()).isEqualTo("openai");
        assertThat(result.modelCode()).isEqualTo("db-purpose-model");
        assertThat(result.purposeCode()).isEqualTo(
                PracticeAiPurpose.PRACTICE_SPEAKING_STT.name());
        assertThat(calls).hasValue(1);
        verify(resolver, times(2)).resolve(
                PracticeAiPurpose.PRACTICE_SPEAKING_STT);
        verify(resolver).assertCurrent(binding.snapshot());
        verify(audits).success(41L);
    }

    @Test
    void productionTtsUsesOnlyResolvedPracticeBindingAndCentralTransport() {
        SpeakingPromptAuthoringAiProperties properties = configuredProperties();
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_SPEAKING_TTS);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits =
                mock(PracticeAiExecutionAuditService.class);
        properties.setBindingResolver(resolver);
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_SPEAKING_TTS))
                .thenReturn(binding);
        doNothing().when(resolver).assertCurrent(binding.snapshot());
        when(audits.start(
                any(), anyString(), anyString(), anyString())).thenReturn(42L);
        byte[] generated = "verified-generated-audio".getBytes(
                StandardCharsets.UTF_8);
        SpeakingPromptAudioVerifier verifier = mock(
                SpeakingPromptAudioVerifier.class);
        when(verifier.verifyTtsOutput(
                any(byte[].class), anyString(), anyString()))
                .thenReturn(new SpeakingPromptAiContract.VerifiedAudio(
                        generated,
                        "speaking-prompt-ai.mp3",
                        "audio/mpeg",
                        SpeakingPromptAiContract.exactBytesSha256(generated),
                        2_000L));
        AtomicInteger calls = new AtomicInteger();
        PracticeAiProviderTransport fakeTransport =
                (resolved, path, contentType, accept, body, headers) -> {
                    calls.incrementAndGet();
                    assertThat(resolved).isSameAs(binding);
                    assertThat(path).isEqualTo("/audio/speech");
                    assertThat(String.valueOf(body))
                            .contains("db-purpose-model")
                            .doesNotContain("tts-secret", "legacy-model");
                    return new PracticeAiProviderTransport.ProviderResponse(
                            200,
                            generated,
                            "audio/mpeg",
                            "fake-tts-request");
                };
        OpenAiSpeakingPromptTtsAdapter adapter =
                new OpenAiSpeakingPromptTtsAdapter(
                        properties,
                        verifier,
                        mock(PracticeAiMetrics.class),
                        resolver,
                        audits,
                        fakeTransport);

        SpeakingPromptAiContract.TtsResult result =
                adapter.synthesize(ttsRequest());

        assertThat(result.providerCode()).isEqualTo("openai");
        assertThat(result.modelCode()).isEqualTo("db-purpose-model");
        assertThat(result.purposeCode()).isEqualTo(
                PracticeAiPurpose.PRACTICE_SPEAKING_TTS.name());
        assertThat(calls).hasValue(1);
        verify(resolver, times(2)).resolve(
                PracticeAiPurpose.PRACTICE_SPEAKING_TTS);
        verify(resolver).assertCurrent(binding.snapshot());
        verify(audits).success(42L);
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

    private static PracticeAiResolvedBinding binding(PracticeAiPurpose purpose) {
        PracticeAiCapabilitySet capabilities = switch (purpose) {
            case PRACTICE_SPEAKING_STT ->
                    new PracticeAiCapabilitySet(false, false, false, true, false);
            case PRACTICE_SPEAKING_TTS ->
                    new PracticeAiCapabilitySet(false, false, false, false, true);
            default -> throw new IllegalArgumentException("Unsupported test purpose");
        };
        return new PracticeAiResolvedBinding(
                new PracticeAiExecutionSnapshot(
                        purpose,
                        7L,
                        3L,
                        PracticeAiBindingResolver.PROVIDER_FAMILY,
                        "PRACTICE_AUDIO",
                        "db-purpose-model",
                        PracticeAiBindingResolver.TRANSPORT_DIALECT,
                        capabilities,
                        new PracticeAiLimits(
                                1_000, 5_000, 0, 1_048_576, 1_048_576),
                        "a".repeat(64),
                        "b".repeat(64),
                        "PRACTICE_AUDIO_V1"),
                URI.create("https://provider.invalid/v1"),
                "FAKE_CONTROL_PLANE_SECRET");
    }
}
