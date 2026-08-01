package com.ksh.features.practice.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAttempt;
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
import com.ksh.features.practice.assessment.WritingBlankContract;
import com.ksh.features.practice.assessment.WritingBlankContractVerifier;
import com.ksh.features.practice.ai.writing.WritingEvaluationResult;
import com.ksh.features.practice.ai.writing.WritingAssessmentPolicyBundle;
import com.ksh.features.practice.ai.writing.WritingDiagnosticContract;
import com.ksh.features.practice.ai.writing.WritingEvidenceLedgerVerifier;
import com.ksh.features.practice.ai.writing.WritingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.writing.WritingFeedbackViewMapper;
import com.ksh.features.practice.ai.writing.WritingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingScoreAnchorPolicy;
import com.ksh.features.practice.ai.writing.WritingScoringCriterion;
import com.ksh.features.practice.ai.writing.WritingScoringPolicy;
import com.ksh.features.practice.ai.writing.WritingScoringRubric;
import com.ksh.features.practice.ai.writing.WritingTaskRequirementPolicy;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnnotationView;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnswerArtifact;
import com.ksh.features.practice.dto.PracticeDtos.WritingBlankAnswerView;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticChip;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticFinding;
import com.ksh.features.practice.dto.PracticeDtos.WritingDiagnosticGroup;
import com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.WritingFindingView;
import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPolarity;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailSpanMembership;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailScoreCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultPerformanceLevel;
import com.ksh.features.practice.dto.PracticeDtos.ResultRubricCriterion;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.WritingAnalysisLens;
import com.ksh.features.practice.dto.PracticeDtos.WritingResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.WritingSentenceRewriteView;
import com.ksh.features.practice.dto.PracticeDtos.WritingTaskResult;
import com.ksh.features.practice.dto.PracticeDtos.WritingTaskCoverageView;
import com.ksh.features.practice.dto.PracticeDtos.WritingTeacherSampleView;
import com.ksh.features.practice.dto.PracticeDtos.WritingTextSegment;
import com.ksh.features.practice.dto.PracticeDtos.WritingUpgradeView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
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
    private static final String CURRENT_EVALUATION_ENGINE =
            "KSH_WRITING_EVALUATOR_V3";

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
                    ? task(
                            question,
                            answer,
                            feedbackNode,
                            malformedStoredFeedback,
                            context.attempt().getAnalysisStatus())
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
                        taskIndex * 100 + criterionIndex + 1,
                        resultPerformanceLevel(criterion)));
            }
        }

        WritingTaskResult activeTask = detailTasks.stream()
                .filter(task -> activeQuestionId != null
                        && activeQuestionId.equals(task.questionId()))
                .findFirst()
                .orElse(null);
        List<WritingDiagnosticGroup> diagnosticGroups = List.of();
        List<WritingTextSegment> learnerAnswerSegments = activeTask == null
                ? List.of()
                : plainLearnerAnswer(activeTask.learnerAnswer());
        List<WritingBlankAnswerView> structuredBlankAnswers =
                activeTask == null
                        ? List.of()
                        : structuredBlankAnswers(
                        context.snapshot().questions(),
                        activeTask);
        WritingUpgradeView upgrade = null;
        WritingTeacherSampleView teacherSample =
                WritingTeacherSampleView.unavailable();
        List<WritingTaskCoverageView> taskCoverage = List.of();
        DiagnosticAvailability diagnosticAvailability =
                DiagnosticAvailability.noDetailTask();
        if (activeTask != null) {
            JsonNode selectedNode = strictQuestionFeedbackNode(
                    feedbackRoot, activeTask.questionId());
            // Exact inline annotations are assembled from the validated
            // diagnostic ledger below.  Do not build a second UI-only span map.
            learnerAnswerSegments = plainLearnerAnswer(activeTask.learnerAnswer());
            upgrade = writingUpgrade(activeTask, selectedNode);
            teacherSample = teacherSample(selectedNode);
            taskCoverage = taskCoverage(activeTask, selectedNode);
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
            } else if (activeTask.clozeTask()
                    && structuredBlankAnswers.isEmpty()) {
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
                    WritingFeedbackView feedback =
                            feedbackMapper.map(currentQuestionNode);
                    Map<String, WritingAnnotationView> annotations =
                            annotationsByFindingId(feedback);
                    Set<String> findingIds = new LinkedHashSet<>();
                    addValidatedWritingDiagnostics(
                            resolved,
                            activeTask,
                            currentQuestionNode.path("strengths"),
                            ResultDetailPolarity.STRENGTH,
                            annotations,
                            findingIds,
                            structuredBlankAnswers);
                    addValidatedWritingDiagnostics(
                            resolved,
                            activeTask,
                            currentQuestionNode.path("needs_improvement"),
                            ResultDetailPolarity.NEEDS_IMPROVEMENT,
                            annotations,
                            findingIds,
                            structuredBlankAnswers);
                    if (annotations == null
                            || annotations.values().stream().anyMatch(
                            annotation -> !findingIds.contains(
                                    annotation.findingId()))) {
                        resolved.clear();
                    }
                    diagnosticGroups = diagnosticGroups(
                            resolved, activeTask, structuredBlankAnswers);
                    diagnosticAvailability = resolved.isEmpty()
                            ? DiagnosticAvailability.noValidatedEvidence()
                            : DiagnosticAvailability.available();
                }
            }
        }
        structuredBlankAnswers = annotateStructuredBlankAnswers(
                structuredBlankAnswers,
                diagnosticGroups);
        if (activeTask != null && !activeTask.clozeTask()
                && !diagnosticGroups.isEmpty()) {
            learnerAnswerSegments =
                    learnerAnswerSegmentsFromDiagnostics(
                            activeTask,
                            diagnosticGroups);
        }

        return new WritingDetailPayload(
                activeTask == null ? overview.feedback() : activeTask.feedback(),
                detailTasks,
                activeQuestionId,
                learnerAnswerSegments,
                List.copyOf(scoreCriteria),
                taskCoverage,
                WritingScoringPolicy.PROFILE_ID,
                WritingDiagnosticDescriptorRegistry.SEAM_ID,
                WritingDiagnosticDescriptorRegistry.SEAM_STATE,
                WritingDiagnosticDescriptorRegistry.SCOPE_NOTE_VI,
                WritingDiagnosticDescriptorRegistry.SCOPE_NOTE_KO,
                diagnosticAvailability.code(),
                diagnosticAvailability.noteVi(),
                diagnosticAvailability.noteKo(),
                diagnosticGroups,
                upgrade,
                structuredBlankAnswers,
                teacherSample);
    }

    private List<WritingBlankAnswerView> structuredBlankAnswers(
            List<PracticeQuestionVersion> questions,
            WritingTaskResult task
    ) {
        if (task == null || !task.clozeTask()
                || task.learnerAnswer() == null
                || task.learnerAnswer().isBlank()) {
            return List.of();
        }
        PracticeQuestionVersion question = questions.stream()
                .filter(candidate -> java.util.Objects.equals(
                        candidate.getQuestionId(),
                        task.questionId()))
                .findFirst()
                .orElse(null);
        if (question == null) {
            return List.of();
        }
        try {
            CanonicalQuestionType type =
                    typeResolver.resolve(question.getQuestionType());
            QuestionContent content =
                    contractCodec.readQuestionContent(
                            question.getQuestionContentJson(),
                            type);
            WritingBlankContract.QuestionResponse authority =
                    content.writingResponse();
            WritingBlankContract.LearnerResponse response =
                    objectMapper.readValue(
                            task.learnerAnswer(),
                            WritingBlankContract.LearnerResponse.class);
            WritingBlankContractVerifier.verifyLearnerResponse(
                    authority,
                    response);
            List<WritingBlankAnswerView> result = new ArrayList<>();
            for (int index = 0; index < authority.blanks().size(); index++) {
                WritingBlankContract.BlankDefinition definition =
                        authority.blanks().get(index);
                WritingBlankContract.LearnerBlankAnswer answer =
                        response.answers().get(index);
                result.add(new WritingBlankAnswerView(
                        definition.blankId(),
                        definition.ordinal(),
                        answer.text(),
                        List.of(WritingTextSegment.plain(
                                answer.text()))));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<WritingBlankAnswerView>
            annotateStructuredBlankAnswers(
            List<WritingBlankAnswerView> blanks,
            List<WritingDiagnosticGroup> groups
    ) {
        if (blanks.isEmpty() || groups.isEmpty()) {
            return blanks;
        }
        List<WritingDiagnosticFinding> findings = groups.stream()
                .flatMap(group -> java.util.stream.Stream.concat(
                        group.strengths().stream(),
                        group.needsImprovement().stream()))
                .filter(finding -> finding.target().kind()
                        == com.ksh.features.practice.dto.PracticeDtos
                        .WritingDiagnosticTargetKind.BLANK)
                .toList();
        List<WritingBlankAnswerView> result = new ArrayList<>();
        for (WritingBlankAnswerView blank : blanks) {
            List<BlankAnnotationCandidate> candidates = new ArrayList<>();
            for (WritingDiagnosticFinding finding : findings) {
                if (!blank.blankId().equals(
                        finding.target().blankId())
                        || !"TEXT_SPAN".equals(
                        finding.evidenceScope())) {
                    continue;
                }
                Integer start = finding.startOffset();
                Integer end = finding.endOffset();
                if (start == null || end == null
                        || start < 0 || end <= start
                        || end > blank.text().length()
                        || !blank.text().substring(start, end)
                        .equals(finding.evidence())) {
                    continue;
                }
                candidates.add(new BlankAnnotationCandidate(
                        start,
                        end,
                        finding));
            }
            candidates.sort(Comparator
                    .comparingInt(BlankAnnotationCandidate::start)
                    .thenComparingInt(BlankAnnotationCandidate::end)
                    .thenComparing(candidate ->
                            candidate.finding().findingId()));
            int previousEnd = 0;
            boolean overlaps = false;
            for (BlankAnnotationCandidate candidate : candidates) {
                if (candidate.start() < previousEnd) {
                    overlaps = true;
                    break;
                }
                previousEnd = candidate.end();
            }
            if (overlaps || candidates.isEmpty()) {
                result.add(blank);
                continue;
            }
            List<WritingTextSegment> segments = new ArrayList<>();
            int cursor = 0;
            for (BlankAnnotationCandidate candidate : candidates) {
                if (candidate.start() > cursor) {
                    segments.add(WritingTextSegment.plain(
                            blank.text().substring(
                                    cursor,
                                    candidate.start())));
                }
                WritingDiagnosticFinding finding =
                        candidate.finding();
                segments.add(new WritingTextSegment(
                        blank.text().substring(
                                candidate.start(),
                                candidate.end()),
                        true,
                        finding.findingId(),
                        finding.displayNumber(),
                        finding.polarity().name(),
                        finding.categoryCode(),
                        finding.featureCode(),
                        finding.explanationVi(),
                        finding.correctionKo() == null
                                || finding.correctionKo().isBlank()
                                ? null
                                : finding.correctionKo(),
                        finding.descriptorId(),
                        List.of(finding.spanMembership())));
                cursor = candidate.end();
            }
            if (cursor < blank.text().length()) {
                segments.add(WritingTextSegment.plain(
                        blank.text().substring(cursor)));
            }
            result.add(new WritingBlankAnswerView(
                    blank.blankId(),
                    blank.ordinal(),
                    blank.text(),
                    segments));
        }
        return List.copyOf(result);
    }

    private static List<WritingTextSegment>
            learnerAnswerSegmentsFromDiagnostics(
            WritingTaskResult task,
            List<WritingDiagnosticGroup> groups
    ) {
        String source = Normalizer.normalize(
                task.learnerAnswer() == null
                        ? ""
                        : task.learnerAnswer(),
                Normalizer.Form.NFC);
        List<FindingAnnotationCandidate> candidates = groups.stream()
                .flatMap(group -> java.util.stream.Stream.concat(
                        group.strengths().stream(),
                        group.needsImprovement().stream()))
                .filter(finding -> "TEXT_SPAN".equals(
                        finding.evidenceScope()))
                .filter(finding -> finding.startOffset() != null
                        && finding.endOffset() != null
                        && finding.startOffset() >= 0
                        && finding.endOffset()
                        <= source.length()
                        && finding.endOffset()
                        > finding.startOffset()
                        && source.substring(
                        finding.startOffset(),
                        finding.endOffset())
                        .equals(finding.evidence()))
                .map(finding -> new FindingAnnotationCandidate(
                        finding.startOffset(),
                        finding.endOffset(),
                        finding))
                .sorted(Comparator
                        .comparingInt(
                                FindingAnnotationCandidate::start)
                        .thenComparingInt(
                                FindingAnnotationCandidate::end)
                        .thenComparing(candidate ->
                                candidate.finding().findingId()))
                .toList();
        if (candidates.isEmpty()) {
            return plainLearnerAnswer(source);
        }
        java.util.SortedSet<Integer> boundaries = new java.util.TreeSet<>();
        boundaries.add(0);
        boundaries.add(source.length());
        candidates.forEach(candidate -> {
            boundaries.add(candidate.start());
            boundaries.add(candidate.end());
        });
        List<WritingTextSegment> result = new ArrayList<>();
        List<Integer> points = List.copyOf(boundaries);
        for (int index = 0; index < points.size() - 1; index++) {
            int start = points.get(index);
            int end = points.get(index + 1);
            if (start == end) {
                continue;
            }
            List<WritingDiagnosticFinding> active = candidates.stream()
                    .filter(candidate -> candidate.start() <= start
                            && candidate.end() >= end)
                    .map(FindingAnnotationCandidate::finding)
                    .toList();
            String text = source.substring(start, end);
            if (active.isEmpty()) {
                result.add(WritingTextSegment.plain(text));
                continue;
            }
            WritingDiagnosticFinding primary = active.get(0);
            List<ResultDetailSpanMembership> memberships = active.stream()
                    .map(WritingDiagnosticFinding::spanMembership)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            result.add(new WritingTextSegment(
                    text,
                    true,
                    primary.findingId(),
                    primary.displayNumber(),
                    primary.polarity().name(),
                    primary.categoryCode(),
                    primary.featureCode(),
                    primary.explanationVi(),
                    primary.correctionKo() == null
                            || primary.correctionKo().isBlank()
                            ? null
                            : primary.correctionKo(),
                    primary.descriptorId(),
                    memberships));
        }
        return List.copyOf(result);
    }

    private static WritingTeacherSampleView teacherSample(
            JsonNode feedbackNode
    ) {
        JsonNode sample = feedbackNode == null
                ? null
                : feedbackNode.get("teacher_sample");
        if (sample == null || !sample.isObject()
                || !"ksh-teacher-sample-v1".equals(
                sample.path("contractVersion").asText(null))
                || !"TEACHER_AUTHORED".equals(
                sample.path("source").asText(null))
                || !"LECTURER".equals(
                sample.path("authorRole").asText(null))
                || sample.path("fixtureId").asText("").isBlank()
                || sample.path("content").asText("").isBlank()) {
            return WritingTeacherSampleView.unavailable();
        }
        return new WritingTeacherSampleView(
                sample.path("content").asText(),
                "AVAILABLE",
                "TEACHER_AUTHORED",
                "LECTURER",
                sample.path("fixtureId").asText());
    }

    private List<WritingTaskCoverageView> taskCoverage(
            WritingTaskResult task,
            JsonNode feedbackNode
    ) {
        if (task == null
                || task.questionId() == null
                || !currentTaskContractMatches(task, feedbackNode)) {
            return List.of();
        }
        JsonNode coverage = feedbackNode.path("task_coverage");
        JsonNode evidenceLedger = feedbackNode.path("evidence_ledger");
        if (!coverage.isArray() || !evidenceLedger.isArray()) {
            return List.of();
        }
        Set<String> knownEvidenceIds = new LinkedHashSet<>();
        for (JsonNode evidence : evidenceLedger) {
            String evidenceId = text(evidence, "evidenceId");
            if (evidenceId == null
                    || evidenceId.isBlank()
                    || !knownEvidenceIds.add(evidenceId)) {
                return List.of();
            }
        }
        Map<String, WritingTaskRequirementPolicy.Requirement> requirements =
                new LinkedHashMap<>();
        for (WritingTaskRequirementPolicy.Requirement requirement
                : WritingTaskRequirementPolicy.requirementsFor(
                        task.taskType())) {
            requirements.put(requirement.requirementId(), requirement);
        }
        List<WritingTaskCoverageView> result = new ArrayList<>();
        Set<String> seenRequirements = new LinkedHashSet<>();
        for (JsonNode row : coverage) {
            String requirementId = text(row, "requirementId");
            String status = text(row, "status");
            WritingTaskRequirementPolicy.Requirement requirement =
                    requirements.get(requirementId);
            List<String> evidenceIds =
                    stringArray(row.get("evidenceIds"));
            if (requirement == null
                    || status == null
                    || !Set.of(
                            "MET", "PARTIAL",
                            "NOT_MET", "NOT_APPLICABLE").contains(status)
                    || evidenceIds == null
                    || !knownEvidenceIds.containsAll(evidenceIds)
                    || !seenRequirements.add(requirementId)) {
                return List.of();
            }
            result.add(new WritingTaskCoverageView(
                    task.questionId(),
                    requirementId,
                    requirement.labelVi(),
                    status,
                    evidenceIds));
        }
        return seenRequirements.equals(requirements.keySet())
                ? List.copyOf(result)
                : List.of();
    }

    private List<WritingTextSegment> learnerAnswerSegments(
            WritingTaskResult task,
            JsonNode feedbackNode
    ) {
        String learnerAnswer = task == null || task.learnerAnswer() == null
                ? ""
                : Normalizer.normalize(
                        task.learnerAnswer(),
                        Normalizer.Form.NFC);
        List<WritingTextSegment> fallback = plainLearnerAnswer(learnerAnswer);
        if (task == null
                || task.clozeTask()
                || task.feedback() == null
                || !task.feedback().ready()
                || !currentTaskContractMatches(task, feedbackNode)) {
            return fallback;
        }

        WritingFeedbackView feedback;
        try {
            feedback = feedbackMapper.map(feedbackNode);
        } catch (RuntimeException exception) {
            return fallback;
        }
        JsonNode rawAnnotations = feedbackNode.get("annotations");
        if (feedback == null
                || rawAnnotations == null
                || !rawAnnotations.isArray()
                || rawAnnotations.isEmpty()
                || rawAnnotations.size() != feedback.annotations().size()) {
            return fallback;
        }

        List<ResolvedTextAnnotation> resolved = new ArrayList<>();
        Set<String> annotationIds = new LinkedHashSet<>();
        for (WritingAnnotationView annotation : feedback.annotations()) {
            ResolvedTextAnnotation candidate = resolveTextAnnotation(
                    task, learnerAnswer, annotation);
            if (candidate == null || !annotationIds.add(candidate.annotationId())) {
                return fallback;
            }
            resolved.add(candidate);
        }
        resolved.sort(Comparator
                .comparingInt(ResolvedTextAnnotation::start)
                .thenComparingInt(ResolvedTextAnnotation::end)
                .thenComparing(ResolvedTextAnnotation::annotationId));

        int previousEnd = 0;
        for (ResolvedTextAnnotation annotation : resolved) {
            if (annotation.start() < previousEnd) {
                return fallback;
            }
            previousEnd = annotation.end();
        }

        List<WritingTextSegment> segments = new ArrayList<>();
        int cursor = 0;
        for (ResolvedTextAnnotation annotation : resolved) {
            if (annotation.start() > cursor) {
                segments.add(WritingTextSegment.plain(
                        learnerAnswer.substring(cursor, annotation.start())));
            }
            segments.add(new WritingTextSegment(
                    learnerAnswer.substring(annotation.start(), annotation.end()),
                    true,
                    annotation.annotationId(),
                    annotation.displayNumber(),
                    annotation.polarity().name(),
                    annotation.categoryCode(),
                    annotation.criterionId(),
                    annotation.explanationVi(),
                    annotation.correctionKo(),
                    annotation.featureId()));
            cursor = annotation.end();
        }
        if (cursor < learnerAnswer.length()) {
            segments.add(WritingTextSegment.plain(
                    learnerAnswer.substring(cursor)));
        }
        return segments.isEmpty() ? fallback : List.copyOf(segments);
    }

    private static ResolvedTextAnnotation resolveTextAnnotation(
            WritingTaskResult task,
            String learnerAnswer,
            WritingAnnotationView annotation
    ) {
        if (annotation == null
                || annotation.id() == null || annotation.id().isBlank()
                || !annotation.id().equals(annotation.findingId())
                || annotation.evidenceId() == null
                || annotation.evidenceId().isBlank()
                || annotation.start() == null || annotation.end() == null
                || annotation.start() < 0
                || annotation.end() <= annotation.start()
                || annotation.end() > learnerAnswer.length()
                || annotation.evidence() == null
                || annotation.evidence().isBlank()
                || annotation.index() == null || annotation.index() < 1
                || annotation.occurrenceIndex() == null
                || annotation.occurrenceIndex() < 1
                || annotation.occurrenceCount() == null
                || annotation.occurrenceCount()
                < annotation.occurrenceIndex()
                || annotation.sourceHash() == null
                || !annotation.sourceHash().equals(
                        WritingEvidenceLedgerVerifier.sha256(learnerAnswer))
                || annotation.operation() == null
                || !Set.of("KEEP", "REPLACE", "REDUNDANT")
                .contains(annotation.operation())
                || annotation.explanationVi() == null
                || annotation.explanationVi().isBlank()) {
            return null;
        }
        ResultDetailPolarity polarity = switch (normalize(annotation.kind())) {
            case "STRENGTH" -> ResultDetailPolarity.STRENGTH;
            case "NEED", "NEEDS_IMPROVEMENT" ->
                    ResultDetailPolarity.NEEDS_IMPROVEMENT;
            default -> null;
        };
        WritingRubricCriterion criterion =
                WritingRubricCriterion.parse(annotation.criterionId());
        if (polarity == null
                || criterion == null
                || !criterion.activeForProvider()
                || !criterion.appliesTo(task.taskType())
                || !criterion.supports(
                        WritingRubricCriterion.EvidenceScope.TEXT_SPAN)
                || !criterion.polarity().name().equals(polarity.name())
                || (polarity == ResultDetailPolarity.STRENGTH
                && annotation.correction() != null
                && !annotation.correction().isBlank())
                || (polarity == ResultDetailPolarity.NEEDS_IMPROVEMENT
                && (("REPLACE".equals(annotation.operation())
                && (annotation.correction() == null
                || annotation.correction().isBlank()))
                || ("REDUNDANT".equals(annotation.operation())
                && annotation.correction() != null
                && !annotation.correction().isBlank())))) {
            return null;
        }
        String exactText = learnerAnswer.substring(
                annotation.start(), annotation.end());
        if (!exactText.equals(annotation.evidence())) {
            return null;
        }
        List<Integer> occurrences = exactOccurrences(
                learnerAnswer, exactText);
        if (occurrences.size() != annotation.occurrenceCount()
                || annotation.occurrenceIndex() > occurrences.size()
                || occurrences.get(annotation.occurrenceIndex() - 1)
                != annotation.start()) {
            return null;
        }
        WritingDiagnosticDescriptorRegistry.Resolution descriptor =
                WritingDiagnosticDescriptorRegistry.resolve(
                        criterion,
                        task.taskType(),
                        polarity,
                        WritingDiagnosticDescriptorRegistry.textSpanTarget());
        if (descriptor == null) {
            return null;
        }
        return new ResolvedTextAnnotation(
                annotation.start(),
                annotation.end(),
                annotation.id().trim(),
                annotation.index(),
                annotation.evidenceId(),
                polarity,
                descriptor.feature().category().code(),
                criterion.canonicalId(),
                annotation.explanationVi().trim(),
                annotation.correction() == null
                        || annotation.correction().isBlank()
                        ? null
                        : annotation.correction().trim(),
                descriptor.id());
    }

    private static List<WritingTextSegment> plainLearnerAnswer(String learnerAnswer) {
        return List.of(WritingTextSegment.plain(learnerAnswer));
    }

    private static WritingTaskResult detailTask(
            WritingTaskResult task,
            boolean trustedTaskIdentity
    ) {
        if (trustedTaskIdentity || !task.detailAvailable()) {
            return task;
        }
        ResultFeedbackAvailability closedFeedback = task.feedback();
        String closedState = closedFeedback == null
                ? ""
                : normalize(closedFeedback.state());
        boolean preservesClosedState = switch (closedState) {
            case "PENDING", "FAILED", "UNAVAILABLE", "LEGACY_UNVERIFIED" -> true;
            default -> false;
        };
        if (!preservesClosedState) {
            closedFeedback = new ResultFeedbackAvailability(
                    "UNAVAILABLE",
                    "Không thể xác minh contract hiện hành của phản hồi",
                    0,
                    task.answered() ? 1 : 0);
        }
        return new WritingTaskResult(
                task.questionId(),
                task.questionVersionId(),
                task.questionNo(),
                task.taskType(),
                task.taskLabel(),
                task.prompt(),
                task.learnerAnswer(),
                task.score() == null ? null : task.score().unavailableView(),
                closedFeedback,
                null,
                List.of(),
                List.of(),
                task.detailAvailable(),
                task.languageTag(),
                ResultPerformanceLevel.unavailableView());
    }

    private static void addValidatedWritingDiagnostics(
            List<ResolvedDiagnostic> target,
            WritingTaskResult task,
            JsonNode findings,
            ResultDetailPolarity polarity,
            Map<String, WritingAnnotationView> annotations,
            Set<String> acceptedFindingIds,
            List<WritingBlankAnswerView> structuredBlankAnswers
    ) {
        if (!findings.isArray()
                || annotations == null
                || acceptedFindingIds == null) {
            return;
        }
        for (int index = 0; index < findings.size(); index++) {
            JsonNode finding = findings.get(index);
            if (finding == null || !finding.isObject()) {
                continue;
            }
            String findingId = finding.path("findingId").asText("").trim();
            String operation = finding.path("operation").asText("").trim();
            int displayNumber = finding.path("index").asInt(0);
            String errorCategory =
                    finding.path("errorCategory").asText("").trim();
            List<String> requirementIds =
                    stringArray(finding.path("requirementIds"));
            if (findingId.isBlank()
                    || displayNumber < 1
                    || errorCategory.isBlank()
                    || requirementIds == null
                    || acceptedFindingIds.contains(findingId)
                    || !validFindingOperation(polarity, operation)) {
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
            if (!WritingDiagnosticContract.validProviderMetadata(
                    finding, criterion, task.taskType(), evidenceScope)) {
                continue;
            }
            String evidence = finding.path("evidence").asText("");
            String evidenceId = nullableText(finding.get("evidenceId"));
            Integer startOffset = nullableInteger(
                    finding.get("startOffset"));
            Integer endOffset = nullableInteger(finding.get("endOffset"));
            Integer occurrenceIndex = nullableInteger(
                    finding.get("occurrenceIndex"));
            Integer occurrenceCount = nullableInteger(
                    finding.get("occurrenceCount"));
            String explanation = finding.path("explanationVi").asText("").trim();
            String correction = finding.path("correction").asText("").trim();
            if (explanation.isBlank()) {
                continue;
            }
            if (evidenceScope == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && (evidence.isBlank()
                    || task.learnerAnswer() == null
                    || evidenceId == null
                    || startOffset == null
                    || endOffset == null
                    || occurrenceIndex == null
                    || occurrenceCount == null)) {
                continue;
            }
            if (evidenceScope == WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER
                    && (!evidence.isEmpty()
                    || evidenceId != null
                    || startOffset != null
                    || endOffset != null
                    || occurrenceIndex != null
                    || occurrenceCount != null)) {
                continue;
            }
            if (polarity == ResultDetailPolarity.STRENGTH && !correction.isEmpty()) {
                continue;
            }
            if (polarity == ResultDetailPolarity.NEEDS_IMPROVEMENT
                    && "REPLACE".equals(operation)
                    && correction.isBlank()) {
                continue;
            }
            if (("REDUNDANT".equals(operation)
                    || "MISSING".equals(operation))
                    && !correction.isBlank()) {
                continue;
            }
            WritingAnnotationView annotation = annotations.get(findingId);
            if (evidenceScope == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && !matchesAuthoritativeAnnotation(
                    task, finding, annotation)) {
                continue;
            }
            if (evidenceScope == WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER
                    && annotation != null) {
                continue;
            }
            WritingDiagnosticDescriptorRegistry.Resolution descriptor =
                    WritingDiagnosticDescriptorRegistry.resolve(
                            criterion,
                            task.taskType(),
                            polarity,
                            authoritativeDiagnosticTarget(
                            task.taskType(),
                            evidenceScope,
                            requirementIds,
                            structuredBlankAnswers));
            String evidenceAvailability = ResultDetailDescriptorRegistry
                    .evidenceAvailability(evidenceScope.name());
            if (descriptor == null || evidenceAvailability == null
                    || !java.util.Objects.equals(
                    descriptor.parentCriterionId(),
                    nullableText(finding.get("scoringCriterionId")))
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
                    findingId,
                    displayNumber,
                    evidenceId,
                    startOffset,
                    endOffset,
                    occurrenceIndex,
                    occurrenceCount,
                    operation,
                    errorCategory,
                    requirementIds,
                    category.code(),
                    category.labelVi(),
                    category.labelKo(),
                    category.stableOrder(),
                    feature.code(),
                    finding.path("subtype").asText(),
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
                    finding.path("impact").asText(),
                    finding.path("frequency").intValue(),
                    finding.path("confidence").decimalValue(),
                    finding.path("observability").asText());
            acceptedFindingIds.add(findingId);
            target.add(new ResolvedDiagnostic(descriptor, diagnostic));
        }
    }

    private static WritingDiagnosticDescriptorRegistry.AuthoritativeTarget
            authoritativeDiagnosticTarget(
            String taskType,
            WritingRubricCriterion.EvidenceScope evidenceScope,
            List<String> requirementIds,
            List<WritingBlankAnswerView> structuredBlankAnswers
    ) {
        if ("Q51".equals(taskType)
                || "Q52".equals(taskType)
                || "Q51_52".equals(taskType)) {
            boolean blankOne = requirementIds != null
                    && requirementIds.contains("CLOZE_BLANK_1_CONTEXT");
            boolean blankTwo = requirementIds != null
                    && requirementIds.contains("CLOZE_BLANK_2_CONTEXT");
            if (blankOne == blankTwo) {
                return null;
            }
            int ordinal = blankOne ? 1 : 2;
            String blankId = structuredBlankAnswers.stream()
                    .filter(blank -> blank.ordinal() == ordinal)
                    .map(WritingBlankAnswerView::blankId)
                    .findFirst()
                    .orElse(null);
            return blankId == null
                    ? null
                    : WritingDiagnosticDescriptorRegistry.blankTarget(
                    blankId, ordinal);
        }
        return evidenceScope
                == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                ? WritingDiagnosticDescriptorRegistry.textSpanTarget()
                : WritingDiagnosticDescriptorRegistry.wholeAnswerTarget();
    }

    private static Map<String, WritingAnnotationView> annotationsByFindingId(
            WritingFeedbackView feedback) {
        if (feedback == null) {
            return null;
        }
        Map<String, WritingAnnotationView> result = new LinkedHashMap<>();
        for (WritingAnnotationView annotation : feedback.annotations()) {
            if (annotation == null
                    || annotation.findingId() == null
                    || annotation.findingId().isBlank()
                    || result.putIfAbsent(
                    annotation.findingId(), annotation) != null) {
                return null;
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static boolean validFindingOperation(
            ResultDetailPolarity polarity,
            String operation) {
        return polarity == ResultDetailPolarity.STRENGTH
                ? "KEEP".equals(operation)
                : Set.of("MISSING", "REPLACE", "REDUNDANT")
                .contains(operation);
    }

    private static boolean matchesAuthoritativeAnnotation(
            WritingTaskResult task,
            JsonNode finding,
            WritingAnnotationView annotation) {
        if (task == null || annotation == null || finding == null) {
            return false;
        }
        String source = Normalizer.normalize(
                task.learnerAnswer() == null ? "" : task.learnerAnswer(),
                Normalizer.Form.NFC);
        Integer start = nullableInteger(finding.get("startOffset"));
        Integer end = nullableInteger(finding.get("endOffset"));
        Integer occurrenceIndex = nullableInteger(
                finding.get("occurrenceIndex"));
        Integer occurrenceCount = nullableInteger(
                finding.get("occurrenceCount"));
        String evidence = finding.path("evidence").asText("");
        if (!finding.path("findingId").asText()
                .equals(annotation.findingId())
                || !finding.path("evidenceId").asText()
                .equals(annotation.evidenceId())
                || !java.util.Objects.equals(start, annotation.start())
                || !java.util.Objects.equals(end, annotation.end())
                || !java.util.Objects.equals(
                occurrenceIndex, annotation.occurrenceIndex())
                || !java.util.Objects.equals(
                occurrenceCount, annotation.occurrenceCount())
                || !finding.path("operation").asText()
                .equals(annotation.operation())
                || !evidence.equals(annotation.evidence())
                || !WritingEvidenceLedgerVerifier.sha256(source)
                .equals(annotation.sourceHash())
                || start == null || end == null
                || start < 0 || end <= start || end > source.length()
                || !source.startsWith(evidence, start)
                || end != start + evidence.length()) {
            return false;
        }
        List<Integer> occurrences = exactOccurrences(source, evidence);
        return occurrenceCount != null
                && occurrenceIndex != null
                && occurrences.size() == occurrenceCount
                && occurrenceIndex <= occurrences.size()
                && occurrences.get(occurrenceIndex - 1).equals(start);
    }

    private static List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual()
                    || value.asText().isBlank()
                    || !unique.add(value.asText())) {
                return null;
            }
            values.add(value.asText());
        }
        return List.copyOf(values);
    }

    private static Integer nullableInteger(JsonNode node) {
        return node != null
                && node.isIntegralNumber()
                && node.canConvertToInt()
                ? node.intValue()
                : null;
    }

    private static List<Integer> exactOccurrences(
            String source,
            String exactText) {
        if (exactText == null || exactText.isEmpty()) {
            return List.of();
        }
        List<Integer> offsets = new ArrayList<>();
        int limit = source.length() - exactText.length();
        for (int offset = 0; offset <= limit; offset++) {
            if (source.startsWith(exactText, offset)) {
                offsets.add(offset);
            }
        }
        return List.copyOf(offsets);
    }

    private static WritingRubricCriterion.EvidenceScope explicitEvidenceScope(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return parseEvidenceScope(node.asText());
    }

    private static WritingRubricCriterion.EvidenceScope parseEvidenceScope(
            String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WritingRubricCriterion.EvidenceScope.valueOf(
                    value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<WritingDiagnosticGroup> diagnosticGroups(
            List<ResolvedDiagnostic> diagnostics,
            WritingTaskResult task,
            List<WritingBlankAnswerView> structuredBlankAnswers
    ) {
        Map<String, Integer> scopedCounts = new LinkedHashMap<>();
        List<ResolvedDiagnostic> scopedDiagnostics = diagnostics.stream()
                .sorted(Comparator
                        .comparingInt((ResolvedDiagnostic row) ->
                                row.definition().feature().category().stableOrder())
                        .thenComparingInt(row ->
                                row.definition().stableOrder())
                        .thenComparing(row -> row.finding().startOffset(),
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(row -> row.finding().findingId()))
                .map(row -> {
                    String key = row.definition().id()
                            + ":" + row.finding().polarity().name();
                    int number = scopedCounts.merge(key, 1, Integer::sum);
                    return new ResolvedDiagnostic(
                            row.definition(),
                            row.finding().withDisplayNumber(number));
                })
                .toList();
        List<CatalogChip> catalog = writingChipCatalog(
                task, structuredBlankAnswers);
        List<WritingDiagnosticGroup> groups = new ArrayList<>();
        for (WritingDiagnosticDescriptorRegistry.CategoryDescriptor category
                : WritingDiagnosticDescriptorRegistry.categories()) {
            List<ResolvedDiagnostic> categoryDiagnostics = scopedDiagnostics.stream()
                    .filter(value -> value.definition().feature().category().code()
                            .equals(category.code()))
                    .toList();
            List<CatalogChip> categoryCatalog = catalog.stream()
                    .filter(value -> value.definition().feature().category().code()
                            .equals(category.code()))
                    .toList();
            if (categoryDiagnostics.isEmpty() && categoryCatalog.isEmpty()) {
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
            List<WritingDiagnosticChip> categoryChips = chips(
                    categoryDiagnostics, categoryCatalog);
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
            List<ResolvedDiagnostic> diagnostics,
            List<CatalogChip> catalog
    ) {
        Map<String, ChipCount> counts = new LinkedHashMap<>();
        for (CatalogChip entry : catalog) {
            String key = entry.definition().id()
                    + ":" + entry.polarity().name();
            counts.put(key, new ChipCount(
                    entry.definition(), entry.polarity(), 0, "NO_FINDING"));
        }
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
                .sorted(Comparator
                        .comparingInt((ChipCount value) -> value.count() == 0 ? 1 : 0)
                        .thenComparingInt(value -> value.definition().stableOrder()))
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

    private static List<CatalogChip> writingChipCatalog(
            WritingTaskResult task,
            List<WritingBlankAnswerView> structuredBlankAnswers
    ) {
        if (task == null) {
            return List.of();
        }
        List<WritingDiagnosticDescriptorRegistry.AuthoritativeTarget> targets;
        if (task.clozeTask()) {
            targets = structuredBlankAnswers == null
                    ? List.of()
                    : structuredBlankAnswers.stream()
                    .map(blank -> WritingDiagnosticDescriptorRegistry.blankTarget(
                            blank.blankId(), blank.ordinal()))
                    .toList();
        } else {
            targets = List.of(
                    WritingDiagnosticDescriptorRegistry.wholeAnswerTarget());
        }
        List<CatalogChip> result = new ArrayList<>();
        for (WritingRubricCriterion criterion
                : WritingRubricCriterion.activeForTask(task.taskType())) {
            ResultDetailPolarity polarity = ResultDetailPolarity.valueOf(
                    criterion.polarity().name());
            for (WritingDiagnosticDescriptorRegistry.AuthoritativeTarget target
                    : targets) {
                WritingDiagnosticDescriptorRegistry.Resolution definition =
                        WritingDiagnosticDescriptorRegistry.resolve(
                                criterion, task.taskType(), polarity, target);
                if (definition != null) {
                    result.add(new CatalogChip(definition, polarity));
                }
            }
        }
        return List.copyOf(result);
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
        List<WritingSentenceRewriteView> rewrites = feedback == null
                ? List.of()
                : feedback.sentenceRewrites().stream()
                        .filter(rewrite -> validRewriteProvenance(
                                feedbackNode, task, rewrite)
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
                        null,
                        "NOT_PROVIDED_BY_CURRENT_EVALUATOR",
                        "Bộ đánh giá hiện tại không tạo bài mẫu độc lập",
                "현재 평가기는 독립 모범 답안을 생성하지 않음"));
    }

    private static boolean validRewriteProvenance(
            JsonNode feedbackNode,
            WritingTaskResult task,
            WritingSentenceRewriteView rewrite) {
        if (feedbackNode == null || task == null || rewrite == null
                || rewrite.evidenceId() == null
                || rewrite.evidenceId().isBlank()
                || rewrite.findingIds().isEmpty()
                || rewrite.original() == null
                || rewrite.original().isBlank()) {
            return false;
        }
        JsonNode evidence = findById(
                feedbackNode.path("evidence_ledger"),
                "evidenceId",
                rewrite.evidenceId());
        if (evidence == null
                || !rewrite.original().equals(
                evidence.path("exactText").asText(null))) {
            return false;
        }
        String source = Normalizer.normalize(
                task.learnerAnswer() == null ? "" : task.learnerAnswer(),
                Normalizer.Form.NFC);
        Integer start = nullableInteger(evidence.get("startOffset"));
        Integer end = nullableInteger(evidence.get("endOffset"));
        if (start == null || end == null
                || start < 0 || end <= start || end > source.length()
                || !source.startsWith(rewrite.original(), start)
                || end != start + rewrite.original().length()) {
            return false;
        }
        for (String findingId : rewrite.findingIds()) {
            JsonNode finding = findById(
                    feedbackNode.path("needs_improvement"),
                    "findingId",
                    findingId);
            if (finding == null
                    || !rewrite.evidenceId().equals(
                    finding.path("evidenceId").asText(null))) {
                return false;
            }
        }
        return true;
    }

    private static JsonNode findById(
            JsonNode rows,
            String field,
            String id) {
        if (rows == null || !rows.isArray()
                || id == null || id.isBlank()) {
            return null;
        }
        JsonNode found = null;
        for (JsonNode row : rows) {
            if (id.equals(row.path(field).asText(null))) {
                if (found != null) {
                    return null;
                }
                found = row;
            }
        }
        return found;
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
                false,
                questionLanguageTag(question),
                ResultPerformanceLevel.unavailableView());
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
            boolean malformedStoredFeedback,
            String analysisStatus) {
        String taskType = taskType(question);
        WritingScoringRubric rubric = WritingScoringPolicy.rubricFor(taskType);
        boolean answered = learnerAnswer != null && !learnerAnswer.isBlank();
        JsonNode usableFeedbackNode = answered ? feedbackNode : null;
        WritingFeedbackView feedback = feedbackMapper.map(usableFeedbackNode);
        WritingFeedbackCompatibilityReader.EntryResult contract =
                compatibilityReader.parseStoredEntry(usableFeedbackNode);
        WritingEvaluationResult evaluation = contract.value();
        boolean scoreContractReady = currentScoreContractMatches(
                taskType, learnerAnswer, usableFeedbackNode, evaluation);
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
                score,
                analysisStatus);
        List<ResultRubricCriterion> visibleCriteria = availability.ready()
                ? parsedCriteria
                : List.of();
        List<WritingAnalysisLens> lenses = isCloze(taskType)
                ? List.of()
                : longFormLenses(
                        taskType,
                        learnerAnswer,
                        visibleCriteria,
                        availability.ready() ? feedback : null);

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
                "ESSAY".equals(question.getQuestionType()),
                questionLanguageTag(question),
                taskPerformanceLevel(score, availability));
    }

    private String questionLanguageTag(PracticeQuestionVersion question) {
        try {
            CanonicalQuestionType type = typeResolver.resolve(question.getQuestionType());
            QuestionContent content = blank(question.getQuestionContentJson())
                    ? contractCodec.adaptLegacyContent(
                            question.getOptionsJson(), question.getQuestionType())
                    : contractCodec.readQuestionContent(
                            question.getQuestionContentJson(), type);
            return content.languageTag() == null ? "ko" : content.languageTag();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return "ko";
        }
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
            WritingScoreAnchorPolicy.ScoreAnchor anchor = score == null
                    ? null
                    : WritingScoreAnchorPolicy.requireAnchor(
                            expected, score.intValueExact());
            String providerOrStoredLevel = text(stored, "performanceLevel");
            if (anchor != null
                    && providerOrStoredLevel != null
                    && !anchor.performanceLevel().name()
                    .equals(providerOrStoredLevel)) {
                // The backend score anchor remains final authority. A stored
                // provider level that contradicts it invalidates this score
                // instead of being silently rendered or repaired.
                score = null;
                anchor = null;
            }
            String criterionFeedback = verifiedCriterionFeedback(
                    feedbackNode, stored, expected.criterionId());
            if ((criterionFeedback == null || criterionFeedback.isBlank())
                    && anchor != null) {
                criterionFeedback = anchor.labelVi();
            }
            result.add(new ResultRubricCriterion(
                    expected.criterionId(),
                    expected.displayName(),
                    score,
                    expectedMaxScore,
                    criterionFeedback,
                    anchor == null
                            ? null : anchor.performanceLevel().name(),
                    anchor == null
                            ? null : anchor.performanceLevel().labelVi(),
                    anchor == null
                            ? null : anchor.performanceLevel().labelKo()));
        }
        return List.copyOf(result);
    }

    private static String verifiedCriterionFeedback(
            JsonNode feedbackNode,
            JsonNode rubricRow,
            String criterionId) {
        if (feedbackNode == null || rubricRow == null
                || !rubricRow.path("findingIds").isArray()) {
            return null;
        }
        LinkedHashSet<String> findingIds = new LinkedHashSet<>();
        rubricRow.path("findingIds").forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) {
                findingIds.add(node.asText());
            }
        });
        if (findingIds.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> verifiedFeedback = new LinkedHashSet<>();
        for (String collection : List.of(
                "strengths", "needs_improvement")) {
            JsonNode findings = feedbackNode.path(collection);
            if (!findings.isArray()) {
                continue;
            }
            for (JsonNode finding : findings) {
                if (findingIds.contains(text(finding, "findingId"))
                        && criterionId.equals(
                        text(finding, "scoringCriterionId"))) {
                    String explanation = text(finding, "explanationVi");
                    if (explanation != null && !explanation.isBlank()) {
                        verifiedFeedback.add(explanation);
                    }
                }
            }
        }
        return verifiedFeedback.isEmpty()
                ? null : String.join(" ", verifiedFeedback);
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

    private static ResultPerformanceLevel resultPerformanceLevel(
            ResultRubricCriterion criterion) {
        if (criterion == null
                || criterion.score() == null
                || criterion.maxScore() == null) {
            return ResultPerformanceLevel.unavailableView();
        }
        try {
            WritingScoreAnchorPolicy.PerformanceLevel level =
                    WritingScoreAnchorPolicy.requirePerformanceLevel(
                            criterion.score().intValueExact(),
                            criterion.maxScore().intValueExact());
            return resultPerformanceLevel(level);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return ResultPerformanceLevel.unavailableView();
        }
    }

    private static ResultPerformanceLevel taskPerformanceLevel(
            ResultScoreSummary score,
            ResultFeedbackAvailability availability) {
        if (availability == null
                || !availability.ready()
                || score == null
                || score.earnedPoints() == null
                || score.possiblePoints() == null) {
            return ResultPerformanceLevel.unavailableView();
        }
        try {
            WritingScoreAnchorPolicy.PerformanceLevel level =
                    WritingScoreAnchorPolicy.requirePerformanceLevel(
                            score.earnedPoints().intValueExact(),
                            score.possiblePoints().intValueExact());
            return resultPerformanceLevel(level);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return ResultPerformanceLevel.unavailableView();
        }
    }

    private static ResultPerformanceLevel resultPerformanceLevel(
            WritingScoreAnchorPolicy.PerformanceLevel level) {
        return new ResultPerformanceLevel(
                level.name(),
                level.labelVi(),
                level.labelKo());
    }

    private static List<WritingAnalysisLens> longFormLenses(
            String taskType,
            String learnerAnswer,
            List<ResultRubricCriterion> criteria,
            WritingFeedbackView feedback) {
        if (criteria.size() < 3) {
            return List.of();
        }
        ResultRubricCriterion content = criteria.get(0);
        ResultRubricCriterion structure = criteria.get(1);
        ResultRubricCriterion language = criteria.get(2);
        return List.of(
                lens(taskType, learnerAnswer,
                        "CONTENT", "Nhiệm vụ và Nội dung", content, feedback,
                        Set.of("CONTENT", "TASK", "CONTEXT"), true),
                lens(taskType, learnerAnswer,
                        "STRUCTURE", "Cấu trúc và mạch lạc", structure, feedback,
                        Set.of("ORGANIZATION", "COHERENCE", "STRUCTURE"), true),
                lens(taskType, learnerAnswer,
                        "VOCABULARY", "Từ vựng và Diễn đạt", language, feedback,
                        Set.of("VOCABULARY", "EXPRESSION", "LEXICAL"), false),
                lens(taskType, learnerAnswer,
                        "GRAMMAR", "Ngữ pháp và Độ chính xác", language, feedback,
                        Set.of("GRAMMAR", "SYNTAX", "SPELLING", "SPACING", "ACCURACY"), false));
    }

    private static WritingAnalysisLens lens(
            String taskType,
            String learnerAnswer,
            String code,
            String label,
            ResultRubricCriterion source,
            WritingFeedbackView feedback,
            Set<String> categories,
            boolean allowCriterionFallback) {
        List<String> evidence = evidence(
                feedback,
                source.criterionId(),
                categories,
                allowCriterionFallback,
                taskType,
                learnerAnswer);
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
            boolean allowCriterionFallback,
            String taskType,
            String learnerAnswer) {
        if (feedback == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addValidatedOverviewEvidence(
                result,
                feedback.strengths(),
                WritingRubricCriterion.Polarity.STRENGTH,
                sourceCriterionId,
                categories,
                allowCriterionFallback,
                taskType,
                learnerAnswer);
        addValidatedOverviewEvidence(
                result,
                feedback.needsImprovement(),
                WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT,
                sourceCriterionId,
                categories,
                allowCriterionFallback,
                taskType,
                learnerAnswer);
        return List.copyOf(result);
    }

    private static void addValidatedOverviewEvidence(
            LinkedHashSet<String> target,
            List<WritingFindingView> findings,
            WritingRubricCriterion.Polarity polarity,
            String sourceCriterionId,
            Set<String> categories,
            boolean allowCriterionFallback,
            String taskType,
            String learnerAnswer) {
        for (WritingFindingView finding : findings) {
            WritingRubricCriterion criterion =
                    WritingRubricCriterion.parse(finding.criterionId());
            WritingRubricCriterion.EvidenceScope evidenceScope =
                    parseEvidenceScope(finding.evidenceScope());
            if (criterion == null
                    || criterion.polarity() != polarity
                    || !criterion.activeForProvider()
                    || !criterion.appliesTo(taskType)
                    || evidenceScope == null
                    || !criterion.supports(evidenceScope)
                    || evidenceScope
                            == WritingRubricCriterion.EvidenceScope.TASK_METADATA
                    || !WritingDiagnosticContract.validProviderMetadata(
                            finding.subtype(),
                            finding.scoringCriterionId(),
                            finding.impact(),
                            finding.frequency(),
                            finding.confidence(),
                            finding.observability(),
                            criterion,
                            taskType,
                            evidenceScope)
                    || !matches(
                            finding.criterionId(),
                            finding.category(),
                            sourceCriterionId,
                            categories,
                            allowCriterionFallback)) {
                continue;
            }
            String evidence = finding.evidence() == null
                    ? ""
                    : finding.evidence();
            String correction = finding.correction() == null
                    ? ""
                    : finding.correction();
            if (finding.explanationVi() == null
                    || finding.explanationVi().isBlank()
                    || (evidenceScope
                            == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && (evidence.isBlank()
                    || learnerAnswer == null
                    || !learnerAnswer.contains(evidence)))
                    || (evidenceScope
                            == WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER
                    && !evidence.isEmpty())
                    || (polarity
                            == WritingRubricCriterion.Polarity.STRENGTH
                    && !correction.isEmpty())
                    || (polarity
                            == WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT
                    && evidenceScope
                            == WritingRubricCriterion.EvidenceScope.TEXT_SPAN
                    && correction.isBlank())) {
                continue;
            }
            addPresent(target, finding.explanationVi());
            addPresent(target, finding.correction());
        }
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
            ResultScoreSummary score,
            String analysisStatus) {
        if (!answered) {
            return new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Chưa có bài viết để đánh giá", 0, 0);
        }
        if (malformedStoredFeedback) {
            return new ResultFeedbackAvailability(
                    "FAILED", "Dữ liệu đánh giá bài viết không hợp lệ", 0, 1);
        }
        if (feedbackNode == null || !feedbackNode.isObject()) {
            if (PracticeAttempt.ANALYSIS_QUEUED.equals(analysisStatus)
                    || PracticeAttempt.ANALYSIS_PROCESSING.equals(
                            analysisStatus)) {
                return new ResultFeedbackAvailability(
                        "PENDING", "Đang chờ đánh giá", 0, 1);
            }
            if (PracticeAttempt.ANALYSIS_FAILED.equals(analysisStatus)) {
                return new ResultFeedbackAvailability(
                        "FAILED", "Không thể hoàn tất đánh giá nhiệm vụ này", 0, 1);
            }
            if (PracticeAttempt.ANALYSIS_UNAVAILABLE.equals(
                    analysisStatus)) {
                return new ResultFeedbackAvailability(
                        "UNAVAILABLE",
                        "Dịch vụ đánh giá hiện không khả dụng",
                        0,
                        1);
            }
            return new ResultFeedbackAvailability(
                    "UNAVAILABLE", "Nhiệm vụ này chưa có đánh giá khả dụng", 0, 1);
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
        if (normalizedStatus.contains("FAILED")
                || normalizedStatus.contains("ERROR")
                || (normalizedStatus.contains("INVALID")
                && !scoreContractReady)) {
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
                task.taskType(), task.learnerAnswer(),
                feedbackNode, contract.value());
    }

    private static boolean currentScoreContractMatches(
            String taskType,
            String learnerAnswer,
            JsonNode feedbackNode,
            WritingEvaluationResult evaluation
    ) {
        if (taskType == null
                || feedbackNode == null
                || !feedbackNode.isObject()
                || evaluation == null
                || !evaluation.scoreAvailableFlag()
                || !hasExactCurrentScoreEnvelopeShape(feedbackNode)) {
            return false;
        }
        WritingScoringRubric rubric = WritingScoringPolicy.rubricFor(taskType);
        BigDecimal expectedMaximum =
                BigDecimal.valueOf(rubric.totalMaxScore());
        return taskType.equals(evaluation.taskType())
                && CURRENT_SCORING_CONTRACT.equals(
                        text(feedbackNode, "scoring_contract"))
                && WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID.equals(
                        evaluation.policyBundleId())
                && CURRENT_EVALUATION_ENGINE.equals(evaluation.engine())
                && WritingEvidenceLedgerVerifier.CONTRACT_VERSION.equals(
                text(feedbackNode, "ledger_contract_version"))
                && WritingScoreAnchorPolicy.VERSION.equals(
                text(feedbackNode, "score_anchor_version"))
                && WritingTaskRequirementPolicy.VERSION.equals(
                text(feedbackNode, "task_requirement_version"))
                && WritingEvidenceLedgerVerifier.SOURCE_NORMALIZATION.equals(
                text(feedbackNode, "source_normalization"))
                && WritingEvidenceLedgerVerifier.sha256(
                Normalizer.normalize(
                        learnerAnswer == null ? "" : learnerAnswer,
                        Normalizer.Form.NFC)).equals(
                text(feedbackNode, "source_hash"))
                && WritingAssessmentPolicyBundle
                        .hasExactCurrentScoreProvenance(evaluation)
                && evaluation.rawScore() != null
                && evaluation.rawScoreMax() != null
                && evaluation.rawScore().signum() >= 0
                && evaluation.rawScore()
                        .compareTo(evaluation.rawScoreMax()) <= 0
                && evaluation.rawScoreMax()
                        .compareTo(expectedMaximum) == 0;
    }

    private static boolean hasExactCurrentScoreEnvelopeShape(
            JsonNode feedbackNode
    ) {
        return feedbackNode.path("raw_score").isNumber()
                && feedbackNode.path("raw_score_max").isNumber()
                && feedbackNode.path("score_available").isBoolean()
                && feedbackNode.path("score_available").asBoolean()
                && feedbackNode.path("task_type").isTextual()
                && feedbackNode.path("scoring_contract").isTextual()
                && feedbackNode.path("policy_bundle_id").isTextual()
                && feedbackNode.path("ledger_contract_version").isTextual()
                && feedbackNode.path("score_anchor_version").isTextual()
                && feedbackNode.path("task_requirement_version").isTextual()
                && feedbackNode.path("source_normalization").isTextual()
                && feedbackNode.path("source_hash").isTextual()
                && feedbackNode.path("source_hash").asText().length() == 64
                && feedbackNode.path("task_coverage").isArray()
                && feedbackNode.path("evidence_ledger").isArray()
                && feedbackNode.path("annotations").isArray()
                && feedbackNode.path("engine").isTextual()
                && feedbackNode.path("evaluation_status").isTextual()
                && feedbackNode.path("evaluation_source").isTextual()
                && feedbackNode.path("evaluation_reason").isTextual()
                && feedbackNode.path("evaluation_retryable").isBoolean();
    }

    private static String nullableText(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
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

    private record ResolvedDiagnostic(
            WritingDiagnosticDescriptorRegistry.Resolution definition,
            WritingDiagnosticFinding finding
    ) {
    }

    private record ResolvedTextAnnotation(
            int start,
            int end,
            String annotationId,
            int displayNumber,
            String evidenceId,
            ResultDetailPolarity polarity,
            String categoryCode,
            String criterionId,
            String explanationVi,
            String correctionKo,
            String featureId
    ) {
    }

    private record BlankAnnotationCandidate(
            int start,
            int end,
            WritingDiagnosticFinding finding
    ) {
    }

    private record FindingAnnotationCandidate(
            int start,
            int end,
            WritingDiagnosticFinding finding
    ) {
    }

    private record CatalogChip(
            WritingDiagnosticDescriptorRegistry.Resolution definition,
            ResultDetailPolarity polarity
    ) {
    }

    private record ChipCount(
            WritingDiagnosticDescriptorRegistry.Resolution definition,
            ResultDetailPolarity polarity,
            int count,
            String evidenceAvailability
    ) {
        private ChipCount incremented(String nextEvidenceAvailability) {
            String merged = count == 0
                    ? nextEvidenceAvailability
                    : evidenceAvailability.equals(nextEvidenceAvailability)
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
