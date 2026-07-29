package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
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

            if (!hasUsableRubricContract(root.path("rubric_scores"), taskType)) {
                return contractFailure("PROVIDER_CONTRACT_INVALID", taskType, studentText);
            }

            List<Map<String, Object>> rubricScores = normalizeRubricScores(root.path("rubric_scores"), taskType);
            if (rubricScores.isEmpty()) {
                return contractFailure("PROVIDER_CONTRACT_INVALID", taskType, studentText);
            }
            List<Map<String, Object>> strengths = normalizeFindings(
                    root.path("strengths"),
                    WritingRubricCriterion.Polarity.STRENGTH,
                    studentText,
                    taskType);
            List<Map<String, Object>> needs = normalizeFindings(
                    root.path("needs_improvement"),
                    WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT,
                    studentText,
                    taskType);
            double score = deriveScoreFromRubrics(rubricScores);
            double rawTopikScore = sumRubricScores(rubricScores);
            double rawTopikMax =
                    WritingScoringPolicy.rubricFor(taskType).totalMaxScore();

            List<Map<String, Object>> annotations = buildAnnotations(strengths, needs, studentText);

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("score", score);
            normalized.put("overall_score", score);
            normalized.put("percentage", score);
            normalized.put("raw_score", rawTopikScore);
            normalized.put("raw_score_max", rawTopikMax);
            normalized.put("scoring_contract",
                    WritingScoringPolicy.SCORING_CONTRACT);
            normalized.put("policy_bundle_id",
                    WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
            normalized.put("task_type", taskType);
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
            normalized.put("sample_answer", "");
            normalized.put("sentence_rewrites", normalizeSentenceRewrites(root.path("sentence_rewrites"), studentText));
            normalized.put("engine", "KSH_WRITING_EVALUATOR_V2");
            putEvaluationMetadata(normalized,
                    "EVALUATED",
                    "PROVIDER",
                    "NONE",
                    false,
                    true);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return contractFailure(
                    "PROVIDER_MALFORMED_JSON",
                    taskType,
                    learnerAnswer);
        }
    }

    public boolean isCacheableAiResult(String normalizedJson) {
        try {
            JsonNode root = objectMapper.readTree(normalizedJson);
            return isTrustedProviderEvaluation(
                    root,
                    root == null ? null : root.path("task_type").asText(null),
                    false);
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

    public String rehydrateCachedResult(String cachedJson, String learnerAnswer,
                                        String expectedTaskType) {
        try {
            JsonNode root = objectMapper.readTree(cachedJson);
            if (!isTrustedProviderEvaluation(root, expectedTaskType, true)) {
                throw new IllegalArgumentException(
                        "Writing cache payload does not match the current provider contract.");
            }

            String studentText = learnerAnswer == null ? "" : learnerAnswer;
            ObjectNode hydrated = ((ObjectNode) root).deepCopy();
            hydrated.put("student_text", studentText);
            hydrated.put("evaluation_origin_source", "PROVIDER");
            hydrated.put("evaluation_source", "CACHE");

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

    public String rehydrateCachedResult(String cachedJson, String learnerAnswer) {
        try {
            JsonNode root = objectMapper.readTree(cachedJson);
            return rehydrateCachedResult(
                    cachedJson,
                    learnerAnswer,
                    root == null ? null : root.path("task_type").asText(null));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Writing cached result is malformed.", ex);
        }
    }

    private static boolean isTrustedProviderEvaluation(JsonNode root,
                                                       String expectedTaskType,
                                                       boolean requireSanitizedPayload) {
        if (root == null || !root.isObject()
                || expectedTaskType == null || expectedTaskType.isBlank()
                || !"KSH_WRITING_EVALUATOR_V2".equals(root.path("engine").asText())
                || !"EVALUATED".equals(root.path("evaluation_status").asText())
                || !"PROVIDER".equals(root.path("evaluation_source").asText())
                || !"NONE".equals(root.path("evaluation_reason").asText())
                || !root.path("score_available").asBoolean(false)
                || !WritingScoringPolicy.SCORING_CONTRACT.equals(
                        root.path("scoring_contract").asText())
                || !WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID.equals(
                        root.path("policy_bundle_id").asText())
                || !expectedTaskType.equals(root.path("task_type").asText())
                || !root.path("score").isNumber()
                || !root.path("overall_score").isNumber()
                || !root.path("percentage").isNumber()
                || !root.path("raw_score").isNumber()
                || !root.path("raw_score_max").isNumber()
                || (requireSanitizedPayload && root.has("student_text"))) {
            return false;
        }

        WritingScoringRubric expectedRubric = WritingScoringPolicy.rubricFor(expectedTaskType);
        JsonNode rubricScores = root.path("rubric_scores");
        if (!rubricScores.isArray() || rubricScores.size() != expectedRubric.criteria().size()) {
            return false;
        }

        Map<String, WritingScoringCriterion> expectedById = new java.util.HashMap<>();
        for (WritingScoringCriterion criterion : expectedRubric.criteria()) {
            expectedById.put(criterion.criterionId(), criterion);
        }

        java.util.Set<String> seenCriterionIds = new java.util.HashSet<>();
        double rubricScoreSum = 0.0;
        for (JsonNode rubricScore : rubricScores) {
            if (!rubricScore.isObject()) {
                return false;
            }
            String criterionId = rubricScore.path("criterionId").asText();
            WritingScoringCriterion criterion = expectedById.get(criterionId);
            if (criterion == null
                    || !seenCriterionIds.add(criterionId)
                    || !criterion.displayName().equals(rubricScore.path("name").asText())
                    || !rubricScore.path("maxScore").isNumber()
                    || Double.compare(
                            rubricScore.path("maxScore").asDouble(),
                            criterion.maxScore()) != 0
                    || !rubricScore.path("score").isNumber()) {
                return false;
            }
            double criterionScore = rubricScore.path("score").asDouble();
            if (!Double.isFinite(criterionScore)
                    || criterionScore < 0.0
                    || criterionScore > criterion.maxScore()) {
                return false;
            }
            rubricScoreSum += criterionScore;
        }
        if (seenCriterionIds.size() != expectedById.size()) {
            return false;
        }

        double score = root.path("score").asDouble();
        double overallScore = root.path("overall_score").asDouble();
        double percentage = root.path("percentage").asDouble();
        double rawScore = root.path("raw_score").asDouble();
        double rawScoreMax = root.path("raw_score_max").asDouble();
        double expectedRawScoreMax = expectedRubric.totalMaxScore();
        double expectedRawScore = Math.round(rubricScoreSum * 100.0) / 100.0;
        double expectedPercentage = Math.round(
                rubricScoreSum / expectedRawScoreMax * 10_000.0) / 100.0;
        return Double.isFinite(score)
                && Double.isFinite(overallScore)
                && Double.isFinite(percentage)
                && Double.isFinite(rawScore)
                && Double.isFinite(rawScoreMax)
                && Double.compare(rawScoreMax, expectedRawScoreMax) == 0
                && sameScoreValue(rawScore, expectedRawScore)
                && sameScoreValue(score, expectedPercentage)
                && sameScoreValue(overallScore, expectedPercentage)
                && sameScoreValue(percentage, expectedPercentage)
                && hasTrustedFindings(
                        root.path("strengths"),
                        WritingRubricCriterion.Polarity.STRENGTH,
                        expectedTaskType)
                && hasTrustedFindings(
                        root.path("needs_improvement"),
                        WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT,
                        expectedTaskType);
    }

    private static boolean hasTrustedFindings(
            JsonNode findings,
            WritingRubricCriterion.Polarity polarity,
            String taskType
    ) {
        if (!findings.isArray()) {
            return false;
        }
        for (JsonNode finding : findings) {
            WritingRubricCriterion criterion = WritingRubricCriterion.parse(
                    finding.path("criterionId").asText(null));
            WritingRubricCriterion.EvidenceScope scope = parseEvidenceScope(
                    finding.path("evidenceScope").asText(null));
            if (criterion == null
                    || criterion.polarity() != polarity
                    || !criterion.activeForProvider()
                    || !criterion.appliesTo(taskType)
                    || scope == null
                    || !criterion.supports(scope)
                    || scope == WritingRubricCriterion.EvidenceScope.TASK_METADATA
                    || !WritingDiagnosticContract.validProviderMetadata(
                            finding, criterion, taskType, scope)) {
                return false;
            }
            String evidence = finding.path("evidence").asText("");
            if ((scope == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && evidence.isBlank())
                    || (scope == WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER
                    && !evidence.isEmpty())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameScoreValue(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000_000_1;
    }

    /**
     * Deterministic spam/empty response. 0 provider calls.
     */
    public String spamResponse(String taskType, String learnerAnswer) {
        try {
            String effectiveTaskType = taskType == null ? "GENERAL" : taskType;
            double score = 0.0;
            double rawScore = 0.0;
            WritingScoringRubric scoringRubric = WritingScoringPolicy.rubricFor(effectiveTaskType);
            double rawMax = scoringRubric.totalMaxScore();

            List<Map<String, Object>> rubricScores = new ArrayList<>();
            for (WritingScoringCriterion criterion : scoringRubric.criteria()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("criterionId", criterion.criterionId());
                row.put("name", criterion.displayName());
                row.put("score", 0.0);
                row.put("maxScore", criterion.maxScore());
                row.put("feedback", "Bài làm không hợp lệ.");
                rubricScores.add(row);
            }

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("score", score);
            normalized.put("overall_score", score);
            normalized.put("percentage", score);
            normalized.put("raw_score", rawScore);
            normalized.put("raw_score_max", rawMax);
            normalized.put("scoring_contract",
                    WritingScoringPolicy.SCORING_CONTRACT);
            normalized.put("policy_bundle_id",
                    WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
            normalized.put("task_type", effectiveTaskType);
            String invalidSummary = "[INVALID_LEARNER_RESPONSE] Bài làm bỏ trống hoặc chưa có đủ dữ liệu tiếng Hàn để chấm.";
            normalized.put("summary", invalidSummary);
            normalized.put("summary_vi", invalidSummary);
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
            putEvaluationMetadata(normalized,
                    "INVALID_LEARNER_RESPONSE",
                    "BACKEND_RULE",
                    invalidLearnerReason(learnerAnswer),
                    false,
                    true);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return fallback(
                    "[INVALID_LEARNER_RESPONSE] Bài làm không hợp lệ.",
                    taskType);
        }
    }

    public String fallback(String reason) {
        return fallback(reason, "GENERAL");
    }

    public String fallback(String reason, String taskType) {
        return availabilityResult(
                "EVALUATION_UNAVAILABLE",
                "SYSTEM",
                "PROVIDER_UNEXPECTED_ERROR",
                true,
                reason,
                taskType,
                "");
    }

    public String providerUnavailable(String reason,
                                      String taskType,
                                      String learnerAnswer,
                                      boolean retryable) {
        return availabilityResult(
                "EVALUATION_UNAVAILABLE",
                "PROVIDER",
                reason,
                retryable,
                "Chưa có đánh giá AI khả dụng — vui lòng chấm lại.",
                taskType,
                learnerAnswer);
    }

    public String contractFailure(String reason, String taskType, String learnerAnswer) {
        return availabilityResult(
                "EVALUATION_CONTRACT_FAILED",
                "PROVIDER",
                reason,
                true,
                "Phản hồi AI không đúng định dạng chấm điểm — vui lòng chấm lại.",
                taskType,
                learnerAnswer);
    }

    private String availabilityResult(String status,
                                      String source,
                                      String reason,
                                      boolean retryable,
                                      String message,
                                      String taskType,
                                      String learnerAnswer) {
        try {
            String effectiveTaskType = taskType == null ? "GENERAL" : taskType;
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("task_type", effectiveTaskType);
            normalized.put("policy_bundle_id",
                    WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
            normalized.put("summary", message);
            normalized.put("summary_vi", message);
            normalized.put("rubric_scores", List.of());
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
            normalized.put("engine", "KSH_WRITING_EVALUATOR_STATUS");
            putEvaluationMetadata(normalized, status, source, reason, retryable, false);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return "{\"policy_bundle_id\":\"KSH_WRITING_POLICY_BUNDLE_V2\",\"evaluation_status\":\"EVALUATION_UNAVAILABLE\",\"evaluation_source\":\"SYSTEM\",\"evaluation_reason\":\"PROVIDER_UNEXPECTED_ERROR\",\"evaluation_retryable\":true,\"score_available\":false,\"summary_vi\":\"Chưa có đánh giá AI khả dụng.\"}";
        }
    }

    // ---- Scoring ----

    /** Derives the percentage from the authoritative task-native maxima. */
    static double deriveScoreFromRubrics(List<Map<String, Object>> rubricScores) {
        if (rubricScores == null || rubricScores.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        double max = 0;
        int count = 0;
        for (Map<String, Object> row : rubricScores) {
            Object scoreObj = row.get("score");
            if (scoreObj instanceof Number n) {
                sum += n.doubleValue();
                Object maxObj = row.get("maxScore");
                if (maxObj instanceof Number m) max += m.doubleValue();
                count++;
            }
        }
        if (count == 0) return 0.0;
        if (max > 0) return Math.round(sum / max * 10000.0) / 100.0;
        return 0.0;
    }

    private static double sumRubricScores(List<Map<String, Object>> rubricScores) {
        double sum = 0.0;
        for (Map<String, Object> row : rubricScores) {
            Object score = row.get("score");
            if (score instanceof Number number) {
                sum += number.doubleValue();
            }
        }
        return Math.round(sum * 100.0) / 100.0;
    }

    // ---- Rubric validation ----

    private static boolean hasUsableRubricContract(JsonNode array, String taskType) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            return false;
        }
        for (JsonNode node : array) {
            if (node.isObject()) {
                JsonNode score = node.get("score");
                if (score != null && score.isNumber()) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Map<String, Object>> normalizeRubricScores(JsonNode array, String taskType) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!array.isArray() || array.isEmpty()) {
            return rows;
        }
        var expected = WritingPromptRules.scoringCriteriaForTask(taskType);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonNode node : array) {
            String id = node.path("criterionId").asText();
            var criterion = expected.stream()
                    .filter(candidate -> candidate.criterionId().equals(id))
                    .findFirst()
                    .orElse(null);
            if (criterion == null || !seen.add(id)
                    || !node.path("maxScore").isNumber()
                    || Double.compare(
                            node.path("maxScore").asDouble(),
                            criterion.maxScore()) != 0
                    || !node.path("score").isNumber()
                    || !Double.isFinite(node.path("score").asDouble())
                    || node.path("score").asDouble() < 0
                    || node.path("score").asDouble() > criterion.maxScore()) {
                return List.of();
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("criterionId", id);
            row.put("name", criterion.displayName());
            row.put("score", node.path("score").asDouble());
            row.put("maxScore", criterion.maxScore());
            row.put("feedback", node.path("feedback").asText(""));
            rows.add(row);
        }
        return rows.size() == expected.size() ? rows : List.of();
    }


    // ---- Findings validation ----

    private List<Map<String, Object>> normalizeFindings(JsonNode array,
                                                        WritingRubricCriterion.Polarity polarity,
                                                        String studentText,
                                                        String taskType) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!array.isArray()) {
            return rows;
        }
        int index = 1;
        for (JsonNode node : array) {
            WritingRubricCriterion criterion = WritingRubricCriterion.parse(node.path("criterionId").asText(null));
            if (criterion == null || criterion.polarity() != polarity
                    || !criterion.activeForProvider() || !criterion.appliesTo(taskType)) {
                continue;
            }
            WritingRubricCriterion.EvidenceScope evidenceScope = parseEvidenceScope(
                    node.path("evidenceScope").asText(null));
            if (evidenceScope == null || !criterion.supports(evidenceScope)
                    || evidenceScope == WritingRubricCriterion.EvidenceScope.TASK_METADATA) {
                continue;
            }
            if (!WritingDiagnosticContract.validProviderMetadata(
                    node, criterion, taskType, evidenceScope)) {
                continue;
            }
            String evidence = node.path("evidence").asText("");
            String explanation = node.path("explanationVi").asText("").trim();
            String correction = node.path("correction").asText("").trim();
            if (explanation.isBlank()) {
                continue;
            }
            if (evidenceScope == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && (evidence.isBlank() || !studentText.contains(evidence))) {
                continue;
            }
            if (evidenceScope == WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER) {
                evidence = "";
            }
            if (polarity == WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT
                    && evidenceScope == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && correction.isBlank()) {
                continue;
            }
            if (polarity == WritingRubricCriterion.Polarity.STRENGTH) {
                correction = "";
            }

            // --- Enriched fields ---
            String category = WritingDiagnosticContract.categoryCode(criterion);
            String subtype = node.path("subtype").asText();
            JsonNode scoringCriterion = node.get("scoringCriterionId");
            String parentCriterionId = scoringCriterion == null
                    || scoringCriterion.isNull()
                    ? null
                    : scoringCriterion.asText();
            String impact = node.path("impact").asText();
            int frequency = node.path("frequency").intValue();
            double confidence = node.path("confidence").doubleValue();
            String observability = node.path("observability").asText();
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
            row.put("subtype", subtype);
            row.put("scoringCriterionId", parentCriterionId);
            row.put("evidenceScope", evidenceScope.name());
            row.put("category", category);
            row.put("subcategory", subtype);
            row.put("vietnameseLabel", criterion.vietnameseLabel());
            row.put("koreanLabel", criterion.koreanLabel());
            row.put("evidence", evidence);
            row.put("explanationVi", explanation);
            row.put("correction", correction);
            row.put("severity", impact);
            row.put("impact", impact);
            row.put("frequency", frequency);
            row.put("confidence", confidence);
            row.put("observability", observability);
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
            WritingRubricCriterion.EvidenceScope scope = parseEvidenceScope(
                    node.path("evidenceScope").asText(null));
            String evidence = node.path("evidence").asText("");
            if (scope == WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER
                    || (scope == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && !evidence.isBlank() && studentText.contains(evidence))) {
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
            row.putIfAbsent("evidenceScope", WritingRubricCriterion.EvidenceScope.TEXT_SPAN.name());
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
            if (!WritingRubricCriterion.EvidenceScope.TEXT_SPAN.name()
                    .equals(item.get("evidenceScope"))) {
                findingIndex++;
                continue;
            }
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

    private static WritingRubricCriterion.EvidenceScope parseEvidenceScope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WritingRubricCriterion.EvidenceScope.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ---- Helpers ----

    private static void putEvaluationMetadata(Map<String, Object> target,
                                              String status,
                                              String source,
                                              String reason,
                                              boolean retryable,
                                              boolean scoreAvailable) {
        target.put("evaluation_status", status);
        target.put("evaluation_source", source);
        target.put("evaluation_reason", reason);
        target.put("evaluation_retryable", retryable);
        target.put("score_available", scoreAvailable);
    }

    private static String invalidLearnerReason(String learnerAnswer) {
        if (learnerAnswer == null || learnerAnswer.trim().isEmpty()) {
            return "BLANK_ANSWER";
        }
        String trimmed = Normalizer.normalize(
                learnerAnswer.trim(), Normalizer.Form.NFC);
        boolean hasHangul = trimmed.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
        if (!hasHangul) {
            return "NO_HANGUL";
        }
        return "INVALID_LEARNER_RESPONSE";
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return value == null ? fallback : value;
    }
}
