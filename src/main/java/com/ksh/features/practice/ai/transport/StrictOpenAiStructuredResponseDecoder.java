package com.ksh.features.practice.ai.transport;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.Map;

@Component
public class StrictOpenAiStructuredResponseDecoder {

    private static final byte UTF8_BOM_1 = (byte) 0xef;
    private static final byte UTF8_BOM_2 = (byte) 0xbb;
    private static final byte UTF8_BOM_3 = (byte) 0xbf;

    private final ObjectMapper strictMapper;

    public StrictOpenAiStructuredResponseDecoder() {
        JsonFactory jsonFactory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.strictMapper = new ObjectMapper(jsonFactory);
    }

    public DecodedResponse decode(byte[] responseBytes, int maxResponseBytes) {
        if (responseBytes == null || responseBytes.length == 0) {
            throw contract("PROVIDER_EMPTY_RESPONSE");
        }
        if (responseBytes.length > maxResponseBytes) {
            throw contract("PROVIDER_RESPONSE_TOO_LARGE");
        }
        if (hasUtf8Bom(responseBytes)) {
            throw contract("PROVIDER_UTF8_BOM_REJECTED");
        }

        String envelopeText = strictUtf8(responseBytes);
        JsonNode envelope = parseSingleJson(
                envelopeText,
                "PROVIDER_MALFORMED_ENVELOPE");
        if (!envelope.isObject()) {
            throw contract("PROVIDER_INVALID_ENVELOPE");
        }

        JsonNode choices = envelope.get("choices");
        if (choices == null || !choices.isArray() || choices.size() != 1) {
            throw contract("PROVIDER_AMBIGUOUS_CHOICES");
        }
        JsonNode choice = choices.get(0);
        String finishReason = requiredText(
                choice,
                "finish_reason",
                "PROVIDER_MISSING_FINISH_REASON");
        if ("length".equals(finishReason)) {
            throw contract("PROVIDER_TRUNCATED_RESPONSE");
        }
        if (!"stop".equals(finishReason)) {
            throw contract("PROVIDER_UNSUPPORTED_FINISH_REASON");
        }

        JsonNode message = choice.get("message");
        if (message == null || !message.isObject()) {
            throw contract("PROVIDER_MISSING_MESSAGE");
        }
        JsonNode refusal = message.get("refusal");
        if (refusal != null && !refusal.isNull()
                && (!refusal.isTextual() || !refusal.textValue().isBlank())) {
            throw contract("PROVIDER_REFUSAL");
        }
        String content = requiredText(
                message,
                "content",
                "PROVIDER_EMPTY_RESPONSE");
        String trimmed = content.trim();
        if (trimmed.startsWith("```") || trimmed.endsWith("```")) {
            throw contract("PROVIDER_MARKDOWN_WRAPPER");
        }

        JsonNode output = parseSingleJson(
                content,
                "PROVIDER_MALFORMED_STRUCTURED_OUTPUT");
        if (!output.isObject()) {
            throw contract("PROVIDER_NON_OBJECT_STRUCTURED_OUTPUT");
        }
        requireNfc(output);

        String providerRequestId = envelope.path("id").isTextual()
                ? envelope.path("id").textValue()
                : "";
        return new DecodedResponse(
                output,
                finishReason,
                providerRequestId);
    }

    private JsonNode parseSingleJson(String json, String category) {
        try (JsonParser parser = strictMapper.getFactory().createParser(json)) {
            JsonNode result = strictMapper.readTree(parser);
            if (result == null || parser.nextToken() != null) {
                throw contract(category);
            }
            return result;
        } catch (PracticeAiContractException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PracticeAiContractException(
                    category,
                    false,
                    exception);
        }
    }

    private static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new PracticeAiContractException(
                    "PROVIDER_INVALID_UTF8",
                    false,
                    exception);
        }
    }

    private static String requiredText(
            JsonNode object,
            String field,
            String category) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()
                || value.textValue().isBlank()) {
            throw contract(category);
        }
        return value.textValue();
    }

    private static void requireNfc(JsonNode node) {
        if (node.isTextual()
                && !Normalizer.isNormalized(
                        node.textValue(),
                        Normalizer.Form.NFC)) {
            throw contract("PROVIDER_NON_NFC_OUTPUT");
        }
        if (node.isArray()) {
            node.forEach(StrictOpenAiStructuredResponseDecoder::requireNfc);
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!Normalizer.isNormalized(
                        field.getKey(),
                        Normalizer.Form.NFC)) {
                    throw contract("PROVIDER_NON_NFC_OUTPUT");
                }
                requireNfc(field.getValue());
            }
        }
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == UTF8_BOM_1
                && bytes[1] == UTF8_BOM_2
                && bytes[2] == UTF8_BOM_3;
    }

    private static PracticeAiContractException contract(String category) {
        return new PracticeAiContractException(category, false);
    }

    public record DecodedResponse(
            JsonNode output,
            String finishReason,
            String providerRequestId
    ) {
        public DecodedResponse {
            output = output.deepCopy();
        }

        @Override
        public JsonNode output() {
            return output.deepCopy();
        }
    }
}
