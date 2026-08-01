package com.ksh.features.practice.ai.writing;

import java.util.ArrayList;
import java.util.List;

/**
 * Backend-owned task-native score anchors.
 *
 * <p>Every integer point value is explicit. This preserves the established
 * Q53/Q54 weights while preventing a provider from inventing an unanchored
 * fractional or band score.</p>
 */
public final class WritingScoreAnchorPolicy {

    public static final String VERSION = "writing-score-anchors-v1";

    private WritingScoreAnchorPolicy() {
    }

    public static List<ScoreAnchor> anchors(
            WritingScoringCriterion criterion) {
        List<ScoreAnchor> anchors = new ArrayList<>();
        for (int score = 0; score <= criterion.maxScore(); score++) {
            anchors.add(new ScoreAnchor(
                    score,
                    label(score, criterion.maxScore()),
                    description(criterion, score),
                    performanceLevel(score, criterion.maxScore())));
        }
        return List.copyOf(anchors);
    }

    public static ScoreAnchor requireAnchor(
            WritingScoringCriterion criterion,
            int score) {
        return anchors(criterion).stream()
                .filter(anchor -> anchor.score() == score)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Writing score is outside the task-native anchor set"));
    }

    /**
     * Resolves a backend-owned display level for an aggregate task score.
     *
     * <p>The provider never owns this value. The same threshold policy used
     * by criterion anchors is applied only after the aggregate score has
     * passed task-native verification.</p>
     */
    public static PerformanceLevel requirePerformanceLevel(
            int score,
            int maxScore) {
        if (maxScore <= 0 || score < 0 || score > maxScore) {
            throw new IllegalArgumentException(
                    "Writing aggregate score is outside the task-native anchor set");
        }
        return performanceLevel(score, maxScore);
    }

    private static String label(int score, int maxScore) {
        if (score == maxScore) {
            return "Đáp ứng đầy đủ";
        }
        if (score == 0) {
            return "Chưa có bằng chứng đáp ứng";
        }
        double ratio = (double) score / maxScore;
        if (ratio >= 0.8) {
            return "Đáp ứng tốt";
        }
        if (ratio >= 0.6) {
            return "Đáp ứng phần lớn";
        }
        if (ratio >= 0.4) {
            return "Đáp ứng một phần";
        }
        return "Đáp ứng hạn chế";
    }

    private static String description(
            WritingScoringCriterion criterion,
            int score) {
        return criterion.displayName()
                + ": "
                + score
                + "/"
                + criterion.maxScore()
                + " — "
                + label(score, criterion.maxScore())
                + "; điểm này chỉ hợp lệ khi các dẫn chứng, phát hiện và yêu cầu "
                + "được backend đối chiếu không mâu thuẫn.";
    }

    private static PerformanceLevel performanceLevel(
            int score,
            int maxScore) {
        if (score == maxScore) {
            return PerformanceLevel.EXCELLENT;
        }
        double ratio = maxScore == 0 ? 0.0 : (double) score / maxScore;
        if (ratio >= 0.8) {
            return PerformanceLevel.GOOD;
        }
        if (ratio >= 0.4) {
            return PerformanceLevel.MODEST;
        }
        return PerformanceLevel.LIMITED;
    }

    public enum PerformanceLevel {
        LIMITED("Hạn chế", "제한적"),
        MODEST("Đang phát triển", "보통"),
        GOOD("Tốt", "좋음"),
        EXCELLENT("Xuất sắc", "우수");

        private final String labelVi;
        private final String labelKo;

        PerformanceLevel(String labelVi, String labelKo) {
            this.labelVi = labelVi;
            this.labelKo = labelKo;
        }

        public String labelVi() {
            return labelVi;
        }

        public String labelKo() {
            return labelKo;
        }
    }

    public record ScoreAnchor(
            int score,
            String labelVi,
            String descriptionVi,
            PerformanceLevel performanceLevel
    ) {
        public ScoreAnchor {
            if (score < 0
                    || labelVi == null || labelVi.isBlank()
                    || descriptionVi == null || descriptionVi.isBlank()
                    || performanceLevel == null) {
                throw new IllegalArgumentException(
                        "Writing score anchor is incomplete");
            }
        }
    }
}
