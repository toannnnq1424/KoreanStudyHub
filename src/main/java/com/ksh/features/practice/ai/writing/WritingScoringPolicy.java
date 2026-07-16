package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class WritingScoringPolicy {

    private WritingScoringPolicy() {
    }

    public static WritingScoringRubric rubricFor(String taskType) {
        String normalized = normalizeTaskType(taskType);
        return switch (normalized) {
            case "Q51", "Q52" -> new WritingScoringRubric(normalized, clozeCriteria());
            case "Q53" -> new WritingScoringRubric(normalized, essayCriteria(12, 9, 9));
            case "Q54" -> new WritingScoringRubric(normalized, essayCriteria(20, 15, 15));
            default -> new WritingScoringRubric("GENERAL", essayCriteria(40, 30, 30));
        };
    }

    public static BigDecimal percentage(BigDecimal earnedScore, BigDecimal totalMaxScore) {
        if (earnedScore == null || totalMaxScore == null
                || totalMaxScore.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal clamped = earnedScore.max(BigDecimal.ZERO).min(totalMaxScore);
        return clamped.multiply(BigDecimal.valueOf(100))
                .divide(totalMaxScore, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentageFromFeedback(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return WritingScoreMatrix.toHundredPointScale(1.0);
        }
        if (root.has("score_available") && !root.path("score_available").asBoolean()) {
            return BigDecimal.ZERO;
        }
        if (root.path("percentage").isNumber()) {
            return clampPercentage(BigDecimal.valueOf(root.path("percentage").asDouble()));
        }
        double score = root.path("score").asDouble(root.path("overall_score").asDouble(1.0));
        if (score <= 0.0) {
            return BigDecimal.ZERO;
        }
        if ("TASK_NATIVE_RUBRIC_V1".equals(root.path("scoring_contract").asText()) || score > 9.0) {
            return clampPercentage(BigDecimal.valueOf(score));
        }
        return WritingScoreMatrix.toHundredPointScale(score);
    }

    private static List<WritingScoringCriterion> clozeCriteria() {
        return List.of(
                criterion("W_CLOZE_BLANK_1_CONTEXT", "Ô 1 - Nội dung và ngữ cảnh", 2, 1),
                criterion("W_CLOZE_BLANK_1_GRAMMAR", "Ô 1 - Ngữ pháp và cấu trúc", 2, 2),
                criterion("W_CLOZE_BLANK_1_EXPRESSION", "Ô 1 - Biểu đạt và độ tự nhiên", 1, 3),
                criterion("W_CLOZE_BLANK_2_CONTEXT", "Ô 2 - Nội dung và ngữ cảnh", 2, 4),
                criterion("W_CLOZE_BLANK_2_GRAMMAR", "Ô 2 - Ngữ pháp và cấu trúc", 2, 5),
                criterion("W_CLOZE_BLANK_2_EXPRESSION", "Ô 2 - Biểu đạt và độ tự nhiên", 1, 6)
        );
    }

    private static List<WritingScoringCriterion> essayCriteria(int content, int organization, int language) {
        return List.of(
                criterion("W_CONTENT_TASK_ACHIEVEMENT", "Hoàn thành nhiệm vụ và Nội dung", content, 1),
                criterion("W_ORGANIZATION_COHERENCE", "Cấu trúc và Mạch lạc", organization, 2),
                criterion("W_LANGUAGE_EXPRESSION", "Ngôn ngữ và Biểu đạt", language, 3)
        );
    }

    private static WritingScoringCriterion criterion(String id, String name, int maxScore, int order) {
        return new WritingScoringCriterion(id, name, maxScore, order);
    }

    private static BigDecimal clampPercentage(BigDecimal percentage) {
        return percentage.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    private static String normalizeTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return "GENERAL";
        }
        if ("Q51_52".equals(taskType)) {
            return "Q51";
        }
        return taskType;
    }
}
