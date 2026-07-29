package com.ksh.features.practice.ai.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictOpenAiStructuredResponseDecoderTest {

    private final StrictOpenAiStructuredResponseDecoder decoder =
            new StrictOpenAiStructuredResponseDecoder();

    @Test
    void acceptsExactlyOneStoppedNfcStructuredObject() {
        StrictOpenAiStructuredResponseDecoder.DecodedResponse decoded =
                decoder.decode(envelope(
                        "stop",
                        "{\"answer\":\"좋아요\"}",
                        "null").getBytes(StandardCharsets.UTF_8), 32_768);

        assertThat(decoded.output().path("answer").asText())
                .isEqualTo("좋아요");
        assertThat(decoded.finishReason()).isEqualTo("stop");
        assertThat(decoded.providerRequestId()).isEqualTo("req-1");
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void rejectsAmbiguousOrUnverifiableProviderOutput(
            String response,
            String expectedCategory) {
        assertThatThrownBy(() -> decoder.decode(
                response.getBytes(StandardCharsets.UTF_8),
                32_768))
                .isInstanceOf(PracticeAiContractException.class)
                .extracting(exception ->
                        ((PracticeAiContractException) exception).category())
                .isEqualTo(expectedCategory);
    }

    @Test
    void invalidUtf8AndBomAreRejectedBeforeJsonParsing() {
        byte[] invalidUtf8 = new byte[]{
                '{', '"', 'x', '"', ':', '"', (byte) 0xc3, 0x28, '"', '}'
        };
        assertThatThrownBy(() -> decoder.decode(invalidUtf8, 32_768))
                .isInstanceOf(PracticeAiContractException.class)
                .extracting(exception ->
                        ((PracticeAiContractException) exception).category())
                .isEqualTo("PROVIDER_INVALID_UTF8");

        byte[] normal = envelope("stop", "{}", "null")
                .getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[normal.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(normal, 0, bom, 3, normal.length);
        assertThatThrownBy(() -> decoder.decode(bom, 32_768))
                .isInstanceOf(PracticeAiContractException.class)
                .extracting(exception ->
                        ((PracticeAiContractException) exception).category())
                .isEqualTo("PROVIDER_UTF8_BOM_REJECTED");
    }

    private static Stream<Arguments> invalidResponses() {
        return Stream.of(
                Arguments.of(
                        envelope("length", "{}", "null"),
                        "PROVIDER_TRUNCATED_RESPONSE"),
                Arguments.of(
                        envelope("content_filter", "{}", "null"),
                        "PROVIDER_UNSUPPORTED_FINISH_REASON"),
                Arguments.of(
                        envelope("stop", "```json\n{}\n```", "null"),
                        "PROVIDER_MARKDOWN_WRAPPER"),
                Arguments.of(
                        envelope("stop", "{} {}", "null"),
                        "PROVIDER_MALFORMED_STRUCTURED_OUTPUT"),
                Arguments.of(
                        envelope(
                                "stop",
                                "{\"a\":1,\"a\":2}",
                                "null"),
                        "PROVIDER_MALFORMED_STRUCTURED_OUTPUT"),
                Arguments.of(
                        envelope("stop", "{}", "\"blocked\""),
                        "PROVIDER_REFUSAL"),
                Arguments.of(
                        envelope("stop", "{\"text\":\"가\"}", "null"),
                        "PROVIDER_NON_NFC_OUTPUT"),
                Arguments.of(
                        "{\"choices\":[],\"id\":\"req-1\"}",
                        "PROVIDER_AMBIGUOUS_CHOICES"),
                Arguments.of(
                        "{\"choices\":[{\"finish_reason\":\"stop\","
                                + "\"message\":{\"content\":\"{}\"}}],"
                                + "\"choices\":[],\"id\":\"req-1\"}",
                        "PROVIDER_MALFORMED_ENVELOPE"));
    }

    private static String envelope(
            String finishReason,
            String content,
            String refusalJson) {
        return "{\"id\":\"req-1\",\"choices\":[{"
                + "\"finish_reason\":\"" + finishReason + "\","
                + "\"message\":{\"content\":" + jsonString(content) + ","
                + "\"refusal\":" + refusalJson + "}}]}";
    }

    private static String jsonString(String value) {
        return "\""
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                + "\"";
    }
}
