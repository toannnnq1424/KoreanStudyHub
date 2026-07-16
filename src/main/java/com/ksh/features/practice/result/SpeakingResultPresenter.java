package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult;
import com.ksh.features.practice.ai.speaking.SpeakingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.writing.WritingFeedbackViewMapper;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.ResultEvaluationBand;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingActionPlanView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingCriterionResult;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFindingView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
final class SpeakingResultPresenter implements PracticeResultPresenter {

    private static final String CONTRACT_FIELD = "_contract";
    private static final String AI_CONTRACT = "speaking_ai_v1";
    private static final String MIXED_CONTRACT = "speaking_mixed_v1";
    private static final String FEEDBACK_BY_QUESTION = "speaking_feedback_by_question";
    private static final String ESSAY_FEEDBACK_BY_QUESTION = "essay_feedback_by_question";

    private final ObjectMapper objectMapper;
    private final SpeakingFeedbackCompatibilityReader feedbackReader;
    private final WritingFeedbackCompatibilityReader writingFeedbackReader;
    private final WritingFeedbackViewMapper writingFeedbackMapper;

    SpeakingResultPresenter(
            ObjectMapper objectMapper,
            SpeakingFeedbackCompatibilityReader feedbackReader,
            WritingFeedbackCompatibilityReader writingFeedbackReader,
            WritingFeedbackViewMapper writingFeedbackMapper) {
        this.objectMapper = objectMapper;
        this.feedbackReader = feedbackReader;
        this.writingFeedbackReader = writingFeedbackReader;
        this.writingFeedbackMapper = writingFeedbackMapper;
    }

    @Override
    public boolean supports(String skill) {
        return "SPEAKING".equals(skill);
    }

    @Override
    public Presentation present(PracticeResultContext context) {
        List<PracticeQuestionVersion> questions = context.snapshot().questions().stream()
                .filter(question -> "SPEAKING".equals(question.getQuestionType())
                        || "ESSAY".equals(question.getQuestionType()))
                .toList();
        String storedFeedback = context.attempt().getAiFeedbackJson();
        JsonNode root = readTree(storedFeedback);
        boolean malformedStoredFeedback = storedFeedback != null
                && !storedFeedback.isBlank()
                && (root == null || !root.isObject());
        boolean unsupportedContract = hasUnsupportedContract(root);
        List<SegmentFeedback> segments = new ArrayList<>();
        List<LegacyEssayFeedback> legacyEssayFeedbacks = new ArrayList<>();
        long legacyEssayQuestionCount = questions.stream()
                .filter(question -> "ESSAY".equals(question.getQuestionType()))
                .count();
        int notAnswered = 0;
        int pending = 0;
        int unscorable = 0;

        for (PracticeQuestionVersion question : questions) {
            String answer = context.answers().getOrDefault(String.valueOf(question.getQuestionId()), "");
            if (answer.isBlank()) {
                notAnswered++;
                continue;
            }
            if (malformedStoredFeedback || unsupportedContract) {
                unscorable++;
                continue;
            }
            if ("ESSAY".equals(question.getQuestionType())) {
                JsonNode node = legacyEssayFeedbackNode(
                        root, question.getQuestionId(), legacyEssayQuestionCount == 1);
                WritingFeedbackView feedback = writingFeedbackMapper.map(node);
                WritingFeedbackCompatibilityReader.EntryResult contract =
                        writingFeedbackReader.parseStoredEntry(node);
                switch (legacyEssayFeedbackState(node, feedback, contract)) {
                    case "READY" -> legacyEssayFeedbacks.add(new LegacyEssayFeedback(feedback));
                    case "PENDING" -> pending++;
                    default -> unscorable++;
                }
                continue;
            }
            JsonNode node = feedbackNode(root, question.getQuestionId(), questions.size() == 1);
            SpeakingEvaluationResult feedback = node == null ? null : feedbackReader.read(node);
            switch (feedbackState(node, feedback)) {
                case "READY" -> segments.add(new SegmentFeedback(question.getQuestionId(), feedback));
                case "PENDING" -> pending++;
                default -> unscorable++;
            }
        }

        List<SpeakingCriterionResult> criteria = criteria(segments, questions.size(), false);
        int coveredSpeakingSegments = (int) segments.stream()
                .filter(segment -> segment.feedback().scoreAvailable()
                        && !segment.feedback().rubricScores().isEmpty())
                .count();
        int coveredSegments = coveredSpeakingSegments + legacyEssayFeedbacks.size();
        int answered = questions.size() - notAnswered;
        ResultFeedbackAvailability feedback = feedbackAvailability(
                coveredSegments, pending, unscorable, answered);
        ResultAnswerDistribution distribution = new ResultAnswerDistribution(
                0, 0, 0, notAnswered, pending, unscorable, questions.size(), coveredSegments);
        ResultScoreSummary displayScore = feedback.ready()
                ? context.score()
                : context.score().unavailableView();

        SpeakingResultPayload payload = new SpeakingResultPayload(
                displayScore,
                coveredSegments,
                questions.size(),
                "TRANSCRIPT_ONLY",
                "Độ lưu loát và phát âm chỉ mang tính tham khảo vì evaluator chưa nhận bằng chứng âm thanh.",
                mergeUnique(uniqueText(segments, TextKind.SUMMARY),
                        legacyEssayText(legacyEssayFeedbacks, TextKind.SUMMARY)),
                mergeUnique(uniqueText(segments, TextKind.STRENGTH),
                        legacyEssayText(legacyEssayFeedbacks, TextKind.STRENGTH)),
                mergeUnique(uniqueText(segments, TextKind.NEED),
                        legacyEssayText(legacyEssayFeedbacks, TextKind.NEED)),
                actionPlan(segments),
                criteria);
        return new Presentation(displayScore, distribution, feedback, payload);
    }

    private static List<SpeakingCriterionResult> criteria(
            List<SegmentFeedback> segments,
            int totalSegments,
            boolean audioBacked) {
        Map<SpeakingRubricCriterion, List<CriterionEvidence>> evidence =
                new EnumMap<>(SpeakingRubricCriterion.class);
        for (SpeakingRubricCriterion criterion : SpeakingRubricCriterion.values()) {
            evidence.put(criterion, new ArrayList<>());
        }
        for (SegmentFeedback segment : segments) {
            if (!segment.feedback().scoreAvailable()) {
                continue;
            }
            for (SpeakingEvaluationResult.RubricScore row : segment.feedback().rubricScores()) {
                if (row.criterion() == null || row.score() == null || row.maxScore() == null
                        || row.maxScore().signum() <= 0) {
                    continue;
                }
                evidence.get(row.criterion()).add(new CriterionEvidence(
                        normalizedWeightedScore(row, row.criterion()),
                        firstPresent(row.feedback(), criterionSummary(segment.feedback(), row.criterion()))));
            }
        }

        List<SpeakingCriterionResult> result = new ArrayList<>();
        for (SpeakingRubricCriterion criterion : SpeakingRubricCriterion.values()) {
            List<CriterionEvidence> rows = evidence.get(criterion);
            BigDecimal score = rows.isEmpty()
                    ? null
                    : rows.stream().map(CriterionEvidence::weightedScore)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
            BigDecimal percentage = score == null
                    ? null
                    : score.multiply(BigDecimal.valueOf(100))
                            .divide(criterion.maxScore(), 2, RoundingMode.HALF_UP);
            boolean advisory = !audioBacked
                    && (criterion == SpeakingRubricCriterion.FLUENCY
                    || criterion == SpeakingRubricCriterion.PRONUNCIATION_DELIVERY);
            result.add(new SpeakingCriterionResult(
                    criterion.id(),
                    criterionLabel(criterion),
                    criterion.maxScore(),
                    score,
                    percentage,
                    rows.size(),
                    totalSegments,
                    ResultEvaluationBand.fromPercentage(percentage),
                    rows.stream().map(CriterionEvidence::summary)
                            .filter(SpeakingResultPresenter::present)
                            .findFirst().orElse(null),
                    advisory));
        }
        return List.copyOf(result);
    }

    private static BigDecimal normalizedWeightedScore(
            SpeakingEvaluationResult.RubricScore row,
            SpeakingRubricCriterion criterion) {
        return row.score().multiply(criterion.maxScore())
                .divide(row.maxScore(), 4, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(criterion.maxScore());
    }

    private static String criterionSummary(
            SpeakingEvaluationResult feedback,
            SpeakingRubricCriterion criterion) {
        return feedback.criterionFeedback().stream()
                .filter(item -> criterion == item.criterion())
                .map(SpeakingEvaluationResult.CriterionFeedback::summary)
                .filter(SpeakingResultPresenter::present)
                .findFirst()
                .orElse(null);
    }

    private static List<String> uniqueText(List<SegmentFeedback> segments, TextKind kind) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (SegmentFeedback segment : segments) {
            SpeakingEvaluationResult feedback = segment.feedback();
            switch (kind) {
                case SUMMARY -> add(values, feedback.overallSummary());
                case STRENGTH -> {
                    feedback.majorStrengths().forEach(value -> add(values, value));
                    feedback.strengths().forEach(item -> add(values, item.explanationVi()));
                }
                case NEED -> {
                    feedback.majorNeedsImprovement().forEach(value -> add(values, value));
                    feedback.needsImprovement().forEach(item -> add(values, item.explanationVi()));
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<String> legacyEssayText(
            List<LegacyEssayFeedback> feedbacks,
            TextKind kind) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (LegacyEssayFeedback legacy : feedbacks) {
            WritingFeedbackView feedback = legacy.feedback();
            switch (kind) {
                case SUMMARY -> add(values, firstPresent(feedback.summaryVi(), feedback.summary()));
                case STRENGTH -> feedback.strengths().stream()
                        .map(SpeakingResultPresenter::findingText)
                        .forEach(value -> add(values, value));
                case NEED -> feedback.needsImprovement().stream()
                        .map(SpeakingResultPresenter::findingText)
                        .forEach(value -> add(values, value));
            }
        }
        return List.copyOf(values);
    }

    private static String findingText(WritingFindingView finding) {
        return firstPresent(finding.explanationVi(), finding.correction());
    }

    private static List<String> mergeUnique(List<String> first, List<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    private static List<SpeakingActionPlanView> actionPlan(List<SegmentFeedback> segments) {
        Map<String, SpeakingActionPlanView> unique = new LinkedHashMap<>();
        for (SegmentFeedback segment : segments) {
            for (SpeakingEvaluationResult.ActionPlanItem item : segment.feedback().actionPlan()) {
                if (!present(item.title()) && !present(item.instruction())) {
                    continue;
                }
                SpeakingActionPlanView view = new SpeakingActionPlanView(
                        item.criterion() == null ? null : item.criterion().id(),
                        item.subCriterionId(),
                        item.title(),
                        item.instruction(),
                        item.reason(),
                        item.priority());
                String key = String.join("|",
                        normalizeKey(item.title()),
                        normalizeKey(item.instruction()),
                        normalizeKey(item.subCriterionId()));
                unique.putIfAbsent(key, view);
            }
        }
        return unique.values().stream().limit(6).toList();
    }

    private static ResultFeedbackAvailability feedbackAvailability(
            int ready,
            int pending,
            int failed,
            int total) {
        if (total == 0) {
            return new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Không có phần trả lời nói để đánh giá", 0, 0);
        }
        if (ready == total) {
            return new ResultFeedbackAvailability("READY", "Đánh giá tổng quan đã sẵn sàng", ready, total);
        }
        if (ready > 0) {
            return new ResultFeedbackAvailability("PARTIAL", "Một phần bằng chứng đánh giá đã sẵn sàng", ready, total);
        }
        if (pending > 0) {
            return new ResultFeedbackAvailability(
                    "PENDING", "Đánh giá bài nói đang được xử lý", 0, total);
        }
        if (failed > 0) {
            return new ResultFeedbackAvailability(
                    "FAILED", "Chưa có đánh giá bài nói khả dụng", 0, total);
        }
        return new ResultFeedbackAvailability(
                "UNAVAILABLE", "Chưa có dữ liệu đánh giá bài nói", 0, total);
    }

    private static String feedbackState(JsonNode node, SpeakingEvaluationResult feedback) {
        if (node == null || !node.isObject()) {
            return "PENDING";
        }
        String rawStatus = firstPresent(text(node, "evaluationStatus"),
                text(node, "evaluation_status"));
        String normalized = rawStatus == null
                ? ""
                : rawStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("PENDING") || normalized.contains("QUEUED")
                || normalized.contains("PROCESSING")) {
            return "PENDING";
        }
        return feedback != null
                && feedback.scoreAvailable()
                && !feedback.rubricScores().isEmpty()
                ? "READY"
                : "FAILED";
    }

    private static String legacyEssayFeedbackState(
            JsonNode node,
            WritingFeedbackView feedback,
            WritingFeedbackCompatibilityReader.EntryResult contract) {
        if (node == null || !node.isObject()) {
            return "PENDING";
        }
        String rawStatus = feedback == null ? null : feedback.evaluationStatus();
        String normalized = rawStatus == null
                ? ""
                : rawStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("PENDING") || normalized.contains("QUEUED")
                || normalized.contains("PROCESSING")) {
            return "PENDING";
        }
        if (feedback != null && Boolean.FALSE.equals(feedback.scoreAvailable())) {
            return "FAILED";
        }
        if (contract.value() != null && contract.value().scoreAvailableFlag()) {
            return "READY";
        }
        return hasLegacyScore(node) ? "READY" : "FAILED";
    }

    private JsonNode feedbackNode(JsonNode root, Long questionId, boolean singleQuestion) {
        if (root == null || !root.isObject()) {
            return null;
        }
        String contract = text(root, CONTRACT_FIELD);
        if (contract != null && !AI_CONTRACT.equals(contract) && !MIXED_CONTRACT.equals(contract)) {
            return null;
        }
        JsonNode byQuestion = root.path(FEEDBACK_BY_QUESTION);
        JsonNode candidate = byQuestion.isObject()
                ? byQuestion.get(String.valueOf(questionId))
                : root.get(String.valueOf(questionId));
        if (candidate != null && candidate.isTextual()) {
            candidate = readTree(candidate.asText());
        }
        if (candidate != null && candidate.isObject()) {
            return candidate;
        }
        return singleQuestion && hasStoredFeedback(root) ? root : null;
    }

    private JsonNode legacyEssayFeedbackNode(JsonNode root, Long questionId, boolean singleEssay) {
        if (root == null || !root.isObject()) {
            return null;
        }
        String contract = text(root, CONTRACT_FIELD);
        if (contract != null && !MIXED_CONTRACT.equals(contract)) {
            return null;
        }
        if (MIXED_CONTRACT.equals(contract)) {
            JsonNode candidate = root.path(ESSAY_FEEDBACK_BY_QUESTION)
                    .get(String.valueOf(questionId));
            return candidate != null && candidate.isObject() ? candidate : null;
        }
        JsonNode candidate = root.get(String.valueOf(questionId));
        if (candidate != null && candidate.isTextual()) {
            candidate = readTree(candidate.asText());
        }
        if (candidate != null && candidate.isObject()) {
            return candidate;
        }
        return singleEssay ? root : null;
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean hasStoredFeedback(JsonNode node) {
        return node != null && node.isObject() && (node.has("rubric_scores")
                || node.has("evaluation_status")
                || node.has("evaluationStatus")
                || node.has("overall_summary")
                || node.has("overallSummary")
                || node.has("summary")
                || node.has("summary_vi"));
    }

    private static boolean hasUnsupportedContract(JsonNode root) {
        String contract = text(root, CONTRACT_FIELD);
        return contract != null && !AI_CONTRACT.equals(contract) && !MIXED_CONTRACT.equals(contract);
    }

    private static boolean hasLegacyScore(JsonNode node) {
        return number(node, "score") || number(node, "overall_score")
                || number(node, "percentage");
    }

    private static boolean number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText()
                : null;
    }

    private static String criterionLabel(SpeakingRubricCriterion criterion) {
        return switch (criterion) {
            case CONTENT_TASK_FULFILLMENT -> "Nội dung và hoàn thành yêu cầu";
            case GRAMMAR_SENTENCE_CONTROL -> "Ngữ pháp và kiểm soát câu";
            case VOCABULARY_EXPRESSIONS -> "Từ vựng và biểu đạt";
            case COHERENCE_ORGANIZATION -> "Mạch lạc và tổ chức ý";
            case FLUENCY -> "Độ lưu loát";
            case PRONUNCIATION_DELIVERY -> "Phát âm và thể hiện";
        };
    }

    private static String firstPresent(String first, String second) {
        return present(first) ? first : second;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static void add(Set<String> values, String value) {
        if (present(value)) {
            values.add(value.trim());
        }
    }

    private static String normalizeKey(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private record SegmentFeedback(Long questionId, SpeakingEvaluationResult feedback) {
    }

    private record LegacyEssayFeedback(WritingFeedbackView feedback) {
    }

    private record CriterionEvidence(BigDecimal weightedScore, String summary) {
    }

    private enum TextKind {
        SUMMARY,
        STRENGTH,
        NEED
    }
}
