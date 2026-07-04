package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WritingEvaluationNormalizer {

    private final ObjectMapper objectMapper;

    public WritingEvaluationNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Production normalizer for the unified one-call path.
     * Normalizer is the sole source of score, raw_score, raw_score_max.
     * AI provider does NOT return these fields.
     */
    public String normalize(String aiJson, String taskType, String learnerAnswer,
                            WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        try {
            JsonNode root = objectMapper.readTree(aiJson);
            String studentText = learnerAnswer == null ? "" : learnerAnswer;

            List<Map<String, Object>> rubricScores = normalizeRubricScores(root.path("rubric_scores"), taskType);
            List<Map<String, Object>> strengths = normalizeFindings(
                    root.path("strengths"),
                    WritingRubricCriterion.Polarity.STRENGTH,
                    studentText);
            List<Map<String, Object>> needs = normalizeFindings(
                    root.path("needs_improvement"),
                    WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT,
                    studentText);

            // Derive score from rubric average — sole source of truth
            double score = deriveScoreFromRubrics(rubricScores);
            double rawTopikScore = WritingScoreMatrix.rawScoreFromNormalized(score, taskType);
            double rawTopikMax = WritingScoreMatrix.rawScoreMax(taskType);

            List<Map<String, Object>> annotations = buildAnnotations(strengths, needs, studentText);

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("score", score);
            normalized.put("overall_score", score);
            normalized.put("raw_score", rawTopikScore);
            normalized.put("raw_score_max", rawTopikMax);
            normalized.put("task_type", taskType);
            normalized.put("band_label", WritingScoreMatrix.bandLabel(score));
            normalized.put("summary", text(root, "summary", text(root, "summary_vi", "")));
            normalized.put("summary_vi", text(root, "summary_vi", text(root, "summary", "")));
            normalized.put("rubric_scores", rubricScores);
            normalized.put("strengths", strengths);
            normalized.put("needs_improvement", needs);
            normalized.put("student_text", studentText);
            normalized.put("student_strengths_annotated", "");
            normalized.put("student_needs_annotated", "");
            normalized.put("annotations", annotations);
            normalized.put("upgraded_answer", text(root, "upgraded_answer", text(root, "corrected_version", "")));
            normalized.put("upgraded_answer_annotated", text(root, "upgraded_answer_annotated", ""));
            normalized.put("upgraded_annotations", normalizeUpgradedAnnotations(root.path("upgraded_annotations")));
            normalized.put("corrected_version", text(root, "corrected_version", text(root, "upgraded_answer", "")));
            normalized.put("sample_answer", text(root, "sample_answer", ""));
            normalized.put("sentence_rewrites", normalizeSentenceRewrites(root.path("sentence_rewrites"), studentText));
            normalized.put("engine", "KSH_WRITING_EVALUATOR_V2");
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return fallback("Không đọc được phản hồi AI. Hệ thống đã lưu bài làm, vui lòng chấm lại sau.", taskType);
        }
    }

    public boolean isCacheableAiResult(String normalizedJson) {
        try {
            JsonNode root = objectMapper.readTree(normalizedJson);
            return root != null
                    && root.isObject()
                    && "KSH_WRITING_EVALUATOR_V2".equals(root.path("engine").asText())
                    && root.path("raw_score").isNumber()
                    && root.path("raw_score_max").isNumber();
        } catch (Exception ex) {
            return false;
        }
    }

    public String sanitizeForCache(String normalizedJson) {
        try {
            JsonNode root = objectMapper.readTree(normalizedJson);
            if (!root.isObject()) {
                throw new IllegalArgumentException("Writing cache payload must be a JSON object.");
            }
            ObjectNode sanitized = ((ObjectNode) root).deepCopy();
            sanitized.remove("student_text");
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Writing cache payload is not valid JSON.", ex);
        }
    }

    public String rehydrateCachedResult(String cachedJson, String learnerAnswer) {
        try {
            JsonNode root = objectMapper.readTree(cachedJson);
            if (!root.isObject()) {
                throw new IllegalArgumentException("Writing cache payload must be a JSON object.");
            }

            String studentText = learnerAnswer == null ? "" : learnerAnswer;
            ObjectNode hydrated = ((ObjectNode) root).deepCopy();
            hydrated.put("student_text", studentText);

            ArrayNode strengths = filterFindingsForAnswer(hydrated.path("strengths"), studentText);
            ArrayNode needs = filterFindingsForAnswer(hydrated.path("needs_improvement"), studentText);
            ArrayNode rewrites = filterSentenceRewritesForAnswer(hydrated.path("sentence_rewrites"), studentText);

            hydrated.set("strengths", strengths);
            hydrated.set("needs_improvement", needs);
            hydrated.set("sentence_rewrites", rewrites);

            List<Map<String, Object>> strengthRows = toFindingRows(strengths);
            List<Map<String, Object>> needRows = toFindingRows(needs);
            hydrated.set("annotations", objectMapper.valueToTree(buildAnnotations(strengthRows, needRows, studentText)));

            return objectMapper.writeValueAsString(hydrated);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Writing cached result is malformed.", ex);
        }
    }

    /**
     * Backward-compatible overload for mock evaluator and PracticeService fallback.
     * Reads taskType from JSON and studentText from student_text field.
     * NOT used in production one-call path.
     */
    public String normalize(String aiJson) {
        try {
            JsonNode root = objectMapper.readTree(aiJson);
            String taskType = text(root, "task_type", "GENERAL");
            String studentText = text(root, "student_text", "");
            // Build a minimal RuleAnalysis — this path does not have the original ruleAnalysis
            return normalize(aiJson, taskType, studentText, null);
        } catch (Exception ex) {
            return fallback("Không đọc được phản hồi AI.");
        }
    }

    /**
     * Deterministic spam/empty response. 0 provider calls.
     */
    public String spamResponse(String taskType, String learnerAnswer) {
        try {
            String effectiveTaskType = taskType == null ? "GENERAL" : taskType;
            double score = 0.0;
            double rawScore = 0.0;
            double rawMax = WritingScoreMatrix.rawScoreMax(effectiveTaskType);

            List<String> rubricNames = WritingPromptRules.rubricNamesForTask(effectiveTaskType);
            List<Map<String, Object>> rubricScores = new ArrayList<>();
            for (String name : rubricNames) {
                rubricScores.add(rubric(name, 0.0, "Bài làm không hợp lệ."));
            }

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("score", score);
            normalized.put("overall_score", score);
            normalized.put("raw_score", rawScore);
            normalized.put("raw_score_max", rawMax);
            normalized.put("task_type", effectiveTaskType);
            normalized.put("band_label", "Không phản hồi");
            normalized.put("summary", "[SPAM_DETECTED] Bài làm không hợp lệ hoặc chưa đủ dữ liệu tiếng Hàn để chấm.");
            normalized.put("summary_vi", "[SPAM_DETECTED] Bài làm không hợp lệ hoặc chưa đủ dữ liệu tiếng Hàn để chấm.");
            normalized.put("rubric_scores", rubricScores);
            normalized.put("strengths", List.of());
            normalized.put("needs_improvement", List.of());
            normalized.put("student_text", learnerAnswer == null ? "" : learnerAnswer);
            normalized.put("student_strengths_annotated", "");
            normalized.put("student_needs_annotated", "");
            normalized.put("annotations", List.of());
            normalized.put("upgraded_answer", "");
            normalized.put("upgraded_answer_annotated", "");
            normalized.put("upgraded_annotations", List.of());
            normalized.put("corrected_version", "");
            normalized.put("sample_answer", "");
            normalized.put("sentence_rewrites", List.of());
            normalized.put("engine", "KSH_WRITING_EVALUATOR_V2");
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return fallback("[SPAM_DETECTED] Bài làm không hợp lệ.", taskType);
        }
    }

    public String fallback(String reason) {
        return fallback(reason, "GENERAL");
    }

    public String fallback(String reason, String taskType) {
        try {
            String effectiveTaskType = taskType == null ? "GENERAL" : taskType;
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("score", 1.0);
            normalized.put("overall_score", 1.0);
            normalized.put("raw_score", WritingScoreMatrix.rawScoreFromNormalized(1.0, effectiveTaskType));
            normalized.put("raw_score_max", WritingScoreMatrix.rawScoreMax(effectiveTaskType));
            normalized.put("task_type", effectiveTaskType);
            normalized.put("band_label", WritingScoreMatrix.bandLabel(1.0));
            normalized.put("summary", reason);
            normalized.put("summary_vi", reason);
            normalized.put("rubric_scores", WritingPromptRules.rubricNamesForTask(effectiveTaskType).stream()
                    .map(name -> rubric(name, 1.0, "Cần chấm lại khi AI khả dụng."))
                    .toList());
            normalized.put("strengths", List.of());
            normalized.put("needs_improvement", List.of());
            normalized.put("student_text", "");
            normalized.put("student_strengths_annotated", "");
            normalized.put("student_needs_annotated", "");
            normalized.put("annotations", List.of());
            normalized.put("upgraded_answer", "");
            normalized.put("upgraded_answer_annotated", "");
            normalized.put("upgraded_annotations", List.of());
            normalized.put("corrected_version", "");
            normalized.put("sample_answer", "");
            normalized.put("sentence_rewrites", List.of());
            normalized.put("engine", "KSH_WRITING_EVALUATOR_FALLBACK");
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return "{\"score\":1.0,\"overall_score\":1.0,\"summary_vi\":\"Không tạo được phản hồi AI.\"}";
        }
    }

    // ---- Scoring ----

    /**
     * Derives final score from rubric scores using equal-weight average.
     * No existing code defines task-specific rubric weights. Equal average is used as
     * a stable default. Task-specific weights should be addressed in a dedicated scoring task.
     */
    static double deriveScoreFromRubrics(List<Map<String, Object>> rubricScores) {
        if (rubricScores == null || rubricScores.isEmpty()) {
            return 1.0;
        }
        double sum = 0;
        int count = 0;
        for (Map<String, Object> row : rubricScores) {
            Object scoreObj = row.get("score");
            if (scoreObj instanceof Number n) {
                sum += n.doubleValue();
                count++;
            }
        }
        if (count == 0) return 1.0;
        return WritingScoreMatrix.clampAndRound(sum / count);
    }

    // ---- Rubric validation ----

    private List<Map<String, Object>> normalizeRubricScores(JsonNode array, String taskType) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode node : array) {
                rows.add(rubric(
                        node.path("name").asText(""),
                        WritingScoreMatrix.clampAndRound(node.path("score").asDouble(1.0)),
                        node.path("feedback").asText("")
                ));
            }
        }
        return enforceTaskRubrics(rows, taskType);
    }

    private static List<Map<String, Object>> enforceTaskRubrics(List<Map<String, Object>> rows, String taskType) {
        List<String> expectedNames = WritingPromptRules.rubricNamesForTask(
                taskType == null ? "GENERAL" : taskType);
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (String name : expectedNames) {
            normalized.add(matchOrFallback(rows, name));
        }
        return normalized;
    }

    private static Map<String, Object> matchOrFallback(List<Map<String, Object>> rows, String name) {
        for (Map<String, Object> row : rows) {
            if (name.equals(row.get("name"))) {
                return row;
            }
        }
        String nameLower = name.toLowerCase();
        for (Map<String, Object> row : rows) {
            Object rowName = row.get("name");
            if (rowName instanceof String s) {
                String sLower = s.toLowerCase();
                if (sLower.contains(nameLower) || nameLower.contains(sLower)) {
                    return row;
                }
            }
        }
        return rubric(name, 1.0, "AI chưa trả đủ nhận xét cho tiêu chí này.");
    }

    // ---- Findings validation ----

    private List<Map<String, Object>> normalizeFindings(JsonNode array,
                                                        WritingRubricCriterion.Polarity polarity,
                                                        String studentText) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!array.isArray()) {
            return rows;
        }
        int index = 1;
        for (JsonNode node : array) {
            WritingRubricCriterion criterion = WritingRubricCriterion.parse(node.path("criterionId").asText(null));
            if (criterion == null || criterion.polarity() != polarity) {
                continue;
            }
            String evidence = node.path("evidence").asText("").trim();
            String explanation = node.path("explanationVi").asText("").trim();
            String correction = node.path("correction").asText("").trim();
            if (evidence.isBlank() || explanation.isBlank()) {
                continue;
            }
            // Evidence validation: must be exact substring of original learnerAnswer
            if (!studentText.isEmpty() && !studentText.contains(evidence)) {
                continue;
            }
            if (polarity == WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT && correction.isBlank()) {
                continue;
            }
            if (polarity == WritingRubricCriterion.Polarity.STRENGTH) {
                correction = "";
            }

            // --- Enriched fields ---
            String category = node.path("category").asText(null);
            if (category == null || category.isBlank()) {
                category = criterion.vietnameseLabel();
            }
            String subcategory = node.path("subcategory").asText("");
            String severity = node.path("severity").asText(
                    polarity == WritingRubricCriterion.Polarity.STRENGTH ? "LOW" : "MEDIUM");
            String displayType = node.path("displayType").asText(null);
            if (displayType == null || displayType.isBlank()) {
                displayType = inferDisplayType(evidence);
            }
            String uiLabel = node.path("uiLabel").asText(criterion.vietnameseLabel());
            String errorType = node.path("errorType").asText("");
            String whyItIsGood = node.path("whyItIsGood").asText("");
            String topikTip = node.path("topikTip").asText("");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", index++);
            row.put("criterionId", criterion.id());
            row.put("category", category);
            row.put("subcategory", subcategory);
            row.put("vietnameseLabel", criterion.vietnameseLabel());
            row.put("koreanLabel", criterion.koreanLabel());
            row.put("evidence", evidence);
            row.put("explanationVi", explanation);
            row.put("correction", correction);
            row.put("severity", severity);
            row.put("displayType", displayType);
            row.put("uiLabel", uiLabel);
            row.put("errorType", errorType);
            row.put("whyItIsGood", whyItIsGood);
            row.put("topikTip", topikTip);
            rows.add(row);
        }
        return rows;
    }

    private static String inferDisplayType(String evidence) {
        if (evidence == null) return "PHRASE";
        int len = evidence.length();
        if (len <= 8) return "WORD";
        if (evidence.contains(".") || evidence.contains("?") || evidence.contains("!") || evidence.contains("。")) return "SENTENCE";
        if (len <= 30) return "PHRASE";
        return "SENTENCE";
    }

    private List<Map<String, Object>> normalizeSentenceRewrites(JsonNode array, String studentText) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!array.isArray()) {
            return rows;
        }
        for (JsonNode node : array) {
            String original = node.path("original").asText("").trim();
            String upgraded = node.path("upgraded").asText("").trim();
            String reason = node.path("reason").asText("").trim();
            if (original.isBlank() || upgraded.isBlank() || reason.isBlank()) {
                continue;
            }
            // Evidence validation: original must be substring of learnerAnswer
            if (!studentText.isEmpty() && !studentText.contains(original)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("original", original);
            row.put("upgraded", upgraded);
            row.put("reason", reason);
            rows.add(row);
        }
        return rows;
    }

    private ArrayNode filterFindingsForAnswer(JsonNode array, String studentText) {
        ArrayNode filtered = objectMapper.createArrayNode();
        if (!array.isArray()) {
            return filtered;
        }
        for (JsonNode node : array) {
            String evidence = node.path("evidence").asText("").trim();
            if (!evidence.isBlank() && (studentText.isEmpty() || studentText.contains(evidence))) {
                filtered.add(node);
            }
        }
        return filtered;
    }

    private ArrayNode filterSentenceRewritesForAnswer(JsonNode array, String studentText) {
        ArrayNode filtered = objectMapper.createArrayNode();
        if (!array.isArray()) {
            return filtered;
        }
        for (JsonNode node : array) {
            String original = node.path("original").asText("").trim();
            if (!original.isBlank() && (studentText.isEmpty() || studentText.contains(original))) {
                filtered.add(node);
            }
        }
        return filtered;
    }

    private List<Map<String, Object>> toFindingRows(JsonNode array) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!array.isArray()) {
            return rows;
        }
        for (JsonNode node : array) {
            Map<String, Object> row = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> row.put(entry.getKey(), toPlainValue(entry.getValue())));
            rows.add(row);
        }
        return rows;
    }

    private Object toPlainValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isTextual()) return value.asText();
        if (value.isInt()) return value.asInt();
        if (value.isLong()) return value.asLong();
        if (value.isFloat() || value.isDouble() || value.isBigDecimal()) return value.asDouble();
        if (value.isBoolean()) return value.asBoolean();
        return objectMapper.convertValue(value, Object.class);
    }

    private List<Map<String, Object>> normalizeUpgradedAnnotations(JsonNode array) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!array.isArray()) {
            return rows;
        }
        for (JsonNode node : array) {
            String evidence = node.path("evidence").asText("").trim();
            String explanationVi = node.path("explanationVi").asText("").trim();
            if (evidence.isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("criterionId", node.path("criterionId").asText(""));
            row.put("category", node.path("category").asText(""));
            row.put("evidence", evidence);
            row.put("start", node.path("start").asInt(-1));
            row.put("end", node.path("end").asInt(-1));
            row.put("explanationVi", explanationVi);
            rows.add(row);
        }
        return rows;
    }

    // ---- Annotation building ----

    private List<Map<String, Object>> buildAnnotations(
            List<Map<String, Object>> strengths,
            List<Map<String, Object>> needs,
            String studentText) {
        List<Map<String, Object>> annotations = new ArrayList<>();
        if (studentText == null || studentText.isBlank()) {
            return annotations;
        }
        AtomicInteger idCounter = new AtomicInteger(1);
        addAnnotations(annotations, strengths, "strength", studentText, idCounter);
        addAnnotations(annotations, needs, "need", studentText, idCounter);
        return annotations;
    }

    private void addAnnotations(
            List<Map<String, Object>> annotations,
            List<Map<String, Object>> findings,
            String kind,
            String text,
            AtomicInteger idCounter) {
        Map<String, Integer> searchFrom = new java.util.HashMap<>();
        int findingIndex = 1;
        for (Map<String, Object> item : findings) {
            String evidence = (String) item.get("evidence");
            String criterionId = (String) item.get("criterionId");
            if (evidence == null || evidence.isBlank() || criterionId == null || criterionId.isBlank()) {
                findingIndex++;
                continue;
            }

            String key = criterionId + "|" + kind;
            int fromIdx = searchFrom.getOrDefault(key, 0);
            int start = text.indexOf(evidence, fromIdx);

            Map<String, Object> annotation = new LinkedHashMap<>();
            annotation.put("id", "ann_" + idCounter.getAndIncrement());
            annotation.put("kind", kind);
            annotation.put("criterionId", criterionId);
            annotation.put("category", item.getOrDefault("category", ""));
            annotation.put("subcategory", item.getOrDefault("subcategory", ""));
            annotation.put("evidence", evidence);
            annotation.put("start", start);
            annotation.put("end", start >= 0 ? start + evidence.length() : -1);
            annotation.put("explanationVi", item.get("explanationVi"));
            annotation.put("correction", item.get("correction"));
            annotation.put("severity", item.getOrDefault("severity", kind.equals("strength") ? "LOW" : "MEDIUM"));
            annotation.put("displayType", item.getOrDefault("displayType", inferDisplayType(evidence)));
            annotation.put("index", findingIndex);
            annotations.add(annotation);

            if (start >= 0) {
                searchFrom.put(key, start + evidence.length());
            }
            findingIndex++;
        }
    }

    // ---- Helpers ----

    private static Map<String, Object> rubric(String name, double score, String feedback) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("score", score);
        row.put("feedback", feedback == null ? "" : feedback);
        return row;
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return value == null ? fallback : value;
    }
}
