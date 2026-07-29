package com.ksh.features.practice.ai.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

@Component
public class CanonicalPracticeJson {

    private final ObjectMapper objectMapper;

    public CanonicalPracticeJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalPayload serialize(Object value) {
        try {
            JsonNode canonical = canonicalize(objectMapper.valueToTree(value));
            byte[] utf8 = objectMapper.writeValueAsBytes(canonical);
            String json = new String(utf8, StandardCharsets.UTF_8);
            return new CanonicalPayload(json, utf8, sha256(utf8));
        } catch (PracticeAiContractException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PracticeAiContractException(
                    "INPUT_SERIALIZATION_FAILED",
                    false,
                    exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()
                && !node.isTextual()) {
            return node == null ? JsonNodeFactory.instance.nullNode() : node;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(
                    Normalizer.normalize(node.textValue(), Normalizer.Form.NFC));
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                result.add(canonicalize(item));
            }
            return result;
        }
        if (node.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalizedName = Normalizer.normalize(
                        field.getKey(),
                        Normalizer.Form.NFC);
                if (sorted.put(normalizedName, canonicalize(field.getValue()))
                        != null) {
                    throw new PracticeAiContractException(
                            "INPUT_DUPLICATE_KEY_AFTER_NFC",
                            false);
                }
            }
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            sorted.forEach(result::set);
            return result;
        }
        throw new PracticeAiContractException(
                "INPUT_UNSUPPORTED_JSON_NODE",
                false);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            hex.append(Character.forDigit(value & 0x0f, 16));
        }
        return hex.toString();
    }

    public record CanonicalPayload(
            String json,
            byte[] utf8,
            String sha256
    ) {
        public CanonicalPayload {
            utf8 = utf8.clone();
        }

        @Override
        public byte[] utf8() {
            return utf8.clone();
        }
    }
}
