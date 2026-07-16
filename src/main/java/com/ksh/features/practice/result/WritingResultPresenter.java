package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentScoreResult;
import com.ksh.features.practice.assessment.AssessmentScoreStatus;
import com.ksh.features.practice.assessment.AssessmentScoringEngine;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.ai.writing.WritingEvaluationResult;
import com.ksh.features.practice.ai.writing.WritingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.writing.WritingFeedbackViewMapper;
import com.ksh.features.practice.ai.writing.WritingScoringCriterion;
import com.ksh.features.practice.ai.writing.WritingScoringPolicy;
import com.ksh.features.practice.ai.writing.WritingScoringRubric;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnnotationView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFindingView;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.ResultEvaluationBand;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultRubricCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnalysisLens;
import com.ksh.features.practice.dto.PracticeDtos.WritingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingTaskResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
final class WritingResultPresenter implements PracticeResultPresenter {

    private final ObjectMapper objectMapper;
    private final WritingFeedbackViewMapper feedbackMapper;
    private final WritingFeedbackCompatibilityReader compatibilityReader;
    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver typeResolver;
    private final AssessmentScoringEngine scoringEngine;

    WritingResultPresenter(
            ObjectMapper objectMapper,
            WritingFeedbackViewMapper feedbackMapper,
            WritingFeedbackCompatibilityReader compatibilityReader,
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver typeResolver,
            AssessmentScoringEngine scoringEngine) {
        this.objectMapper = objectMapper;
        this.feedbackMapper = feedbackMapper;
        this.compatibilityReader = compatibilityReader;
        this.contractCodec = contractCodec;
        this.typeResolver = typeResolver;
        this.scoringEngine = scoringEngine;
    }

    @Override
    public boolean supports(String skill) {
        return "WRITING".equals(skill);
    }

    @Override
    public Presentation present(PracticeResultContext context) {
        String storedFeedback = context.attempt().getAiFeedbackJson();
        JsonNode feedbackRoot = readTree(storedFeedback);
        boolean malformedStoredFeedback = !blank(storedFeedback)
                && (feedbackRoot == null || !feedbackRoot.isObject());
        List<PracticeQuestionVersion> questions = context.snapshot().questions();
        List<WritingTaskResult> tasks = new ArrayList<>();
        int ready = 0;
        int notAnswered = 0;
        int pending = 0;
        int unscorable = 0;

        for (PracticeQuestionVersion question : questions) {
            String answer = context.answers().getOrDefault(String.valueOf(question.getQuestionId()), "");
            if (answer.isBlank()) {
                notAnswered++;
            }
            JsonNode feedbackNode = feedbackNode(feedbackRoot, question.getQuestionId(), questions.size() == 1);
            WritingTaskResult task = isEssay(question)
                    ? task(question, answer, feedbackNode, malformedStoredFeedback)
                    : historicalObjectiveTask(question, answer);
            tasks.add(task);
            if (!answer.isBlank()) {
                switch (task.feedback().state()) {
                    case "READY" -> ready++;
                    case "PENDING" -> pending++;
                    default -> unscorable++;
                }
            }
        }

        int answered = questions.size() - notAnswered;
        ResultFeedbackAvailability feedback = aggregateFeedback(
                ready, pending, unscorable, answered);
        ResultAnswerDistribution distribution = new ResultAnswerDistribution(
                0, 0, 0, notAnswered, pending, unscorable, questions.size(), ready);
        ResultScoreSummary displayScore = feedback.ready()
                ? context.score()
                : context.score().unavailableView();
        return new Presentation(displayScore, distribution, feedback, new WritingResultPayload(tasks));
    }

    private WritingTaskResult historicalObjectiveTask(
            PracticeQuestionVersion question,
            String learnerAnswer) {
        boolean answered = learnerAnswer != null && !learnerAnswer.isBlank();
        ResultScoreSummary score = unavailableObjectiveScore(question);
        ResultFeedbackAvailability availability;
        String summary = null;
        try {
            AssessmentScoreResult result = scoreObjective(question, learnerAnswer);
            if (!answered || result.status() == AssessmentScoreStatus.NOT_ANSWERED) {
                availability = new ResultFeedbackAvailability(
                        "UNAVAILABLE", "Chưa có câu trả lời để chấm", 0, 0);
            } else if (result.status() == AssessmentScoreStatus.PENDING_AI) {
                availability = new ResultFeedbackAvailability(
                        "PENDING", "Đang chờ đánh giá", 0, 1);
            } else {
                BigDecimal resultPercentage = percentage(result.earnedPoints(), result.possiblePoints());
                score = new ResultScoreSummary(
                        result.earnedPoints(),
                        result.earnedPoints(),
                        result.possiblePoints(),
                        resultPercentage,
                        "POINTS",
                        "Đáp án cố định của phiên bản đề",
                        objectiveStatusLabel(result.status()));
                availability = new ResultFeedbackAvailability(
                        "READY", "Đã chấm theo đáp án cố định", 1, 1);
                summary = "Kết quả được tính từ đáp án đã khóa cùng phiên bản đề của bài làm.";
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            availability = new ResultFeedbackAvailability(
                    "FAILED", "Không thể đọc đáp án của phiên bản đề cũ", 0, answered ? 1 : 0);
        }
        return new WritingTaskResult(
                question.getQuestionId(),
                question.getId(),
                question.getQuestionNo(),
                taskType(question),
                taskLabel(question.getQuestionNo(), taskType(question)),
                question.getPrompt(),
                learnerAnswer,
                score,
                availability,
                summary,
                List.of(),
                List.of());
    }

    private AssessmentScoreResult scoreObjective(
            PracticeQuestionVersion question,
            String rawAnswer) {
        CanonicalQuestionType type = typeResolver.resolve(question.getQuestionType());
        QuestionContent content = blank(question.getQuestionContentJson())
                ? contractCodec.adaptLegacyContent(question.getOptionsJson(), question.getQuestionType())
                : contractCodec.readQuestionContent(question.getQuestionContentJson(), type);
        AnswerSpec answerSpec = blank(question.getAnswerSpecJson())
                ? contractCodec.adaptLegacyAnswerSpec(question.getQuestionType(), question.getAnswerKey(), content)
                : contractCodec.readAnswerSpec(question.getAnswerSpecJson(), content);
        LearnerAnswer learnerAnswer = !blank(rawAnswer) && rawAnswer.trim().startsWith("{")
                ? contractCodec.readLearnerAnswer(rawAnswer)
                : contractCodec.adaptLegacyLearnerAnswer(question.getQuestionType(), rawAnswer, content);
        return scoringEngine.score(answerSpec, learnerAnswer, question.getPoints());
    }

    private static ResultScoreSummary unavailableObjectiveScore(PracticeQuestionVersion question) {
        return new ResultScoreSummary(
                null, null, question.getPoints(), null,
                "POINTS", "Đáp án cố định của phiên bản đề", null);
    }

    private boolean isEssay(PracticeQuestionVersion question) {
        try {
            return typeResolver.resolve(question.getQuestionType()) == CanonicalQuestionType.ESSAY;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private WritingTaskResult task(
            PracticeQuestionVersion question,
            String learnerAnswer,
            JsonNode feedbackNode,
            boolean malformedStoredFeedback) {
        String taskType = taskType(question);
        WritingScoringRubric rubric = WritingScoringPolicy.rubricFor(taskType);
        boolean answered = learnerAnswer != null && !learnerAnswer.isBlank();
        JsonNode usableFeedbackNode = answered ? feedbackNode : null;
        WritingFeedbackView feedback = feedbackMapper.map(usableFeedbackNode);
        WritingFeedbackCompatibilityReader.EntryResult contract =
                compatibilityReader.parseStoredEntry(usableFeedbackNode);
        WritingEvaluationResult evaluation = contract.value();
        boolean scoreContractReady = evaluation != null && evaluation.scoreAvailableFlag();
        List<ResultRubricCriterion> criteria = criteria(
                rubric, usableFeedbackNode, scoreContractReady);
        ResultScoreSummary score = taskScore(evaluation, criteria, rubric);
        ResultFeedbackAvailability availability = taskFeedback(
                answered, malformedStoredFeedback, feedback, usableFeedbackNode, contract, score);
        List<WritingAnalysisLens> lenses = isCloze(taskType)
                ? List.of()
                : longFormLenses(criteria, availability.ready() ? feedback : null);

        return new WritingTaskResult(
                question.getQuestionId(),
                question.getId(),
                question.getQuestionNo(),
                taskType,
                taskLabel(question.getQuestionNo(), taskType),
                question.getPrompt(),
                learnerAnswer,
                score,
                availability,
                availability.ready()
                        ? firstPresent(feedback == null ? null : feedback.summaryVi(),
                                feedback == null ? null : feedback.summary())
                        : null,
                criteria,
                lenses);
    }

    private List<ResultRubricCriterion> criteria(
            WritingScoringRubric rubric,
            JsonNode feedbackNode,
            boolean scoreContractReady) {
        JsonNode rows = feedbackNode == null ? null : feedbackNode.path("rubric_scores");
        List<ResultRubricCriterion> result = new ArrayList<>();
        for (int index = 0; index < rubric.criteria().size(); index++) {
            WritingScoringCriterion expected = rubric.criteria().get(index);
            JsonNode stored = scoreContractReady ? findCriterion(rows, expected, index) : null;
            BigDecimal score = decimal(stored, "score");
            BigDecimal maxScore = decimal(stored, "maxScore");
            if (maxScore == null) {
                maxScore = decimal(stored, "max_score");
            }
            if (maxScore == null) {
                maxScore = BigDecimal.valueOf(expected.maxScore());
            }
            BigDecimal percentage = percentage(score, maxScore);
            result.add(new ResultRubricCriterion(
                    expected.criterionId(),
                    expected.displayName(),
                    score,
                    maxScore,
                    percentage,
                    ResultEvaluationBand.fromPercentage(percentage),
                    text(stored, "feedback")));
        }
        return List.copyOf(result);
    }

    private static ResultScoreSummary taskScore(
            WritingEvaluationResult evaluation,
            List<ResultRubricCriterion> criteria,
            WritingScoringRubric rubric) {
        if (evaluation == null || !evaluation.scoreAvailableFlag()) {
            return new ResultScoreSummary(null, null, null, null,
                    "POINTS", "Thang điểm " + rubric.totalMaxScore(), null);
        }
        boolean complete = !criteria.isEmpty() && criteria.stream()
                .allMatch(row -> row.score() != null && row.maxScore() != null);
        BigDecimal earned = complete
                ? criteria.stream().map(ResultRubricCriterion::score)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : evaluation.rawScore();
        BigDecimal possible = complete
                ? criteria.stream().map(ResultRubricCriterion::maxScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : evaluation.rawScoreMax();
        BigDecimal percentage = percentage(earned, possible);
        return new ResultScoreSummary(
                earned,
                earned,
                possible,
                percentage,
                "POINTS",
                "Thang điểm " + rubric.totalMaxScore(),
                null);
    }

    private static List<WritingAnalysisLens> longFormLenses(
            List<ResultRubricCriterion> criteria,
            WritingFeedbackView feedback) {
        if (criteria.size() < 3) {
            return List.of();
        }
        ResultRubricCriterion content = criteria.get(0);
        ResultRubricCriterion structure = criteria.get(1);
        ResultRubricCriterion language = criteria.get(2);
        return List.of(
                lens("CONTENT", "Nhiệm vụ và Nội dung", content, feedback,
                        Set.of("CONTENT", "TASK", "CONTEXT"), true),
                lens("STRUCTURE", "Cấu trúc và mạch lạc", structure, feedback,
                        Set.of("ORGANIZATION", "COHERENCE", "STRUCTURE"), true),
                lens("VOCABULARY", "Từ vựng và Diễn đạt", language, feedback,
                        Set.of("VOCABULARY", "EXPRESSION", "LEXICAL"), false),
                lens("GRAMMAR", "Ngữ pháp và Độ chính xác", language, feedback,
                        Set.of("GRAMMAR", "SYNTAX", "SPELLING", "SPACING", "ACCURACY"), false));
    }

    private static WritingAnalysisLens lens(
            String code,
            String label,
            ResultRubricCriterion source,
            WritingFeedbackView feedback,
            Set<String> categories,
            boolean countedSeparately) {
        List<String> evidence = evidence(
                feedback, source.criterionId(), categories, countedSeparately);
        boolean hasLensEvidence = countedSeparately || !evidence.isEmpty();
        return new WritingAnalysisLens(
                code,
                label,
                source.criterionId(),
                hasLensEvidence ? source.score() : null,
                hasLensEvidence ? source.maxScore() : null,
                hasLensEvidence ? source.percentage() : null,
                hasLensEvidence ? source.band() : ResultEvaluationBand.UNAVAILABLE,
                countedSeparately
                        ? source.feedback()
                        : evidence.stream().findFirst().orElse(null),
                evidence,
                countedSeparately);
    }

    private static List<String> evidence(
            WritingFeedbackView feedback,
            String sourceCriterionId,
            Set<String> categories,
            boolean allowCriterionFallback) {
        if (feedback == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (WritingFindingView finding : concat(feedback.strengths(), feedback.needsImprovement())) {
            if (matches(finding.criterionId(), finding.category(), sourceCriterionId,
                    categories, allowCriterionFallback)) {
                addPresent(result, finding.explanationVi());
                addPresent(result, finding.correction());
            }
        }
        for (WritingAnnotationView annotation : feedback.annotations()) {
            if (matches(annotation.criterionId(), annotation.category(), sourceCriterionId,
                    categories, allowCriterionFallback)) {
                addPresent(result, annotation.explanationVi());
                addPresent(result, annotation.correction());
            }
        }
        return List.copyOf(result);
    }

    private static boolean matches(
            String criterionId,
            String category,
            String sourceCriterionId,
            Set<String> categories,
            boolean allowCriterionFallback) {
        String normalizedCategory = normalize(category);
        if (categories.stream().anyMatch(normalizedCategory::contains)) {
            return true;
        }
        return allowCriterionFallback
                && sourceCriterionId != null
                && sourceCriterionId.equals(criterionId);
    }

    private ResultFeedbackAvailability taskFeedback(
            boolean answered,
            boolean malformedStoredFeedback,
            WritingFeedbackView feedback,
            JsonNode feedbackNode,
            WritingFeedbackCompatibilityReader.EntryResult contract,
            ResultScoreSummary score) {
        if (!answered) {
            return new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Chưa có bài viết để đánh giá", 0, 0);
        }
        if (malformedStoredFeedback) {
            return new ResultFeedbackAvailability(
                    "FAILED", "Dữ liệu đánh giá bài viết không hợp lệ", 0, 1);
        }
        if (feedbackNode == null || !feedbackNode.isObject()) {
            return new ResultFeedbackAvailability("PENDING", "Đang chờ đánh giá", 0, 1);
        }
        String status = feedback == null ? null : feedback.evaluationStatus();
        if (status != null && (status.contains("PENDING") || status.contains("QUEUED")
                || status.contains("PROCESSING"))) {
            return new ResultFeedbackAvailability("PENDING", "Đang xử lý đánh giá", 0, 1);
        }
        if (contract.value() != null && contract.value().scoreAvailableFlag() && score.available()) {
            return new ResultFeedbackAvailability("READY", "Đã có đánh giá", 1, 1);
        }
        return new ResultFeedbackAvailability("FAILED", "Chưa có đánh giá khả dụng", 0, 1);
    }

    private static ResultFeedbackAvailability aggregateFeedback(
            int ready,
            int pending,
            int failed,
            int total) {
        if (total == 0) {
            return new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Không có bài viết đã trả lời để đánh giá", 0, 0);
        }
        if (ready == total) {
            return new ResultFeedbackAvailability("READY", "Đã có đánh giá cho toàn bộ bài viết", ready, total);
        }
        if (ready > 0) {
            return new ResultFeedbackAvailability("PARTIAL", "Một phần đánh giá đã sẵn sàng", ready, total);
        }
        if (pending > 0) {
            return new ResultFeedbackAvailability(
                    "PENDING", "Đánh giá bài viết đang được xử lý", 0, total);
        }
        if (failed > 0) {
            return new ResultFeedbackAvailability(
                    "FAILED", "Chưa có đánh giá bài viết khả dụng", 0, total);
        }
        return new ResultFeedbackAvailability(
                "UNAVAILABLE", "Chưa có dữ liệu đánh giá bài viết", 0, total);
    }

    private JsonNode feedbackNode(JsonNode root, Long questionId, boolean singleQuestion) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode candidate = root.get(String.valueOf(questionId));
        if (candidate != null && candidate.isTextual()) {
            candidate = readTree(candidate.asText());
        }
        if (candidate != null && candidate.isObject()) {
            return candidate;
        }
        return singleQuestion && root.has("rubric_scores") ? root : null;
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

    private static JsonNode findCriterion(JsonNode rows, WritingScoringCriterion expected, int fallbackIndex) {
        if (rows == null || !rows.isArray()) {
            return null;
        }
        for (JsonNode row : rows) {
            if (expected.criterionId().equals(text(row, "criterionId"))
                    || expected.criterionId().equals(text(row, "criterion_id"))) {
                return row;
            }
        }
        return fallbackIndex < rows.size() ? rows.get(fallbackIndex) : null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText().trim()
                : null;
    }

    private static BigDecimal percentage(BigDecimal score, BigDecimal maxScore) {
        return score == null || maxScore == null || maxScore.signum() <= 0
                ? null
                : score.multiply(BigDecimal.valueOf(100))
                        .divide(maxScore, 2, RoundingMode.HALF_UP);
    }

    private static boolean isCloze(String taskType) {
        return "Q51".equals(taskType) || "Q52".equals(taskType);
    }

    private static String taskLabel(Integer questionNo, String taskType) {
        return questionNo == null ? "Bài viết " + taskType : "Câu " + questionNo;
    }

    private static String taskType(PracticeQuestionVersion question) {
        return question.getWritingTaskType() == null
                ? "GENERAL"
                : question.getWritingTaskType().name();
    }

    private static String objectiveStatusLabel(AssessmentScoreStatus status) {
        return switch (status) {
            case CORRECT -> "Đúng";
            case PARTIALLY_CORRECT -> "Đúng một phần";
            case INCORRECT -> "Chưa đúng";
            case NOT_ANSWERED -> "Chưa trả lời";
            case PENDING_AI -> "Đang chờ đánh giá";
        };
    }

    private static String firstPresent(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void addPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    private static List<WritingFindingView> concat(
            List<WritingFindingView> first,
            List<WritingFindingView> second) {
        List<WritingFindingView> values = new ArrayList<>(first);
        values.addAll(second);
        return values;
    }
}
