package com.ksh.features.practice.ai.speaking.transcription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiCapabilitySet;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionAuditService;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionSnapshot;
import com.ksh.features.practice.ai.controlplane.PracticeAiLimits;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderTransport;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiResolvedBinding;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiSpeakingTranscriptionControlPlaneTest {

    @Test
    void productionPathIgnoresLegacyCredentialAndRetriesOnlyResolvedSttBinding() {
        PracticeAiResolvedBinding binding = binding();
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits = mock(PracticeAiExecutionAuditService.class);
        AtomicInteger calls = new AtomicInteger();
        PracticeAiProviderTransport fakeTransport =
                (resolved, path, contentType, accept, body, headers) -> {
                    assertThat(resolved).isSameAs(binding);
                    assertThat(path).isEqualTo("/audio/transcriptions");
                    int call = calls.incrementAndGet();
                    return call == 1
                            ? new PracticeAiProviderTransport.ProviderResponse(
                                    503, new byte[0], "application/json", "fake-retry")
                            : new PracticeAiProviderTransport.ProviderResponse(
                                    200,
                                    "{\"text\":\"안녕하세요\"}"
                                            .getBytes(StandardCharsets.UTF_8),
                                    "application/json",
                                    "fake-success");
                };
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_SPEAKING_STT))
                .thenReturn(binding);
        when(resolver.availableSnapshot(PracticeAiPurpose.PRACTICE_SPEAKING_STT))
                .thenReturn(Optional.of(binding.snapshot()));
        doNothing().when(resolver).assertCurrent(binding.snapshot());
        when(audits.start(
                binding.snapshot(),
                "LEARNER_RESPONSE_STT",
                "media=9|attempt=10|question=11|version=12|mime=audio/webm|bytes=5|duration=1200",
                "LEARNER_RESPONSE_AUDIO")).thenReturn(99L);
        OpenAiSpeakingTranscriptionClient client =
                new OpenAiSpeakingTranscriptionClient(
                        legacyPropertiesWithoutCredential(),
                        new ObjectMapper(),
                        resolver,
                        audits,
                        fakeTransport);
        byte[] audio = "audio".getBytes(StandardCharsets.UTF_8);

        SpeakingTranscriptionResult result = client.transcribe(
                new SpeakingTranscriptionRequest(
                        9L, 10L, 11L, 12L, "audio/webm", 5L, 1200L, "ko",
                        () -> new ByteArrayInputStream(audio)));

        assertThat(result.status()).isEqualTo(SpeakingEvaluationStatus.EVALUATED);
        assertThat(result.provider()).isEqualTo("PRACTICE_STT");
        assertThat(result.model()).isEqualTo("db-stt-model");
        assertThat(calls).hasValue(2);
        assertThat(client.identity().bindingRevision()).isEqualTo(6L);
        verify(resolver, times(1)).resolve(
                PracticeAiPurpose.PRACTICE_SPEAKING_STT);
        verify(audits).success(99L);
    }

    private static SpeakingTranscriptionProperties legacyPropertiesWithoutCredential() {
        return new SpeakingTranscriptionProperties(
                false,
                "openai",
                "https://legacy.invalid/v1",
                "",
                "legacy-model-must-not-be-used",
                "ko",
                26_214_400L,
                Duration.ofSeconds(30),
                0,
                true,
                "audio/webm,audio/mp4");
    }

    private static PracticeAiResolvedBinding binding() {
        return new PracticeAiResolvedBinding(
                new PracticeAiExecutionSnapshot(
                        PracticeAiPurpose.PRACTICE_SPEAKING_STT,
                        6L,
                        4L,
                        PracticeAiBindingResolver.PROVIDER_FAMILY,
                        "PRACTICE_STT",
                        "db-stt-model",
                        PracticeAiBindingResolver.TRANSPORT_DIALECT,
                        new PracticeAiCapabilitySet(
                                false, false, false, true, false),
                        new PracticeAiLimits(
                                1_000, 5_000, 1, 1_048_576, 1_048_576),
                        "a".repeat(64),
                        "b".repeat(64),
                        "SPEAKING_AUDIO_STT_V1"),
                URI.create("https://provider.invalid/v1"),
                "FAKE_SECRET_NEVER_SENT");
    }
}
