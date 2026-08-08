package com.ksh.features.practice.ai.contract;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One versioned completeness vocabulary shared by every Practice AI result. */
public record PracticeAiResultCompleteness(
        String version,
        Status status,
        String reasonCode,
        int rejectedItemCount) {

    public static final String FIELD = "result_completeness";
    public static final String VERSION = "practice-ai-result-completeness-v1";
    public static final String REASON_NONE = "NONE";

    public PracticeAiResultCompleteness {
        if (!VERSION.equals(version)
                || status == null
                || reasonCode == null
                || !reasonCode.matches("[A-Z][A-Z0-9_]{0,95}")
                || rejectedItemCount < 0
                || (status == Status.COMPLETE
                    && (!REASON_NONE.equals(reasonCode)
                        || rejectedItemCount != 0))
                || (status == Status.PARTIAL_NON_SCORE
                    && rejectedItemCount == 0)) {
            throw new IllegalArgumentException(
                    "Invalid Practice AI result completeness");
        }
    }

    public static PracticeAiResultCompleteness complete() {
        return new PracticeAiResultCompleteness(
                VERSION, Status.COMPLETE, REASON_NONE, 0);
    }

    public static PracticeAiResultCompleteness partial(
            String reasonCode, int rejectedItemCount) {
        return new PracticeAiResultCompleteness(
                VERSION, Status.PARTIAL_NON_SCORE,
                reasonCode, rejectedItemCount);
    }

    public static PracticeAiResultCompleteness unavailable(
            String reasonCode, int rejectedItemCount) {
        return new PracticeAiResultCompleteness(
                VERSION, Status.UNAVAILABLE, reasonCode, rejectedItemCount);
    }

    public static PracticeAiResultCompleteness require(JsonNode parent) {
        Objects.requireNonNull(parent);
        JsonNode node = parent.get(FIELD);
        if (node == null || !node.isObject() || node.size() != 4) {
            throw new IllegalArgumentException(
                    "Missing Practice AI result completeness");
        }
        JsonNode version = node.get("version");
        JsonNode status = node.get("status");
        JsonNode reason = node.get("reason_code");
        JsonNode rejected = node.get("rejected_item_count");
        if (version == null || !version.isTextual()
                || status == null || !status.isTextual()
                || reason == null || !reason.isTextual()
                || rejected == null || !rejected.isIntegralNumber()
                || !rejected.canConvertToInt()) {
            throw new IllegalArgumentException(
                    "Malformed Practice AI result completeness");
        }
        try {
            return new PracticeAiResultCompleteness(
                    version.textValue(),
                    Status.valueOf(status.textValue()),
                    reason.textValue(),
                    rejected.intValue());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Unsupported Practice AI result completeness", exception);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", version);
        value.put("status", status.name());
        value.put("reason_code", reasonCode);
        value.put("rejected_item_count", rejectedItemCount);
        return Map.copyOf(value);
    }

    public boolean scoreBearingComplete() {
        return status == Status.COMPLETE;
    }

    public enum Status {
        COMPLETE,
        PARTIAL_NON_SCORE,
        UNAVAILABLE
    }
}
