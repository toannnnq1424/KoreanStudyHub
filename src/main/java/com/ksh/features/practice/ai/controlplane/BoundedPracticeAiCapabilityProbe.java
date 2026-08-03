package com.ksh.features.practice.ai.controlplane;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.transport.StrictOpenAiStructuredResponseDecoder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BoundedPracticeAiCapabilityProbe implements PracticeAiCapabilityProbe {

    private static final String ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    private final PracticeAiProviderTransport transport;
    private final ObjectMapper objectMapper;
    private final StrictOpenAiStructuredResponseDecoder decoder;

    public BoundedPracticeAiCapabilityProbe(
            PracticeAiProviderTransport transport,
            ObjectMapper objectMapper,
            StrictOpenAiStructuredResponseDecoder decoder) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.decoder = decoder;
    }

    @Override
    public void probe(PracticeAiResolvedBinding binding) {
        switch (binding.snapshot().purpose()) {
            case PRACTICE_PDF_AUTHORING,
                 PRACTICE_RL_EXPLANATION,
                 PRACTICE_WRITING_EVALUATION,
                 PRACTICE_SPEAKING_EVALUATION -> probeStructured(binding);
            case PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION ->
                    throw new PracticeAiControlPlaneException(
                            "DIRECT_AUDIO_DARK_ROLLOUT_REQUIRED", false);
            case PRACTICE_SPEAKING_STT -> probeStt(binding);
            case PRACTICE_SPEAKING_TTS -> probeTts(binding);
        }
    }

    private void probeStructured(PracticeAiResolvedBinding binding) {
        PracticeAiPurpose purpose = binding.snapshot().purpose();
        List<Map<String, Object>> userContent = new ArrayList<>();
        userContent.add(Map.of(
                "type", "text",
                "text", fixtureText(purpose)));
        if (binding.snapshot().capabilities().imageInput()) {
            userContent.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of(
                            "url", "data:image/png;base64," + ONE_PIXEL_PNG,
                            "detail", "low")));
        }
        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("purpose", "ok"),
                "properties", Map.of(
                        "purpose", Map.of("type", "string", "const", purpose.name()),
                        "ok", Map.of("type", "boolean", "const", true)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", binding.snapshot().model());
        body.put("temperature", 0.0);
        body.put("max_tokens", 64);
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "Return the exact capability-test JSON. Do not include prose."),
                Map.of("role", "user", "content", userContent)));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "practice_capability_test_v1",
                        "strict", true,
                        "schema", schema)));
        PracticeAiProviderTransport.ProviderResponse response = exchange(
                binding,
                "/chat/completions",
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_JSON,
                write(body));
        JsonNode output = decoder.decode(
                response.body(), binding.snapshot().limits().maxResponseBytes()).output();
        if (!purpose.name().equals(output.path("purpose").asText())
                || !output.path("ok").asBoolean(false)) {
            throw new PracticeAiControlPlaneException(
                    "CAPABILITY_FIXTURE_CONTRACT_FAILED", false);
        }
    }

    private void probeStt(PracticeAiResolvedBinding binding) {
        byte[] wav = silentWav();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", binding.snapshot().model());
        body.add("language", "ko");
        body.add("response_format", "json");
        body.add("file", new ByteArrayResource(wav) {
            @Override
            public String getFilename() {
                return "ksh-practice-capability-test.wav";
            }
        });
        PracticeAiProviderTransport.ProviderResponse response = exchange(
                binding,
                "/audio/transcriptions",
                MediaType.MULTIPART_FORM_DATA,
                MediaType.APPLICATION_JSON,
                body);
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("text").isTextual()) {
                throw new PracticeAiControlPlaneException(
                        "CAPABILITY_FIXTURE_CONTRACT_FAILED", false);
            }
        } catch (PracticeAiControlPlaneException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PracticeAiControlPlaneException(
                    "CAPABILITY_FIXTURE_CONTRACT_FAILED", false, exception);
        }
    }

    private void probeTts(PracticeAiResolvedBinding binding) {
        Map<String, Object> body = Map.of(
                "model", binding.snapshot().model(),
                "input", "안녕하세요.",
                "voice", "alloy",
                "response_format", "mp3");
        PracticeAiProviderTransport.ProviderResponse response = exchange(
                binding,
                "/audio/speech",
                MediaType.APPLICATION_JSON,
                MediaType.valueOf("audio/mpeg"),
                write(body));
        if (response.body().length == 0
                || !response.contentType().toLowerCase().startsWith("audio/")) {
            throw new PracticeAiControlPlaneException(
                    "CAPABILITY_FIXTURE_CONTRACT_FAILED", false);
        }
    }

    private PracticeAiProviderTransport.ProviderResponse exchange(
            PracticeAiResolvedBinding binding,
            String path,
            MediaType contentType,
            MediaType accept,
            Object body) {
        PracticeAiProviderTransport.ProviderResponse last = null;
        for (int attempt = 0;
                attempt <= binding.snapshot().limits().maxRetries();
                attempt++) {
            last = transport.exchange(binding, path, contentType, accept, body, Map.of());
            if (last.successful()) {
                return last;
            }
            if (!retryable(last.status())
                    || attempt >= binding.snapshot().limits().maxRetries()) {
                break;
            }
        }
        int status = last == null ? 0 : last.status();
        throw new PracticeAiControlPlaneException(
                "PROVIDER_HTTP_ERROR", retryable(status));
    }

    private String write(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.getBytes(StandardCharsets.UTF_8).length > 65_536) {
                throw new PracticeAiControlPlaneException(
                        "CAPABILITY_FIXTURE_TOO_LARGE", false);
            }
            return json;
        } catch (PracticeAiControlPlaneException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PracticeAiControlPlaneException(
                    "CAPABILITY_FIXTURE_SERIALIZATION_FAILED", false, exception);
        }
    }

    private static String fixtureText(PracticeAiPurpose purpose) {
        return "purpose=" + purpose.name()
                + "; fixture=KSH_PROJECT_OWNED_BOUNDED_V1; return ok=true.";
    }

    private static byte[] silentWav() {
        int samples = 800;
        int dataBytes = samples * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataBytes);
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(8_000);
        buffer.putInt(16_000);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataBytes);
        return buffer.array();
    }

    private static boolean retryable(int status) {
        return status == 429 || status == 500 || status == 502
                || status == 503 || status == 504;
    }
}
