package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class PracticeAuthoringCandidateJson {

    private final ObjectMapper objectMapper;

    public PracticeAuthoringCandidateJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String digest(JsonNode value) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(canonical(value));
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Cannot calculate candidate content digest", exception);
        }
    }

    public JsonNode canonical(JsonNode value) {
        if (value == null || value.isNull()) {
            return objectMapper.nullNode();
        }
        if (value.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = value.fields();
            iterator.forEachRemaining(fields::add);
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            fields.forEach(field -> sorted.set(
                    field.getKey(), canonical(field.getValue())));
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            value.forEach(item -> array.add(canonical(item)));
            return array;
        }
        return value.deepCopy();
    }

    public String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write candidate JSON", exception);
        }
    }

    public ObjectNode readObject(String value) {
        try {
            JsonNode parsed = objectMapper.readTree(value);
            if (parsed instanceof ObjectNode object) {
                return object;
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Persisted candidate JSON is invalid", exception);
        }
        throw new IllegalStateException("Persisted candidate JSON must be an object");
    }

    public static String normalizedText(String value) {
        if (value == null) return "";
        return Normalizer.normalize(
                value.replace("\r\n", "\n").replace('\r', '\n'),
                Normalizer.Form.NFC).trim();
    }

    public static String stripDigestPrefix(String value) {
        String normalized = normalizedText(value).toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("sha256:")
                ? normalized.substring(7)
                : normalized;
    }

    public static String prefixedDigest(String value) {
        return "sha256:" + stripDigestPrefix(value);
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(Character.forDigit((current >>> 4) & 0x0f, 16));
            value.append(Character.forDigit(current & 0x0f, 16));
        }
        return value.toString();
    }
}
