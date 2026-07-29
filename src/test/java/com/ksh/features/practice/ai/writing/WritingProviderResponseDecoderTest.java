package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WritingProviderResponseDecoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingProviderResponseDecoder decoder =
            new WritingProviderResponseDecoder(objectMapper);

    @Test
    void acceptsRawValidJsonObject() throws Exception {
        JsonNode decoded = decoder.decode(chatEnvelope(
                "{\"summary\":\"ok\",\"nested\":{\"value\":1}}",
                "stop"));

        assertThat(decoded.path("summary").asText()).isEqualTo("ok");
        assertThat(decoded.path("nested").path("value").asInt())
                .isEqualTo(1);
    }

    @Test
    void acceptsOneMarkdownJsonOrCodeFence() throws Exception {
        JsonNode jsonFence = decoder.decode(chatEnvelope(
                "```json\n{\"summary\":\"json\"}\n```",
                "stop"));
        JsonNode codeFence = decoder.decode(chatEnvelope(
                "```code\n{\"summary\":\"code\"}\n```",
                "stop"));

        assertThat(jsonFence.path("summary").asText())
                .isEqualTo("json");
        assertThat(codeFence.path("summary").asText())
                .isEqualTo("code");
    }

    @Test
    void acceptsPrefaceAndSuffixAroundOneBalancedObject() throws Exception {
        JsonNode decoded = decoder.decode(chatEnvelope(
                "Evaluation follows:\n{\"summary\":\"ok\"}\nEnd.",
                "stop"));

        assertThat(decoded.path("summary").asText()).isEqualTo("ok");
    }

    @Test
    void balancedExtractionHandlesNestedArraysBracesAndEscapedQuotes()
            throws Exception {
        String content = """
                Preface
                {
                  "items":[
                    {"note":"literal braces { and } plus an escaped quote: \\"ok\\""},
                    {"nested":{"values":[1,{"name":"deep"}]}}
                  ]
                }
                Suffix
                """;

        JsonNode decoded = decoder.decode(chatEnvelope(content, "stop"));

        assertThat(decoded.path("items").get(0).path("note").asText())
                .contains("{ and }", "\"ok\"");
        assertThat(decoded.path("items").get(1)
                .path("nested").path("values").get(1)
                .path("name").asText()).isEqualTo("deep");
    }

    @Test
    void rejectsAmbiguousTwoObjectOutput() throws Exception {
        assertReason(
                chatEnvelope(
                        "First {\"value\":1} second {\"value\":2}",
                        "stop"),
                "PROVIDER_MALFORMED_JSON");
    }

    @Test
    void mapsLengthFinishedUnbalancedOutputToTruncation()
            throws Exception {
        String envelope = objectMapper.writeValueAsString(Map.of(
                "id", "request-safe-123",
                "choices", List.of(Map.of(
                        "finish_reason", "length",
                        "message", Map.of(
                                "content", "{\"summary\":\"cut")))));

        assertThatThrownBy(() -> decoder.decode(envelope))
                .isInstanceOfSatisfying(
                        WritingProviderResponseDecoder
                                .DecodingException.class,
                        exception -> {
                            assertThat(exception.reason())
                                    .isEqualTo(
                                            "PROVIDER_OUTPUT_TRUNCATED");
                            assertThat(exception.finishReason())
                                    .isEqualTo("length");
                            assertThat(exception.contentLength())
                                    .isPositive();
                            assertThat(exception.requestId())
                                    .isEqualTo("request-safe-123");
                        });
    }

    @Test
    void mapsResponsesApiIncompleteMaxOutputTokensToTruncation()
            throws Exception {
        String envelope = objectMapper.writeValueAsString(Map.of(
                "status", "incomplete",
                "incomplete_details", Map.of(
                        "reason", "max_output_tokens"),
                "output_text", "{\"summary\":\"cut"));

        assertReason(envelope, "PROVIDER_OUTPUT_TRUNCATED");
    }

    @Test
    void rejectsMalformedNonTruncatedOutput() throws Exception {
        assertReason(
                chatEnvelope("{\"summary\":\"cut", "stop"),
                "PROVIDER_MALFORMED_JSON");
    }

    @Test
    void supportsChatAndResponsesContentParts() throws Exception {
        String chatParts = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "finish_reason", "stop",
                        "message", Map.of(
                                "content", List.of(
                                        Map.of(
                                                "type", "text",
                                                "text", "{\"summary\":"),
                                        Map.of(
                                                "type", "output_text",
                                                "text", "\"chat\"}")))))));
        String responseParts = objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "text", "{\"summary\":\"response\"}"))))));

        assertThat(decoder.decode(chatParts).path("summary").asText())
                .isEqualTo("chat");
        assertThat(decoder.decode(responseParts).path("summary").asText())
                .isEqualTo("response");
    }

    @Test
    void rejectsNullEmptyAndUnknownContentEnvelopes() throws Exception {
        String nullContent = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "content",
                                objectMapper.nullNode())))));
        String emptyParts = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "content", List.of())))));
        String unknownPart = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "content", List.of(Map.of(
                                        "type", "refusal",
                                        "text", "no")))))));

        assertReason(nullContent, "PROVIDER_EMPTY_RESPONSE");
        assertReason(emptyParts, "PROVIDER_EMPTY_RESPONSE");
        assertReason(
                unknownPart,
                "PROVIDER_UNSUPPORTED_CONTENT_ENVELOPE");
    }

    private String chatEnvelope(String content, String finishReason)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "finish_reason", finishReason,
                        "message", Map.of("content", content)))));
    }

    private void assertReason(String envelope, String reason) {
        assertThatThrownBy(() -> decoder.decode(envelope))
                .isInstanceOfSatisfying(
                        WritingProviderResponseDecoder
                                .DecodingException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(reason));
    }
}
