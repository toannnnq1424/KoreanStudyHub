package com.ksh.features.gradebook.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Immutable view model for the class gradebook; no gradebook table is required. */
public record GradebookView(List<Column> columns, List<StudentRow> students) {
    public record Column(String key, String kind, String title, BigDecimal maxScore) {
        public String displayMaxScore() {
            return maxScore == null ? "—" : maxScore.stripTrailingZeros().toPlainString();
        }
    }

    public record Cell(BigDecimal score, BigDecimal maxScore, BigDecimal normalizedTen) {
        public String displayScore() {
            return score == null ? "—" : score.stripTrailingZeros().toPlainString();
        }

        public String displayMaxScore() {
            return maxScore == null ? "—" : maxScore.stripTrailingZeros().toPlainString();
        }
    }

    public record StudentRow(Long userId, String name, String email, Map<String, Cell> cells) {}
}
