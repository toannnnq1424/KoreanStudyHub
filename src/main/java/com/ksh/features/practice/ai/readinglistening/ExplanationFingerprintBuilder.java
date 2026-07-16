package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class ExplanationFingerprintBuilder {

    static final String ASSESSMENT_SCHEMA_VERSION = "rl-assessment-contract-v1";

    private final ObjectMapper objectMapper;
    private final ReadingListeningExplanationClient client;

    public ExplanationFingerprintBuilder(
            ObjectMapper objectMapper,
            ReadingListeningExplanationClient client) {
        this.objectMapper = objectMapper;
        this.client = client;
    }

    public ExplanationFingerprint build(ExplanationArtifactInput input) {
        String contentJson = canonicalJson(input.questionContent());
        String answerSpecJson = canonicalJson(input.answerSpec());
        String stimulusJson = canonicalJson(new StimulusFingerprintMaterial(
                input.stimulus().schemaVersion(),
                input.stimulus().type().name(),
                input.stimulus().passageText(),
                input.stimulus().transcriptText(),
                input.stimulus().approved()));
        String mediaJson = canonicalJson(input.media().stream()
                .map(media -> new MediaFingerprintMaterial(
                        media.role(), media.kind(), media.sha256()))
                .sorted(Comparator
                        .comparing((MediaFingerprintMaterial media) -> normalize(media.role()))
                        .thenComparing(media -> normalize(media.kind()))
                        .thenComparing(media -> normalize(media.sha256())))
                .toList());
        String questionHash = sha256(framed(
                ASSESSMENT_SCHEMA_VERSION,
                input.schemaVersion(),
                input.skill().name(),
                input.questionType().name(),
                normalize(input.prompt()),
                normalize(input.instruction()),
                contentJson,
                normalize(input.teacherExplanation()),
                normalize(input.optionLabelMode())));
        String stimulusHash = sha256(framed(stimulusJson));
        String answerSpecHash = sha256(framed(answerSpecJson));
        String mediaBundleHash = sha256(framed(mediaJson));
        String fingerprint = sha256(framed(
                ASSESSMENT_SCHEMA_VERSION,
                questionHash,
                stimulusHash,
                answerSpecHash,
                mediaBundleHash,
                client.model(),
                client.promptVersion(),
                client.schemaVersion(),
                input.explanationLanguage()));
        return new ExplanationFingerprint(
                fingerprint,
                questionHash,
                stimulusHash,
                answerSpecHash,
                mediaBundleHash,
                ASSESSMENT_SCHEMA_VERSION,
                client.model(),
                client.promptVersion(),
                client.schemaVersion(),
                input.explanationLanguage(),
                canonicalJson(input));
    }

    public String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(canonicalize(objectMapper.valueToTree(value)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not canonicalize explanation input", exception);
        }
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isTextual()) {
            return JsonNodeFactory.instance.textNode(normalize(node.textValue()));
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                result.add(canonicalize(item));
            }
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            iterator.forEachRemaining(fields::add);
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, JsonNode> field : fields) {
                result.set(field.getKey(), canonicalize(field.getValue()));
            }
            return result;
        }
        return node.deepCopy();
    }

    static String framed(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String normalized = value == null ? "" : value;
            builder.append(normalized.getBytes(StandardCharsets.UTF_8).length)
                    .append(':')
                    .append(normalized);
        }
        return builder.toString();
    }

    static String normalize(String value) {
        return value == null
                ? ""
                : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    static String sha256(String material) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record StimulusFingerprintMaterial(
            String schemaVersion,
            String type,
            String passageText,
            String transcriptText,
            boolean approved
    ) {
    }

    private record MediaFingerprintMaterial(
            String role,
            String kind,
            String sha256
    ) {
    }
}
