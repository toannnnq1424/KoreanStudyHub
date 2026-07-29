package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic decoder for the OpenAI-compatible response envelopes used by
 * Writing evaluation. It extracts evidence only; task schema validation remains
 * owned by {@link WritingEvaluationNormalizer}.
 */
final class WritingProviderResponseDecoder {

    private static final Set<String> TRUNCATION_REASONS = Set.of(
            "length",
            "max_tokens",
            "max_output_tokens");
    private static final Set<String> TEXT_PART_TYPES = Set.of(
            "text",
            "output_text");
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final ObjectMapper objectMapper;

    WritingProviderResponseDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode decode(String rawEnvelope) {
        JsonNode envelope = parseEnvelope(rawEnvelope);
        String finishReason = finishReason(envelope);
        String requestId = requestId(envelope);

        String content;
        try {
            content = extractContent(envelope);
        } catch (DecodingException exception) {
            if (isTruncated(envelope, finishReason)) {
                throw failure(
                        "PROVIDER_OUTPUT_TRUNCATED",
                        finishReason,
                        exception.contentLength(),
                        requestId,
                        exception);
            }
            throw failure(
                    exception.reason(),
                    finishReason,
                    exception.contentLength(),
                    requestId,
                    exception);
        }

        int contentLength = content == null ? 0 : content.length();
        if (isTruncated(envelope, finishReason)) {
            throw failure(
                    "PROVIDER_OUTPUT_TRUNCATED",
                    finishReason,
                    contentLength,
                    requestId,
                    null);
        }
        if (content == null || content.isBlank()) {
            throw failure(
                    "PROVIDER_EMPTY_RESPONSE",
                    finishReason,
                    contentLength,
                    requestId,
                    null);
        }

        try {
            return decodeSingleObject(content);
        } catch (DecodingException exception) {
            throw failure(
                    exception.reason(),
                    finishReason,
                    contentLength,
                    requestId,
                    exception);
        }
    }

    private JsonNode parseEnvelope(String rawEnvelope) {
        if (rawEnvelope == null || rawEnvelope.isBlank()) {
            throw failure(
                    "PROVIDER_EMPTY_RESPONSE",
                    "not-provided",
                    0,
                    "not-available",
                    null);
        }
        JsonNode envelope = tryParseSingleValue(rawEnvelope.trim());
        if (envelope == null || !envelope.isObject()) {
            throw failure(
                    "PROVIDER_MALFORMED_JSON",
                    "not-provided",
                    -1,
                    "not-available",
                    null);
        }
        return envelope;
    }

    private JsonNode decodeSingleObject(String content) {
        String trimmed = content.trim();
        JsonNode direct = tryParseSingleValue(trimmed);
        if (direct != null) {
            if (direct.isObject()) {
                return direct;
            }
            throw malformed();
        }

        JsonNode fenced = tryParseSingleFence(trimmed);
        if (fenced != null) {
            return fenced;
        }

        List<JsonNode> candidates = balancedObjectCandidates(trimmed);
        if (candidates.size() != 1) {
            throw malformed();
        }
        return candidates.get(0);
    }

    private JsonNode tryParseSingleFence(String text) {
        if (!text.startsWith("```") || !text.endsWith("```")) {
            return null;
        }
        int firstLineEnd = text.indexOf('\n');
        if (firstLineEnd < 0 || text.indexOf("```", 3) != text.length() - 3) {
            return null;
        }
        String language = text.substring(3, firstLineEnd).trim();
        if (!language.isEmpty()
                && !"json".equalsIgnoreCase(language)
                && !"code".equalsIgnoreCase(language)) {
            return null;
        }
        String body = text.substring(firstLineEnd + 1, text.length() - 3)
                .trim();
        JsonNode candidate = tryParseSingleValue(body);
        return candidate != null && candidate.isObject()
                ? candidate
                : null;
    }

    private List<JsonNode> balancedObjectCandidates(String text) {
        List<JsonNode> candidates = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int start = text.indexOf('{', cursor);
            if (start < 0) {
                break;
            }
            int end = balancedObjectEnd(text, start);
            if (end < 0) {
                throw malformed();
            }
            JsonNode candidate = tryParseSingleValue(
                    text.substring(start, end + 1));
            if (candidate != null && candidate.isObject()) {
                candidates.add(candidate);
                if (candidates.size() > 1) {
                    throw malformed();
                }
            }
            cursor = end + 1;
        }
        return candidates;
    }

    private static int balancedObjectEnd(String text, int start) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '{' || current == '[') {
                stack.push(current);
                continue;
            }
            if (current == '}' || current == ']') {
                if (stack.isEmpty()
                        || !matchingPair(stack.pop(), current)) {
                    return -1;
                }
                if (stack.isEmpty()) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static boolean matchingPair(char open, char close) {
        return (open == '{' && close == '}')
                || (open == '[' && close == ']');
    }

    private JsonNode tryParseSingleValue(String text) {
        try (JsonParser parser =
                     objectMapper.getFactory().createParser(text)) {
            JsonNode value = objectMapper.readTree(parser);
            if (value == null || parser.nextToken() != null) {
                return null;
            }
            return value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractContent(JsonNode envelope) {
        JsonNode choices = envelope.get("choices");
        if (choices != null) {
            if (!choices.isArray() || choices.isEmpty()
                    || !choices.get(0).isObject()) {
                throw unsupportedEnvelope();
            }
            JsonNode message = choices.get(0).get("message");
            if (message == null || !message.isObject()
                    || !message.has("content")) {
                throw unsupportedEnvelope();
            }
            return textContent(message.get("content"));
        }

        if (envelope.has("output_text")) {
            JsonNode outputText = envelope.get("output_text");
            if (outputText == null || outputText.isNull()) {
                return "";
            }
            if (!outputText.isTextual()) {
                throw unsupportedEnvelope();
            }
            return outputText.asText();
        }

        JsonNode output = envelope.get("output");
        if (output != null) {
            if (!output.isArray()) {
                throw unsupportedEnvelope();
            }
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : output) {
                if (item == null || !item.isObject()
                        || !item.has("content")) {
                    throw unsupportedEnvelope();
                }
                builder.append(textContent(item.get("content")));
            }
            return builder.toString();
        }

        throw unsupportedEnvelope();
    }

    private String textContent(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            throw unsupportedEnvelope();
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode part : content) {
            if (part == null || !part.isObject()) {
                throw unsupportedEnvelope();
            }
            String type = part.path("type").asText("").trim()
                    .toLowerCase(Locale.ROOT);
            if (!type.isEmpty() && !TEXT_PART_TYPES.contains(type)) {
                throw unsupportedEnvelope();
            }
            JsonNode text = part.get("text");
            if (text == null || !text.isTextual()) {
                throw unsupportedEnvelope();
            }
            builder.append(text.asText());
        }
        return builder.toString();
    }

    private static boolean isTruncated(
            JsonNode envelope,
            String finishReason) {
        if (TRUNCATION_REASONS.contains(finishReason)) {
            return true;
        }
        String status = normalizedMetadata(envelope.path("status"));
        String incompleteReason = normalizedMetadata(
                envelope.path("incomplete_details").path("reason"));
        return "incomplete".equals(status)
                && TRUNCATION_REASONS.contains(incompleteReason);
    }

    private static String finishReason(JsonNode envelope) {
        JsonNode choices = envelope.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            String snakeCase = normalizedMetadata(
                    choice.path("finish_reason"));
            if (!"not-provided".equals(snakeCase)) {
                return snakeCase;
            }
            return normalizedMetadata(choice.path("finishReason"));
        }
        String incompleteReason = normalizedMetadata(
                envelope.path("incomplete_details").path("reason"));
        return "not-provided".equals(incompleteReason)
                ? normalizedMetadata(envelope.path("status"))
                : incompleteReason;
    }

    private static String normalizedMetadata(JsonNode node) {
        if (node == null || !node.isTextual()
                || node.asText().isBlank()) {
            return "not-provided";
        }
        String value = node.asText().trim().toLowerCase(Locale.ROOT);
        if (value.length() > 40
                || !value.matches("[a-z0-9_-]+")) {
            return "other";
        }
        return value;
    }

    private static String requestId(JsonNode envelope) {
        JsonNode id = envelope.path("id");
        if (!id.isTextual()) {
            return "not-available";
        }
        String value = id.asText().trim();
        return SAFE_REQUEST_ID.matcher(value).matches()
                ? value
                : "not-available";
    }

    private static DecodingException malformed() {
        return new DecodingException(
                "PROVIDER_MALFORMED_JSON",
                "not-provided",
                -1,
                "not-available",
                null);
    }

    private static DecodingException unsupportedEnvelope() {
        return new DecodingException(
                "PROVIDER_UNSUPPORTED_CONTENT_ENVELOPE",
                "not-provided",
                -1,
                "not-available",
                null);
    }

    private static DecodingException failure(
            String reason,
            String finishReason,
            int contentLength,
            String requestId,
            Throwable cause) {
        return new DecodingException(
                reason,
                finishReason,
                contentLength,
                requestId,
                cause);
    }

    static final class DecodingException extends RuntimeException {
        private final String reason;
        private final String finishReason;
        private final int contentLength;
        private final String requestId;

        private DecodingException(
                String reason,
                String finishReason,
                int contentLength,
                String requestId,
                Throwable cause) {
            super(reason, cause);
            this.reason = reason;
            this.finishReason = finishReason;
            this.contentLength = contentLength;
            this.requestId = requestId;
        }

        String reason() {
            return reason;
        }

        String finishReason() {
            return finishReason;
        }

        int contentLength() {
            return contentLength;
        }

        String requestId() {
            return requestId;
        }
    }
}
