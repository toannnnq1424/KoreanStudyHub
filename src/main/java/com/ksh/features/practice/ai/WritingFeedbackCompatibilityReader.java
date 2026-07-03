package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WritingFeedbackCompatibilityReader {

    private final ObjectMapper objectMapper;

    public WritingFeedbackCompatibilityReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EntryResult parseGeneratedEntry(JsonNode node) {
        return parseEntry(node, false);
    }

    public EntryResult parseStoredEntry(JsonNode node) {
        return parseEntry(node, true);
    }

    public FeedbackResult parsePayload(String payload, Collection<Long> essayQuestionIds) {
        if (payload == null || payload.isBlank()) {
            return FeedbackResult.missing();
        }
        try {
            return parseRoot(objectMapper.readTree(payload), essayQuestionIds);
        } catch (Exception ex) {
            return FeedbackResult.malformed();
        }
    }

    public FeedbackResult parseRoot(JsonNode root, Collection<Long> essayQuestionIds) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return FeedbackResult.missing();
        }
        if (!root.isObject()) {
            return FeedbackResult.malformed();
        }

        if (isLegacyFlatFeedback(root)) {
            if (essayQuestionIds == null || essayQuestionIds.size() != 1) {
                return FeedbackResult.unsupportedLegacyMulti();
            }
            EntryResult entry = parseStoredEntry(root);
            if (entry.status() != Status.VALID_LEGACY_SINGLE && entry.status() != Status.VALID_CURRENT) {
                return FeedbackResult.malformed();
            }
            Long questionId = essayQuestionIds.iterator().next();
            Map<Long, WritingEvaluationResult> entries = new LinkedHashMap<>();
            entries.put(questionId, entry.value());
            return new FeedbackResult(Status.VALID_LEGACY_SINGLE, entries);
        }

        if (essayQuestionIds == null || essayQuestionIds.isEmpty()) {
            return FeedbackResult.malformed();
        }

        Map<Long, WritingEvaluationResult> entries = new LinkedHashMap<>();
        for (Long questionId : essayQuestionIds) {
            if (questionId == null) {
                return FeedbackResult.malformed();
            }
            EntryResult entry = parseStoredEntry(root.get(String.valueOf(questionId)));
            if (entry.status() != Status.VALID_CURRENT) {
                return FeedbackResult.malformed();
            }
            entries.put(questionId, entry.value());
        }
        return new FeedbackResult(Status.VALID_CURRENT, entries);
    }

    public boolean isLegacyFlatFeedback(JsonNode root) {
        return root != null
                && root.isObject()
                && (root.has("student_text")
                || root.has("raw_score")
                || root.has("rubric_scores")
                || root.has("task_type")
                || root.has("score"));
    }

    private EntryResult parseEntry(JsonNode node, boolean strictRange) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return EntryResult.missing();
        }
        if (!node.isObject()) {
            return EntryResult.malformed();
        }
        BigDecimal rawScore = number(node.get("raw_score"));
        BigDecimal rawScoreMax = number(node.get("raw_score_max"));
        if (rawScore == null || rawScoreMax == null || rawScoreMax.compareTo(BigDecimal.ZERO) <= 0) {
            return EntryResult.malformed();
        }
        if (strictRange && (rawScore.compareTo(BigDecimal.ZERO) < 0 || rawScore.compareTo(rawScoreMax) > 0)) {
            return EntryResult.malformed();
        }
        return EntryResult.valid(new WritingEvaluationResult(
                rawScore,
                rawScoreMax,
                number(node.get("score")),
                number(node.get("overall_score")),
                text(node.get("task_type")),
                text(node.get("engine"))
        ));
    }

    private BigDecimal number(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return node.asText();
    }

    public enum Status {
        VALID_CURRENT,
        VALID_LEGACY_SINGLE,
        UNSUPPORTED_LEGACY_MULTI,
        MALFORMED,
        MISSING
    }

    public record EntryResult(Status status, WritingEvaluationResult value) {
        static EntryResult valid(WritingEvaluationResult value) {
            return new EntryResult(Status.VALID_CURRENT, value);
        }

        static EntryResult malformed() {
            return new EntryResult(Status.MALFORMED, null);
        }

        static EntryResult missing() {
            return new EntryResult(Status.MISSING, null);
        }
    }

    public record FeedbackResult(Status status, Map<Long, WritingEvaluationResult> entries) {
        static FeedbackResult missing() {
            return new FeedbackResult(Status.MISSING, Map.of());
        }

        static FeedbackResult malformed() {
            return new FeedbackResult(Status.MALFORMED, Map.of());
        }

        static FeedbackResult unsupportedLegacyMulti() {
            return new FeedbackResult(Status.UNSUPPORTED_LEGACY_MULTI, Map.of());
        }
    }
}
