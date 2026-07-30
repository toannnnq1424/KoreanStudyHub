package com.ksh.features.practice.ai.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiPrimaryStructuredGenerationAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalPracticeJson canonicalJson =
            new CanonicalPracticeJson(objectMapper);
    private final StrictOpenAiStructuredResponseDecoder decoder =
            new StrictOpenAiStructuredResponseDecoder();

    @Test
    void adapterSerializesTypedAuthorityOnlyAtWireBoundaryAndKeepsStableIdentity()
            throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedKey = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        OpenAiPrimaryStructuredGenerationAdapter.OpenAiChatTransport transport =
                (body, key) -> {
                    calls.incrementAndGet();
                    capturedBody.set(body);
                    capturedKey.set(key);
                    return validEnvelope().getBytes(StandardCharsets.UTF_8);
                };
        OpenAiPrimaryStructuredGenerationAdapter adapter =
                new OpenAiPrimaryStructuredGenerationAdapter(
                        OpenAiPrimaryCapabilityPropertiesTest.properties(
                                true,
                                "secret",
                                "assessment-model",
                                "",
                                "",
                                "",
                                ""),
                        canonicalJson,
                        decoder,
                        transport);

        PracticeStructuredGenerationResponse response =
                adapter.generate(request());

        assertThat(calls).hasValue(1);
        assertThat(capturedKey.get())
                .startsWith("ksh-practice-")
                .hasSize(77);
        assertThat(response.output().path("answer").asText())
                .isEqualTo("가");
        assertThat(response.provider()).isEqualTo("openai-primary");
        assertThat(response.model()).isEqualTo("assessment-model");

        JsonNode wire = objectMapper.readTree(capturedBody.get());
        assertThat(wire.path("response_format").path("type").asText())
                .isEqualTo("json_schema");
        assertThat(wire.path("response_format")
                .path("json_schema")
                .path("strict")
                .asBoolean()).isTrue();
        String typedInputText = wire.path("messages")
                .path(1)
                .path("content")
                .path(0)
                .path("text")
                .asText();
        JsonNode typedInput = objectMapper.readTree(typedInputText);
        assertThat(typedInput.path("schemaVersion").asText())
                .isEqualTo("schema-v1");
        assertThat(typedInput.path("promptVersion").asText())
                .isEqualTo("prompt-v1");
        assertThat(typedInput.path("strategyCode").asText())
                .isEqualTo("TYPE_NATIVE");
        assertThat(typedInput.path("modelCapabilityProfile")
                .path("nativeJsonSchema")
                .asBoolean()).isTrue();
        assertThat(typedInput.path("input").path("text").asText())
                .isEqualTo("가");
    }

    @Test
    void disabledAdapterAndUnsupportedFallbacksNeverCallTransport() {
        OpenAiPrimaryCapabilityProperties disabledProperties =
                OpenAiPrimaryCapabilityPropertiesTest.properties(
                        false,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "");
        DisabledPracticeStructuredGenerationAdapter disabled =
                new DisabledPracticeStructuredGenerationAdapter(
                        disabledProperties);

        assertThat(disabled.identity(
                PracticeAiCapability.ASSESSMENT_TEXT_VISION).available())
                .isFalse();
        assertThatThrownBy(() -> disabled.generate(request()))
                .isInstanceOf(PracticeAiContractException.class)
                .extracting(exception ->
                        ((PracticeAiContractException) exception).category())
                .isEqualTo("PROVIDER_DISABLED");
    }

    private PracticeStructuredGenerationRequest request() {
        return new PracticeStructuredGenerationRequest(
                "fixture",
                PracticeAiCapability.ASSESSMENT_TEXT_VISION,
                new PracticeAiAuthoritySnapshot(
                        "schema-v1",
                        "prompt-v1",
                        "TYPE_NATIVE",
                        "strategy-v1",
                        "question-version-17"),
                PracticeModelCapabilityProfile.openAiAssessmentV1(),
                "Return structured JSON.",
                "",
                Map.of("text", "가"),
                "fixture_response",
                Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("answer"),
                        "properties", Map.of(
                                "answer", Map.of("type", "string"))),
                List.of(),
                256,
                "");
    }

    private static String validEnvelope() {
        return """
                {
                  "id": "req-fixture",
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "content": "{\\"answer\\":\\"가\\"}",
                        "refusal": null
                      }
                    }
                  ]
                }
                """;
    }
}
