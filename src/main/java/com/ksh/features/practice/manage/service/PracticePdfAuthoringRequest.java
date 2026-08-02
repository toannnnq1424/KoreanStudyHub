package com.ksh.features.practice.manage.service;

import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One bounded, target-authorized PDF/Text authoring request. Source content is
 * deliberately carried as untrusted data rather than concatenated into an
 * instruction string.
 */
public record PracticePdfAuthoringRequest(
        SourceType sourceType,
        SourceOperation operation,
        String sourceName,
        String sourceDigest,
        TargetRoute target,
        String lecturerRequest,
        List<SourceEvidence> evidence,
        Map<String, Object> sourceContext,
        List<PracticeStructuredGenerationRequest.ImageEvidence> images
) {

    public PracticePdfAuthoringRequest {
        sourceType = Objects.requireNonNull(sourceType, "sourceType");
        operation = Objects.requireNonNull(operation, "operation");
        target = Objects.requireNonNull(target, "target");
        sourceName = bounded(sourceName, 255, "sourceName");
        sourceDigest = bounded(sourceDigest, 71, "sourceDigest")
                .toLowerCase(java.util.Locale.ROOT);
        if (!sourceDigest.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceDigest must be SHA-256");
        }
        if (operation == SourceOperation.NONE) {
            throw new IllegalArgumentException("PDF authoring operation is required");
        }
        lecturerRequest = normalize(lecturerRequest);
        if (lecturerRequest.length() > 10_000) {
            throw new IllegalArgumentException(
                    "Yêu cầu của giảng viên không được vượt quá 10.000 ký tự.");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (evidence.isEmpty() || evidence.size() > 200) {
            throw new IllegalArgumentException(
                    "Nguồn authoring phải có từ 1 đến 200 evidence blocks.");
        }
        sourceContext = sourceContext == null ? Map.of() : immutableMap(sourceContext);
        images = images == null ? List.of() : List.copyOf(images);
    }

    public enum SourceType {
        TEXT,
        PDF
    }

    public record SourceEvidence(
            String kind,
            String sourceId,
            Integer pageNumber,
            int textLength,
            String untrustedText
    ) {
        public SourceEvidence {
            kind = bounded(kind, 20, "evidence.kind");
            sourceId = bounded(sourceId, 200, "evidence.sourceId");
            untrustedText = normalize(untrustedText);
            if (!List.of("TEXT_SPAN", "PAGE").contains(kind)) {
                throw new IllegalArgumentException("Evidence kind is invalid");
            }
            if ("PAGE".equals(kind) && (pageNumber == null || pageNumber < 1)) {
                throw new IllegalArgumentException(
                        "Page evidence requires pageNumber");
            }
            if (textLength < 0 || textLength != untrustedText.length()) {
                throw new IllegalArgumentException("Evidence text length is invalid");
            }
        }
    }

    private static String bounded(String raw, int max, String field) {
        String value = normalize(raw);
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    static String normalize(String raw) {
        if (raw == null) return "";
        return java.text.Normalizer.normalize(
                        raw.replace("\r\n", "\n").replace('\r', '\n'),
                        java.text.Normalizer.Form.NFC)
                .trim();
    }

    private static Map<String, Object> immutableMap(Map<String, ?> raw) {
        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
        raw.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "sourceContext key"),
                immutableValue(value)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
            map.forEach((key, child) -> copy.put(
                    Objects.toString(key), immutableValue(child)));
            return java.util.Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(PracticePdfAuthoringRequest::immutableValue).toList();
        }
        if (value instanceof java.util.Set<?> set) {
            return set.stream().map(PracticePdfAuthoringRequest::immutableValue)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return Objects.requireNonNull(value, "sourceContext value");
    }
}
