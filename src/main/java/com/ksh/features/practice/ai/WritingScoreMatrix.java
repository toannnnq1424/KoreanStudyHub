package com.ksh.features.practice.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

public final class WritingScoreMatrix {

    private WritingScoreMatrix() {
    }

    public static List<Map<String, Object>> bands() {
        return List.of(
                band(9.0, "Xuất sắc", "Bài viết kiểm soát gần như tự nhiên, đầy đủ nhiệm vụ, văn phong TOPIK rõ và ít lỗi."),
                band(8.0, "Rất tốt", "Bài viết mạnh, ý rõ, từ vựng phong phú, chỉ còn lỗi nhỏ."),
                band(7.0, "Tốt", "Hoàn thành nhiệm vụ rõ, bố cục ổn, còn một số lỗi không phá vỡ ý."),
                band(6.0, "Đạt", "Trả lời được đề, nhưng phát triển ý, liên kết hoặc ngôn ngữ còn hạn chế."),
                band(5.0, "Đang phát triển", "Nội dung cơ bản, còn nhiều lỗi từ vựng/ngữ pháp và ý chưa sâu."),
                band(4.0, "Hạn chế", "Ý chưa rõ hoặc thiếu phát triển; lỗi ngôn ngữ xuất hiện thường xuyên."),
                band(3.0, "Rất hạn chế", "Chỉ truyền đạt được một phần nhỏ; lỗi nghiêm trọng ở nhiều tiêu chí."),
                band(2.0, "Tối thiểu", "Rất ít tiếng Hàn sử dụng được cho nhiệm vụ."),
                band(1.0, "Không phản hồi", "Không có câu trả lời có ý nghĩa, gõ bừa hoặc lạc đề.")
        );
    }

    public static double clampAndRound(double score) {
        double clamped = Math.max(1.0, Math.min(9.0, score));
        return BigDecimal.valueOf(clamped * 2.0)
                .setScale(0, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(2.0), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static double backendScoreFromEvidence(double evaluatorScore, int strengthEvidence, int majorIssues) {
        double adjusted = evaluatorScore;
        if (strengthEvidence < 2) {
            adjusted -= 1.0;
        } else if (strengthEvidence <= 3) {
            adjusted -= 0.5;
        }
        if (majorIssues > 6) {
            adjusted -= 1.0;
        } else if (majorIssues >= 4) {
            adjusted -= 0.5;
        }
        return clampAndRound(adjusted);
    }

    public static double rawScoreMax(String taskType) {
        return switch (taskType == null ? "GENERAL" : taskType) {
            case "Q51_52" -> 10.0;
            case "Q53" -> 30.0;
            case "Q54" -> 50.0;
            default -> 100.0;
        };
    }

    public static double rawScoreFromNormalized(double normalizedScore, String taskType) {
        double max = rawScoreMax(taskType);
        return BigDecimal.valueOf(clampAndRound(normalizedScore))
                .multiply(BigDecimal.valueOf(max))
                .divide(BigDecimal.valueOf(9.0), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static BigDecimal toHundredPointScale(double score) {
        return BigDecimal.valueOf(clampAndRound(score))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(9), 2, RoundingMode.HALF_UP);
    }

    public static String bandLabel(double score) {
        double s = clampAndRound(score);
        if (s >= 9) return "Xuất sắc";
        if (s >= 8) return "Rất tốt";
        if (s >= 7) return "Tốt";
        if (s >= 6) return "Đạt";
        if (s >= 5) return "Đang phát triển";
        if (s >= 4) return "Hạn chế";
        if (s >= 3) return "Rất hạn chế";
        if (s >= 2) return "Tối thiểu";
        return "Không phản hồi";
    }

    private static Map<String, Object> band(double score, String label, String description) {
        return Map.of("score", score, "label", label, "description", description);
    }
}
