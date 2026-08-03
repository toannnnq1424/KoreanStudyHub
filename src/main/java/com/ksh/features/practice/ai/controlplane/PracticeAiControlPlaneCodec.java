package com.ksh.features.practice.ai.controlplane;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Component
public class PracticeAiControlPlaneCodec {

    private static final Set<String> CAPABILITY_FIELDS = Set.of(
            "strictJsonSchema",
            "imageInput",
            "transcriptTextInput",
            "batchTranscription",
            "speechSynthesis",
            "directAudioInput");
    private static final Set<String> LIMIT_FIELDS = Set.of(
            "connectTimeoutMs",
            "readTimeoutMs",
            "maxRetries",
            "maxRequestBytes",
            "maxResponseBytes");

    private final ObjectMapper objectMapper;

    public PracticeAiControlPlaneCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PracticeAiCapabilitySet parseCapabilities(
            PracticeAiPurpose purpose,
            String json) {
        JsonNode root = object(json, "PROVIDER_CAPABILITY_INCOMPATIBLE");
        exactFields(root, CAPABILITY_FIELDS, "PROVIDER_CAPABILITY_INCOMPATIBLE");
        PracticeAiCapabilitySet capabilities = new PracticeAiCapabilitySet(
                bool(root, "strictJsonSchema"),
                bool(root, "imageInput"),
                bool(root, "transcriptTextInput"),
                bool(root, "batchTranscription"),
                bool(root, "speechSynthesis"),
                bool(root, "directAudioInput"));
        if (!capabilities.supportsAll(purpose.requiredCapabilities())) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_CAPABILITY_INCOMPATIBLE", false);
        }
        return capabilities;
    }

    public PracticeAiLimits parseLimits(String json) {
        JsonNode root = object(json, "PROVIDER_LIMITS_INCOMPATIBLE");
        exactFields(root, LIMIT_FIELDS, "PROVIDER_LIMITS_INCOMPATIBLE");
        try {
            return new PracticeAiLimits(
                    integer(root, "connectTimeoutMs"),
                    integer(root, "readTimeoutMs"),
                    integer(root, "maxRetries"),
                    integer(root, "maxRequestBytes"),
                    integer(root, "maxResponseBytes"));
        } catch (ArithmeticException exception) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_LIMITS_INCOMPATIBLE", false, exception);
        }
    }

    public String capabilityJson(PracticeAiPurpose purpose, boolean pdfImageInput) {
        return capabilityJson(purpose, pdfImageInput, false);
    }

    public String capabilityJson(
            PracticeAiPurpose purpose,
            boolean pdfImageInput,
            boolean directAudioInput) {
        boolean structured = purpose.structuredJson();
        boolean image = switch (purpose) {
            case PRACTICE_PDF_AUTHORING -> pdfImageInput;
            case PRACTICE_RL_EXPLANATION,
                 PRACTICE_WRITING_EVALUATION,
                 PRACTICE_SPEAKING_EVALUATION -> true;
            default -> false;
        };
        ObjectNode root = objectMapper.createObjectNode();
        root.put("strictJsonSchema", structured);
        root.put("imageInput", image);
        root.put("transcriptTextInput",
                purpose == PracticeAiPurpose.PRACTICE_SPEAKING_EVALUATION);
        root.put("batchTranscription",
                purpose == PracticeAiPurpose.PRACTICE_SPEAKING_STT);
        root.put("speechSynthesis",
                purpose == PracticeAiPurpose.PRACTICE_SPEAKING_TTS);
        root.put("directAudioInput",
                purpose == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION
                        && directAudioInput);
        return write(root);
    }

    public String limitsJson(
            int connectTimeoutMs,
            int readTimeoutMs,
            int maxRetries,
            int maxRequestBytes,
            int maxResponseBytes) {
        PracticeAiLimits validated = new PracticeAiLimits(
                connectTimeoutMs,
                readTimeoutMs,
                maxRetries,
                maxRequestBytes,
                maxResponseBytes);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("connectTimeoutMs", validated.connectTimeoutMs());
        root.put("readTimeoutMs", validated.readTimeoutMs());
        root.put("maxRetries", validated.maxRetries());
        root.put("maxRequestBytes", validated.maxRequestBytes());
        root.put("maxResponseBytes", validated.maxResponseBytes());
        return write(root);
    }

    public String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private JsonNode object(String json, String errorCode) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new PracticeAiControlPlaneException(errorCode, false);
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new PracticeAiControlPlaneException(errorCode, false, exception);
        }
    }

    private static void exactFields(JsonNode root, Set<String> expected, String errorCode) {
        Set<String> actual = new java.util.HashSet<>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new PracticeAiControlPlaneException(errorCode, false);
        }
    }

    private static boolean bool(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isBoolean()) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_CAPABILITY_INCOMPATIBLE", false);
        }
        return value.booleanValue();
    }

    private static int integer(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_LIMITS_INCOMPATIBLE", false);
        }
        return value.intValue();
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize control-plane JSON", exception);
        }
    }
}
