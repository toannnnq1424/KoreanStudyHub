package com.ksh.features.practice.ai.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.transport.StrictOpenAiStructuredResponseDecoder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedPracticeAiCapabilityProbeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PracticeAiControlPlaneCodec codec =
            new PracticeAiControlPlaneCodec(objectMapper);

    @Test
    void existingPurposesUseBoundedTransportAndDirectAudioStopsBeforeTransport() {
        List<String> paths = new ArrayList<>();
        List<String> structuredBodies = new ArrayList<>();
        PracticeAiProviderTransport fakeTransport =
                (binding, path, contentType, accept, body, headers) -> {
                    paths.add(path);
                    if ("/chat/completions".equals(path)) {
                        structuredBodies.add(String.valueOf(body));
                        String content = json(Map.of(
                                "purpose", binding.snapshot().purpose().name(),
                                "ok", true));
                        byte[] envelope = bytes(Map.of(
                                "id", "fake-capability-request",
                                "choices", List.of(Map.of(
                                        "finish_reason", "stop",
                                        "message", Map.of("content", content)))));
                        return new PracticeAiProviderTransport.ProviderResponse(
                                200, envelope, "application/json", "fake-request");
                    }
                    if ("/audio/transcriptions".equals(path)) {
                        return new PracticeAiProviderTransport.ProviderResponse(
                                200,
                                "{\"text\":\"테스트\"}".getBytes(StandardCharsets.UTF_8),
                                "application/json",
                                "fake-request");
                    }
                    return new PracticeAiProviderTransport.ProviderResponse(
                            200,
                            new byte[]{0x49, 0x44, 0x33},
                            "audio/mpeg",
                            "fake-request");
                };
        BoundedPracticeAiCapabilityProbe probe =
                new BoundedPracticeAiCapabilityProbe(
                        fakeTransport,
                        objectMapper,
                        new StrictOpenAiStructuredResponseDecoder());

        for (PracticeAiPurpose purpose : PracticeAiPurpose.values()) {
            if (purpose == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION) {
                org.assertj.core.api.Assertions.assertThatThrownBy(
                                () -> probe.probe(binding(purpose)))
                        .isInstanceOf(PracticeAiControlPlaneException.class)
                        .extracting(error -> ((PracticeAiControlPlaneException) error)
                                .errorCode())
                        .isEqualTo("DIRECT_AUDIO_DARK_ROLLOUT_REQUIRED");
            } else {
                probe.probe(binding(purpose));
            }
        }

        assertThat(paths).containsExactly(
                "/chat/completions",
                "/chat/completions",
                "/chat/completions",
                "/chat/completions",
                "/audio/transcriptions",
                "/audio/speech");
        assertThat(structuredBodies).hasSize(4);
        assertThat(structuredBodies.get(0)).doesNotContain("data:image/png;base64");
        assertThat(structuredBodies.subList(1, 4))
                .allSatisfy(body -> assertThat(body)
                        .contains("data:image/png;base64"));
    }

    private PracticeAiResolvedBinding binding(PracticeAiPurpose purpose) {
        String capabilityJson = codec.capabilityJson(
                purpose, false,
                purpose == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION);
        String limitsJson = codec.limitsJson(
                1_000, 5_000, 1, 1_048_576, 1_048_576);
        return new PracticeAiResolvedBinding(
                new PracticeAiExecutionSnapshot(
                        purpose,
                        4L,
                        2L,
                        PracticeAiBindingResolver.PROVIDER_FAMILY,
                        "FAKE_PROFILE",
                        "fake-model",
                        PracticeAiBindingResolver.TRANSPORT_DIALECT,
                        codec.parseCapabilities(purpose, capabilityJson),
                        codec.parseLimits(limitsJson),
                        codec.digest(capabilityJson),
                        codec.digest(limitsJson),
                        "CAPABILITY_TEST_V1"),
                URI.create("https://provider.invalid/v1"),
                "FAKE_SECRET_NEVER_SENT");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] bytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
