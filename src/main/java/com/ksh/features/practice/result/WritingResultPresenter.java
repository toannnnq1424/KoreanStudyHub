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
import com.ksh.features.practice.ai.writing.WritingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingScoringCriterion;
import com.ksh.features.practice.ai.writing.WritingScoringPolicy;
import com.ksh.features.practice.ai.writing.WritingScoringRubric;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnnotationView;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnswerArtifact;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticChip;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticFinding;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticGroup;
import com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFindingView;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPolarity;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailScoreCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultRubricCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnalysisLens;
import com.ksh.features.practice.dto.PracticeDtos.WritingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingSentenceRewriteView;
import com.ksh.features.practice.dto.PracticeDtos.WritingTaskResult;
import com.ksh.features.practice.dto.PracticeDtos.WritingUpgradeView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
final class WritingResultPresenter implements PracticeResultPresenter, PracticeResultDetailPresenter {

    private static final String CURRENT_SCORING_CONTRACT = "TASK_NATIVE_RUBRIC_V1";
    private static final String CURRENT_EVALUATION_ENGINE = "KSH_WRITING_EVALUATOR_V2";
    private static final Set<String> CURRENT_EVALUATION_SOURCES = Set.of("PROVIDER", "CACHE");

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
        int failed = 0;
        int unavailable = 0;

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
                    case "FAILED" -> failed++;
                    default -> unavailable++;
                }
            }
        }

        int answered = questions.size() - notAnswered;
        ResultFeedbackAvailability feedback = aggregateFeedback(
                ready, pending, failed, unavailable, answered);
        ResultAnswerDistribution distribution = new ResultAnswerDistribution(
                0, 0, 0, notAnswered, pending, failed + unavailable, questions.size(), ready);
        ResultScoreSummary displayScore = feedback.ready()
                ? context.score()
                : context.score().unavailableView();
        return new Presentation(displayScore, distribution, feedback, new WritingResultPayload(tasks));
    }

    @Override
    public ResultDetailPayload presentDetail(
            PracticeResultContext context,
            PracticeAttemptResultView overview,
            Long questionId
    ) {
        if (!(overview.payload() instanceof WritingResultPayload writing)) {
            throw new IllegalStateException("Writing Result Detail requires a Writing payload.");
        }
        // Explicit invalid/foreign selectors retain the accepted 13E-01
        // compatibility fallback. The fallback is limited to detail-capable
        // tasks already inside this immutable attempt, so it cannot leak or
        // cross-select a foreign question.
        Long activeQuestionId = writing.tasks().stream()
                .filter(WritingTaskResult::detailAvailable)
                .filter(task -> questionId != null && questionId.equals(task.questionId()))
                .map(WritingTaskResult::questionId)
                .findFirst()
                .orElseGet(() -> writing.tasks().stream()
                        .filter(WritingTaskResult::detailAvailable)
                        .map(WritingTaskResult::questionId)
                        .findFirst()
                        .orElse(null));

        JsonNode feedbackRoot = readTree(context.attempt().getAiFeedbackJson());
        List<WritingTaskResult> detailTasks = writing.tasks().stream()
                .map(task -> detailTask(
                        task,
                        currentTaskContractMatches(
                                task,
                                strictQuestionFeedbackNode(
                                        feedbackRoot, task.questionId()))))
                .toList();
        List<ResultDetailScoreCriterion> scoreCriteria = new ArrayList<>();
        for (int taskIndex = 0; taskIndex < detailTasks.size(); taskIndex++) {
            WritingTaskResult task = detailTasks.get(taskIndex);
            JsonNode currentQuestionNode = strictQuestionFeedbackNode(
                    feedbackRoot, task.questionId());
            if (!currentTaskContractMatches(task, currentQuestionNode)) {
                continue;
            }
            for (int criterionIndex = 0;
                 criterionIndex < task.officialCriteria().size();
                 criterionIndex++) {
                ResultRubricCriterion criterion = task.officialCriteria().get(criterionIndex);
                scoreCriteria.add(new ResultDetailScoreCriterion(
                        task.questionId(),
                        criterion.criterionId(),
                        ResultDetailDescriptorRegistry.scoreLabelVi(criterion.criterionId()),
                        ResultDetailDescriptorRegistry.scoreLabelKo(criterion.criterionId()),
                        criterion.score(),
                        criterion.maxScore(),
                        criterion.score() == null || criterion.maxScore() == null
                                ? "UNAVAILABLE" : "SCORED",
                        taskIndex * 100 + criterionIndex + 1));
            }
        }

        WritingTaskResult activeTask = detailTasks.stream()
                .filter(task -> activeQuestionId != null
                        && activeQuestionId.equals(task.questionId()))
                .findFirst()
                .orElse(null);
        List<WritingDiagnosticGroup> diagnosticGroups = List.of();
        WritingUpgradeView upgrade = null;
        DiagnosticAvailability diagnosticAvailability =
                DiagnosticAvailability.noDetailTask();
        if (activeTask != null) {
            JsonNode selectedNode = strictQuestionFeedbackNode(
                    feedbackRoot, activeTask.questionId());
            upgrade = writingUpgrade(activeTask, selectedNode);
            String feedbackState = activeTask.feedback() == null
                    ? null
                    : activeTask.feedback().state();
            if (activeTask.feedback() == null
                    || "PENDING".equals(feedbackState)
                    || "FAILED".equals(feedbackState)
                    || "UNAVAILABLE".equals(feedbackState)) {
                diagnosticAvailability = DiagnosticAvailability.feedbackUnavailable();
            } else if (!currentTaskContractMatches(activeTask, selectedNode)) {
                diagnosticAvailability =
                        DiagnosticAvailability.taskIdentityUnavailable();
            } else if (activeTask.clozeTask()) {
                // Current immutable task/finding contracts do not expose a
                // blank id/index. Findings therefore fail closed instead of
                // being guessed onto blank 1, blank 2, or an essay parent.
                diagnosticAvailability = DiagnosticAvailability.blankIdentityUnavailable();
            } else if (!activeTask.feedback().ready()) {
                diagnosticAvailability = DiagnosticAvailability.feedbackUnavailable();
            } else {
                JsonNode currentQuestionNode = strictQuestionFeedbackNode(
                        feedbackRoot, activeTask.questionId());
                WritingFeedbackCompatibilityReader.EntryResult contract =
                        compatibilityReader.parseStoredEntry(currentQuestionNode);
                if (contract.value() == null) {
                    diagnosticAvailability =
                            DiagnosticAvailability.currentEvidenceUnavailable();
                } else {
                    List<ResolvedDiagnostic> resolved = new ArrayList<>();
                    addValidatedWritingDiagnostics(
                            resolved,
                            activeTask,
                            currentQuestionNode.path("strengths"),
                            ResultDetailPolarity.STRENGTH);
                    addValidatedWritingDiagnostics(
                            resolved,
                            activeTask,
                            currentQuestionNode.path("needs_improvement"),
                            ResultDetailPolarity.NEEDS_IMPROVEMENT);
                    diagnosticGroups = diagnosticGroups(resolved);
                    diagnosticAvailability = resolved.isEmpty()
                            ? DiagnosticAvailability.noValidatedEvidence()
                            : DiagnosticAvailability.available();
                }
            }
        }

        return new WritingDetailPayload(
                activeTask == null ? overview.feedback() : activeTask.feedback(),
                detailTasks,
                activeQuestionId,
                List.copyOf(scoreCriteria),
                WritingScoringPolicy.PROFILE_ID,
                WritingDiagnosticDescriptorRegistry.SEAM_ID,
                WritingDiagnosticDescriptorRegistry.SEAM_STATE,
                WritingDiagnosticDescriptorRegistry.SCOPE_NOTE_VI,
                WritingDiagnosticDescriptorRegistry.SCOPE_NOTE_KO,
                diagnosticAvailability.code(),
                diagnosticAvailability.noteVi(),
                diagnosticAvailability.noteKo(),
                diagnosticGroups,
                upgrade);
    }

    private static WritingTaskResult detailTask(
            WritingTaskResult task,
            boolean trustedTaskIdentity
    ) {
        if (trustedTaskIdentity || !task.detailAvailable()) {
            return task;
        }
        boolean legacyUnverified = task.feedback() != null
                && "LEGACY_UNVERIFIED".equals(task.feedback().state());
        return new WritingTaskResult(
                task.questionId(),
                task.questionVersionId(),
                task.questionNo(),
                task.taskType(),
                task.taskLabel(),
                task.prompt(),
                task.learnerAnswer(),
                task.score() == null ? null : task.score().unavailableView(),
                new ResultFeedbackAvailability(
                        legacyUnverified ? "LEGACY_UNVERIFIED" : "UNAVAILABLE",
                        legacyUnverified
                                ? "Dữ liệu đánh giá cũ chỉ được nhận diện, không được tin làm kết quả"
                                : "Không thể xác minh contract hiện hành của phản hồi",
                        0,
                        task.answered() ? 1 : 0),
                null,
                List.of(),
                List.of(),
                task.detailAvailable());
    }

    private static void addValidatedWritingDiagnostics(
            List<ResolvedDiagnostic> target,
            WritingTaskResult task,
            JsonNode findings,
            ResultDetailPolarity polarity
    ) {
        if (!findings.isArray()) {
            return;
        }
        for (int index = 0; index < findings.size(); index++) {
            JsonNode finding = findings.get(index);
            if (finding == null || !finding.isObject()) {
                continue;
            }
            // Validate the raw id before any compatibility canonicalization so
            // an inactive alias and its canonical replacement cannot both count.
            WritingRubricCriterion criterion = WritingRubricCriterion.parse(
                    finding.path("criterionId").asText(null));
            if (criterion == null || !criterion.activeForProvider()
                    || !criterion.appliesTo(task.taskType())
                    || !criterion.polarity().name().equals(polarity.name())) {
                continue;
            }
            WritingRubricCriterion.EvidenceScope evidenceScope =
                    explicitEvidenceScope(finding.get("evidenceScope"));
            if (evidenceScope == null || !criterion.supports(evidenceScope)
                    // Result Detail has no authoritative structured task metadata seam yet.
                    || evidenceScope == WritingRubricCriterion.EvidenceScope.TASK_METADATA) {
                continue;
            }
            String evidence = finding.path("evidence").asText("");
            String explanation = finding.path("explanationVi").asText("").trim();
            String correction = finding.path("correction").asText("").trim();
            if (explanation.isBlank()) {
                continue;
            }
            if (evidenceScope == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && (evidence.isBlank()
                    || task.learnerAnswer() == null
                    || !task.learnerAnswer().contains(evidence))) {
                continue;
            }
            if (evidenceScope == WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER
                    && !evidence.isEmpty()) {
                continue;
            }
            if (polarity == ResultDetailPolarity.STRENGTH && !correction.isEmpty()) {
                continue;
            }
            if (polarity == ResultDetailPolarity.NEEDS_IMPROVEMENT
                    && evidenceScope == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && correction.isBlank()) {
                continue;
            }
            WritingDiagnosticDescriptorRegistry.Resolution descriptor =
                    WritingDiagnosticDescriptorRegistry.resolve(
                            criterion,
                            task.taskType(),
                            polarity,
                            WritingDiagnosticDescriptorRegistry.wholeAnswerTarget());
            String evidenceAvailability = ResultDetailDescriptorRegistry
                    .evidenceAvailability(evidenceScope.name());
            if (descriptor == null || evidenceAvailability == null
                    || (descriptor.parentCriterionId() != null
                    && task.officialCriteria().stream().noneMatch(scoreCriterion ->
                    descriptor.parentCriterionId().equals(
                            scoreCriterion.criterionId())))) {
                continue;
            }
            WritingDiagnosticDescriptorRegistry.FeatureDescriptor feature =
                    descriptor.feature();
            WritingDiagnosticDescriptorRegistry.CategoryDescriptor category =
                    feature.category();
            WritingDiagnosticFinding diagnostic = new WritingDiagnosticFinding(
                    task.questionId(),
                    "W:" + task.questionId() + ":" + descriptor.id() + ":" + (index + 1),
                    category.code(),
                    category.labelVi(),
                    category.labelKo(),
                    category.stableOrder(),
                    feature.code(),
                    feature.labelVi(),
                    feature.labelKo(),
                    feature.stableOrder(),
                    polarity,
                    descriptor.parentCriterionId(),
                    descriptor.scoreEffect(),
                    descriptor.applicability(),
                    descriptor.target(),
                    evidenceAvailability,
                    evidenceScope.name(),
                    evidence,
                    explanation,
                    correction,
                    null,
                    null,
                    null,
                    null);
            target.add(new ResolvedDiagnostic(descriptor, diagnostic));
        }
    }

    private static WritingRubricCriterion.EvidenceScope explicitEvidenceScope(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        try {
            return WritingRubricCriterion.EvidenceScope.valueOf(node.asText().trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<WritingDiagnosticGroup> diagnosticGroups(
            List<ResolvedDiagnostic> diagnostics
    ) {
        List<WritingDiagnosticGroup> groups = new ArrayList<>();
        for (WritingDiagnosticDescriptorRegistry.CategoryDescriptor category
                : WritingDiagnosticDescriptorRegistry.categories()) {
            List<ResolvedDiagnostic> categoryDiagnostics = diagnostics.stream()
                    .filter(value -> value.definition().feature().category().code()
                            .equals(category.code()))
                    .toList();
            if (categoryDiagnostics.isEmpty()) {
                continue;
            }
            List<WritingDiagnosticFinding> strengths = categoryDiagnostics.stream()
                    .map(ResolvedDiagnostic::finding)
                    .filter(finding ->
                            finding.polarity() == ResultDetailPolarity.STRENGTH)
                    .toList();
            List<WritingDiagnosticFinding> needs = categoryDiagnostics.stream()
                    .map(ResolvedDiagnostic::finding)
                    .filter(finding ->
                            finding.polarity() == ResultDetailPolarity.NEEDS_IMPROVEMENT)
                    .toList();
            List<WritingDiagnosticChip> categoryChips = chips(categoryDiagnostics);
            groups.add(new WritingDiagnosticGroup(
                    category.code(),
                    category.labelVi(),
                    category.labelKo(),
                    category.stableOrder(),
                    strengths,
                    needs,
                    categoryChips.stream()
                            .filter(chip ->
                                    chip.polarity() == ResultDetailPolarity.STRENGTH)
                            .toList(),
                    categoryChips.stream()
                            .filter(chip ->
                                    chip.polarity()
                                            == ResultDetailPolarity.NEEDS_IMPROVEMENT)
                            .toList()));
        }
        return List.copyOf(groups);
    }

    private static List<WritingDiagnosticChip> chips(
            List<ResolvedDiagnostic> diagnostics
    ) {
        Map<String, ChipCount> counts = new LinkedHashMap<>();
        for (ResolvedDiagnostic resolved : diagnostics) {
            String key = resolved.definition().id()
                    + ":" + resolved.finding().polarity().name();
            counts.compute(key, (ignored, current) ->
                    current == null
                            ? new ChipCount(
                                    resolved.definition(),
                                    resolved.finding().polarity(),
                                    1,
                                    resolved.finding().evidenceAvailability())
                            : current.incremented(resolved.finding().evidenceAvailability()));
        }
        return counts.values().stream()
                .sorted(Comparator.comparingInt(value -> value.definition().stableOrder()))
                .map(value -> new WritingDiagnosticChip(
                        value.definition().id(),
                        value.definition().feature().labelVi(),
                        value.definition().feature().labelKo(),
                        value.polarity(),
                        value.definition().parentCriterionId(),
                        value.definition().scoreEffect(),
                        value.definition().applicability(),
                        value.definition().stableOrder(),
                        value.count(),
                        false,
                        value.evidenceAvailability()))
                .toList();
    }

    private WritingUpgradeView writingUpgrade(
            WritingTaskResult task,
            JsonNode feedbackNode
    ) {
        WritingFeedbackView feedback = task.feedback() != null
                && task.feedback().ready()
                && currentTaskContractMatches(task, feedbackNode)
                ? feedbackMapper.map(feedbackNode)
                : null;
        String upgradedAnswer = feedback == null ? null : feedback.upgradedAnswer();
        String evaluatorSample = feedback == null ? null : feedback.sampleAnswer();
        List<WritingSentenceRewriteView> rewrites = feedback == null
                ? List.of()
                : feedback.sentenceRewrites().stream()
                        .filter(rewrite -> rewrite.original() != null
                                && !rewrite.original().isBlank()
                                && task.learnerAnswer() != null
                                && task.learnerAnswer().contains(rewrite.original())
                                && rewrite.upgraded() != null
                                && !rewrite.upgraded().isBlank()
                                && rewrite.reason() != null
                                && !rewrite.reason().isBlank())
                        .toList();
        return new WritingUpgradeView(
                task.questionId(),
                answerArtifact(
                        upgradedAnswer,
                        "LEARNER_SUBMISSION_DERIVED_EVALUATOR_OUTPUT",
                        "Bài nâng cấp dựa trên bài đã nộp",
                        "제출 답안을 바탕으로 개선한 답안"),
                rewrites,
                answerArtifact(
                        evaluatorSample,
                        "EVALUATOR_GENERATED_NOT_TEACHER_REFERENCE",
                        "Bài tham khảo do bộ đánh giá tạo",
                        "평가기가 생성한 참고 답안"));
    }

    private static WritingAnswerArtifact answerArtifact(
            String content,
            String provenance,
            String labelVi,
            String labelKo
    ) {
        String normalized = content == null ? "" : content.trim();
        return new WritingAnswerArtifact(
                normalized,
                normalized.isBlank() ? "UNAVAILABLE" : "AVAILABLE",
                provenance,
                labelVi,
                labelKo);
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
                List.of(),
                false);
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
        boolean scoreContractReady = currentScoreContractMatches(
                taskType, usableFeedbackNode, evaluation);
        List<ResultRubricCriterion> parsedCriteria = criteria(
                rubric, usableFeedbackNode, scoreContractReady);
        ResultScoreSummary score = taskScore(
                scoreContractReady ? evaluation : null, parsedCriteria, rubric);
        ResultFeedbackAvailability availability = taskFeedback(
                answered,
                malformedStoredFeedback,
                feedback,
                usableFeedbackNode,
                contract,
                scoreContractReady,
                score);
        List<ResultRubricCriterion> visibleCriteria = availability.ready()
                ? parsedCriteria
                : List.of();
        List<WritingAnalysisLens> lenses = isCloze(taskType)
                ? List.of()
                : longFormLenses(visibleCriteria, availability.ready() ? feedback : null);

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
                visibleCriteria,
                lenses,
                "ESSAY".equals(question.getQuestionType()));
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
            BigDecimal expectedMaxScore = BigDecimal.valueOf(expected.maxScore());
            if (maxScore != null && maxScore.compareTo(expectedMaxScore) != 0) {
                score = null;
            }
            if (score != null && (score.signum() < 0 || score.compareTo(expectedMaxScore) > 0)) {
                score = null;
            }
            result.add(new ResultRubricCriterion(
                    expected.criterionId(),
                    expected.displayName(),
                    score,
                    expectedMaxScore,
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
        if (!complete) {
            return new ResultScoreSummary(null, null, null, null,
                    "POINTS", "Thang điểm " + rubric.totalMaxScore(), null);
        }
        BigDecimal earned = criteria.stream().map(ResultRubricCriterion::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal possible = criteria.stream().map(ResultRubricCriterion::maxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            boolean allowCriterionFallback) {
        List<String> evidence = evidence(
                feedback, source.criterionId(), categories, allowCriterionFallback);
        return new WritingAnalysisLens(
                code,
                label,
                source.criterionId(),
                evidence);
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
            boolean scoreContractReady,
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
        String normalizedStatus = normalize(status);
        if (normalizedStatus.contains("PENDING") || normalizedStatus.contains("QUEUED")
                || normalizedStatus.contains("PROCESSING")) {
            return new ResultFeedbackAvailability("PENDING", "Đang xử lý đánh giá", 0, 1);
        }
        if (normalizedStatus.contains("UNAVAILABLE") || normalizedStatus.contains("NOT_SCORABLE")) {
            return new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Nhiệm vụ này hiện chưa có đánh giá khả dụng", 0, 1);
        }
        if (normalizedStatus.contains("FAILED") || normalizedStatus.contains("ERROR")
                || normalizedStatus.contains("INVALID")) {
            return new ResultFeedbackAvailability(
                    "FAILED", "Không thể hoàn tất đánh giá nhiệm vụ này", 0, 1);
        }
        if (contract.value() != null
                && contract.value().scoreAvailableFlag()
                && !scoreContractReady) {
            return new ResultFeedbackAvailability(
                    "LEGACY_UNVERIFIED",
                    "Dữ liệu đánh giá cũ chỉ được nhận diện, không được dùng làm điểm",
                    0,
                    1);
        }
        if (scoreContractReady && score.available()) {
            return new ResultFeedbackAvailability("READY", "Đã có đánh giá", 1, 1);
        }
        return new ResultFeedbackAvailability("FAILED", "Chưa có đánh giá khả dụng", 0, 1);
    }

    private static ResultFeedbackAvailability aggregateFeedback(
            int ready,
            int pending,
            int failed,
            int unavailable,
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
        if (unavailable > 0) {
            return new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Một hoặc nhiều nhiệm vụ chưa có đánh giá khả dụng", 0, total);
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

    private JsonNode strictQuestionFeedbackNode(JsonNode root, Long questionId) {
        if (root == null || !root.isObject() || questionId == null) {
            return null;
        }
        JsonNode candidate = root.get(String.valueOf(questionId));
        if (candidate != null && candidate.isTextual()) {
            candidate = readTree(candidate.asText());
        }
        return candidate != null && candidate.isObject() ? candidate : null;
    }

    private boolean currentTaskContractMatches(
            WritingTaskResult task,
            JsonNode feedbackNode
    ) {
        if (task == null || task.taskType() == null || feedbackNode == null) {
            return false;
        }
        WritingFeedbackCompatibilityReader.EntryResult contract =
                compatibilityReader.parseStoredEntry(feedbackNode);
        return currentScoreContractMatches(
                task.taskType(), feedbackNode, contract.value());
    }

    private static boolean currentScoreContractMatches(
            String taskType,
            JsonNode feedbackNode,
            WritingEvaluationResult evaluation
    ) {
        if (taskType == null
                || feedbackNode == null
                || !feedbackNode.isObject()
                || evaluation == null
                || !evaluation.scoreAvailableFlag()) {
            return false;
        }
        String evaluationStatus = normalize(evaluation.evaluationStatus());
        String evaluationSource = normalize(evaluation.evaluationSource());
        return taskType.equals(evaluation.taskType())
                && CURRENT_SCORING_CONTRACT.equals(
                        text(feedbackNode, "scoring_contract"))
                && CURRENT_EVALUATION_ENGINE.equals(evaluation.engine())
                && "EVALUATED".equals(evaluationStatus)
                && CURRENT_EVALUATION_SOURCES.contains(evaluationSource)
                && feedbackNode.path("score_available").isBoolean()
                && feedbackNode.path("score_available").asBoolean();
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
        if (fallbackIndex >= rows.size()) {
            return null;
        }
        JsonNode fallback = rows.get(fallbackIndex);
        String fallbackCriterionId = firstPresent(
                text(fallback, "criterionId"), text(fallback, "criterion_id"));
        return fallbackCriterionId == null ? fallback : null;
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

    private record ResolvedDiagnostic(
            WritingDiagnosticDescriptorRegistry.Resolution definition,
            WritingDiagnosticFinding finding
    ) {
    }

    private record ChipCount(
            WritingDiagnosticDescriptorRegistry.Resolution definition,
            ResultDetailPolarity polarity,
            int count,
            String evidenceAvailability
    ) {
        private ChipCount incremented(String nextEvidenceAvailability) {
            String merged = evidenceAvailability.equals(nextEvidenceAvailability)
                    ? evidenceAvailability
                    : "MIXED_EVIDENCE_AVAILABLE";
            return new ChipCount(definition, polarity, count + 1, merged);
        }
    }

    private record DiagnosticAvailability(
            String code,
            String noteVi,
            String noteKo
    ) {
        private static DiagnosticAvailability available() {
            return new DiagnosticAvailability(
                    "AVAILABLE",
                    "Đang hiển thị các phát hiện hiện tại đã vượt qua kiểm tra bằng chứng.",
                    "현재 근거 검증을 통과한 항목을 표시합니다.");
        }

        private static DiagnosticAvailability noValidatedEvidence() {
            return new DiagnosticAvailability(
                    "NO_VALIDATED_EVIDENCE",
                    "Chưa có phát hiện nào vượt qua kiểm tra bằng chứng cho câu đang chọn.",
                    "선택한 문항에서 근거 검증을 통과한 항목이 없습니다.");
        }

        private static DiagnosticAvailability blankIdentityUnavailable() {
            return new DiagnosticAvailability(
                    "BLANK_IDENTITY_UNAVAILABLE",
                    "Chưa thể gắn phát hiện vào ô trống vì dữ liệu hiện tại không có định danh ô có thẩm quyền.",
                    "현재 데이터에 권위 있는 빈칸 식별자가 없어 진단 항목을 빈칸에 연결할 수 없습니다.");
        }

        private static DiagnosticAvailability feedbackUnavailable() {
            return new DiagnosticAvailability(
                    "FEEDBACK_UNAVAILABLE",
                    "Câu đang chọn chưa có phản hồi khả dụng để hiển thị chẩn đoán.",
                    "선택한 문항에는 진단을 표시할 수 있는 피드백이 아직 없습니다.");
        }

        private static DiagnosticAvailability currentEvidenceUnavailable() {
            return new DiagnosticAvailability(
                    "CURRENT_EVIDENCE_CONTRACT_UNAVAILABLE",
                    "Phản hồi tương thích vẫn đọc được, nhưng không đủ contract hiện hành để tính phát hiện chẩn đoán.",
                    "호환 피드백은 읽을 수 있지만 현재 진단 항목으로 집계할 계약 근거가 부족합니다.");
        }

        private static DiagnosticAvailability taskIdentityUnavailable() {
            return new DiagnosticAvailability(
                    "TASK_IDENTITY_UNAVAILABLE",
                    "Phản hồi chỉ đọc được qua compatibility hoặc thiếu contract hiện hành; KSH không dùng nó làm chẩn đoán.",
                    "호환 경로로만 읽히거나 현재 계약이 부족한 피드백은 KSH 진단에 사용하지 않습니다.");
        }

        private static DiagnosticAvailability noDetailTask() {
            return new DiagnosticAvailability(
                    "NO_DETAIL_TASK",
                    "Không có nhiệm vụ Viết phù hợp để hiển thị chi tiết.",
                    "상세 결과를 표시할 수 있는 쓰기 과제가 없습니다.");
        }
    }
}
