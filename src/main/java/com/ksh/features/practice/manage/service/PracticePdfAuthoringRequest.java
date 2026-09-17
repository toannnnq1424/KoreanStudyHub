package com.ksh.features.practice.manage.service;

import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /*
     * A lecturer often asks to extract a precise printed-question range (for
     * example "câu 1-4 và 48-50").  Keep that scope code-owned rather than
     * asking a provider to infer it from prose after the fact.
     */
    private static final Pattern QUESTION_SCOPE = Pattern.compile(
            "(?iu)\\b(?:câu|question(?:s)?)\\s+([0-9][0-9\\s,;vàandtođến\\-–]*)");
    private static final Pattern QUESTION_GROUP_HEADING = Pattern.compile(
            "(?iu)\\b(?:nhóm|group)\\s*(\\d+)?\\s*"
                    + "(?:gồm|bao\\s+gồm|có)?\\s*"
                    + "(?:câu|question(?:s)?)\\s*");
    private static final Pattern QUESTION_RANGE = Pattern.compile(
            "(?u)(\\d+)\\s*(?:-|–|đến|to)\\s*(\\d+)");
    private static final Pattern QUESTION_NUMBER = Pattern.compile("\\d+");

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

    /** Numbers explicitly requested by the lecturer, never numbers guessed
     * from source content. An empty set means no numbered scope was stated. */
    public Set<Integer> requestedSourceQuestionNumbers() {
        return parseRequestedSourceQuestionNumbers(lecturerRequest);
    }

    /**
     * An optional group plan explicitly stated by the lecturer, in its stated
     * order. For example: “nhóm 1 câu 1-2 và nhóm 2 câu 48-50”. This is
     * source scope, not a suggestion for the provider to reinterpret.
     */
    public List<RequestedSourceQuestionGroup> requestedSourceQuestionGroups() {
        return parseRequestedSourceQuestionGroups(lecturerRequest);
    }

    static Set<Integer> parseRequestedSourceQuestionNumbers(String rawLecturerRequest) {
        TreeSet<Integer> result = new TreeSet<>();
        Matcher clause = QUESTION_SCOPE.matcher(normalize(rawLecturerRequest));
        while (clause.find()) {
            result.addAll(parseQuestionExpression(clause.group(1)));
        }
        return java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(result));
    }

    static List<RequestedSourceQuestionGroup> parseRequestedSourceQuestionGroups(
            String rawLecturerRequest
    ) {
        java.util.ArrayList<RequestedSourceQuestionGroup> result =
                new java.util.ArrayList<>();
        String normalized = normalize(rawLecturerRequest);
        Matcher clause = QUESTION_GROUP_HEADING.matcher(normalized);
        int implicitOrder = 1;
        while (clause.find()) {
            int groupOrder = clause.group(1) == null || clause.group(1).isBlank()
                    ? implicitOrder
                    : Integer.parseInt(clause.group(1));
            int expressionEnd = normalized.length();
            Matcher nextHeading = QUESTION_GROUP_HEADING.matcher(normalized);
            if (nextHeading.find(clause.end())) {
                expressionEnd = nextHeading.start();
            }
            Set<Integer> numbers = parseQuestionExpression(
                    normalized.substring(clause.end(), expressionEnd));
            if (groupOrder < 1 || groupOrder > 100 || numbers.isEmpty()) {
                throw new IllegalArgumentException("Phạm vi nhóm câu không hợp lệ.");
            }
            result.add(new RequestedSourceQuestionGroup(groupOrder, numbers));
            implicitOrder++;
        }
        return List.copyOf(result);
    }

    private static Set<Integer> parseQuestionExpression(String expression) {
        TreeSet<Integer> result = new TreeSet<>();
        Matcher ranges = QUESTION_RANGE.matcher(expression == null ? "" : expression);
        String withoutRanges = expression == null ? "" : expression;
        while (ranges.find()) {
            int from = Integer.parseInt(ranges.group(1));
            int to = Integer.parseInt(ranges.group(2));
            int low = Math.min(from, to);
            int high = Math.max(from, to);
            if (low < 1 || high > 200 || high - low > 100) {
                throw new IllegalArgumentException("Phạm vi số câu không hợp lệ.");
            }
            for (int number = low; number <= high; number++) result.add(number);
        }
        withoutRanges = QUESTION_RANGE.matcher(withoutRanges).replaceAll(" ");
        Matcher numbers = QUESTION_NUMBER.matcher(withoutRanges);
        while (numbers.find()) {
            int number = Integer.parseInt(numbers.group());
            if (number < 1 || number > 200) {
                throw new IllegalArgumentException("Phạm vi số câu không hợp lệ.");
            }
            result.add(number);
        }
        return java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(result));
    }

    public record RequestedSourceQuestionGroup(
            int groupOrder,
            Set<Integer> sourceQuestionNumbers
    ) {
        public RequestedSourceQuestionGroup {
            sourceQuestionNumbers = sourceQuestionNumbers == null
                    ? Set.of()
                    : java.util.Collections.unmodifiableSet(
                            new TreeSet<>(sourceQuestionNumbers));
            if (groupOrder < 1 || sourceQuestionNumbers.isEmpty()) {
                throw new IllegalArgumentException("Requested question group is invalid");
            }
        }
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
