package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnnotationView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFindingView;
import com.ksh.features.practice.dto.PracticeDtos.WritingRubricScoreView;
import com.ksh.features.practice.dto.PracticeDtos.WritingSentenceRewriteView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class WritingFeedbackViewMapper {

    public WritingFeedbackView map(JsonNode feedbackEntry) {
        if (feedbackEntry == null || feedbackEntry.isNull() || feedbackEntry.isMissingNode() || !feedbackEntry.isObject()) {
            return null;
        }
        return new WritingFeedbackView(
                decimal(feedbackEntry.get("raw_score")),
                decimal(feedbackEntry.get("raw_score_max")),
                decimal(feedbackEntry.get("score")),
                text(feedbackEntry.get("summary")),
                text(feedbackEntry.get("summary_vi")),
                rubricScores(feedbackEntry.get("rubric_scores")),
                findings(feedbackEntry.get("strengths")),
                findings(feedbackEntry.get("needs_improvement")),
                annotations(feedbackEntry.get("annotations")),
                text(feedbackEntry.get("upgraded_answer")),
                sentenceRewrites(feedbackEntry.get("sentence_rewrites")),
                text(feedbackEntry.get("sample_answer")),
                text(feedbackEntry.get("evaluation_status")),
                text(feedbackEntry.get("evaluation_source")),
                text(feedbackEntry.get("evaluation_reason")),
                bool(feedbackEntry.get("evaluation_retryable")),
                bool(feedbackEntry.get("score_available"))
        );
    }

    private List<WritingRubricScoreView> rubricScores(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<WritingRubricScoreView> rows = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            rows.add(new WritingRubricScoreView(
                    text(item.get("name")),
                    decimal(item.get("score")),
                    text(item.get("feedback"))
            ));
        }
        return List.copyOf(rows);
    }

    private List<WritingFindingView> findings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<WritingFindingView> rows = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            String criterionId = text(item.get("criterionId"));
            WritingRubricCriterion criterion = WritingRubricCriterion.parse(criterionId);
            String category = firstPresent(text(item.get("category")),
                    criterion == null ? null : criterion.category().name());
            String vietnameseLabel = firstPresent(text(item.get("vietnameseLabel")),
                    criterion == null ? null : criterion.vietnameseLabel());
            rows.add(new WritingFindingView(
                    category,
                    vietnameseLabel,
                    firstPresent(text(item.get("uiLabel")), vietnameseLabel),
                    criterionId,
                    firstPresent(text(item.get("evidenceScope")), "TEXT_SPAN"),
                    text(item.get("evidence")),
                    text(item.get("explanationVi")),
                    text(item.get("correction")),
                    text(item.get("severity")),
                    text(item.get("errorType")),
                    text(item.get("whyItIsGood")),
                    text(item.get("topikTip"))
            ));
        }
        return List.copyOf(rows);
    }

    private List<WritingAnnotationView> annotations(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<WritingAnnotationView> rows = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            rows.add(new WritingAnnotationView(
                    text(item.get("id")),
                    text(item.get("kind")),
                    text(item.get("criterionId")),
                    text(item.get("category")),
                    integer(item.get("start")),
                    integer(item.get("end")),
                    text(item.get("severity")),
                    text(item.get("displayType")),
                    integer(item.get("index")),
                    text(item.get("explanationVi")),
                    text(item.get("correction")),
                    text(item.get("evidence"))
            ));
        }
        return List.copyOf(rows);
    }

    private List<WritingSentenceRewriteView> sentenceRewrites(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<WritingSentenceRewriteView> rows = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            rows.add(new WritingSentenceRewriteView(
                    text(item.get("original")),
                    text(item.get("upgraded")),
                    text(item.get("reason"))
            ));
        }
        return List.copyOf(rows);
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }

    private Integer integer(JsonNode node) {
        if (node == null || node.isNull() || !node.isIntegralNumber() || !node.canConvertToInt()) {
            return null;
        }
        return node.intValue();
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return node.asText();
    }

    private Boolean bool(JsonNode node) {
        if (node == null || node.isNull() || !node.isBoolean()) {
            return null;
        }
        return node.asBoolean();
    }

    private String firstPresent(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
