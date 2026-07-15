package com.ksh.features.practice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.dto.PracticeDtos;
import com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionGroupRow;
import com.ksh.features.practice.dto.PracticeDtos.ExampleBox;
import com.ksh.features.practice.ai.writing.WritingEvaluationClient;
import com.ksh.features.practice.ai.writing.WritingEvaluationResult;
import com.ksh.features.practice.ai.writing.WritingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.writing.WritingFeedbackViewMapper;
import com.ksh.features.practice.ai.writing.WritingScoreMatrix;
import com.ksh.features.practice.ai.writing.WritingScoringPolicy;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationApplicationService;
import com.ksh.features.practice.ai.speaking.SpeakingFeedbackCompatibilityReader;
import com.ksh.features.practice.ai.speaking.SpeakingFeedbackViewMapper;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.AssessmentScoreResult;
import com.ksh.features.practice.assessment.AssessmentScoreStatus;
import com.ksh.features.practice.assessment.AssessmentScoringEngine;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.PracticeSectionDelivery;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAnswerExplanationRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAnswerReviewRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionOptionRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultSummary;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeTestRow;
import com.ksh.features.practice.dto.PracticeDtos.ReadingListeningResultView;
import com.ksh.features.practice.dto.PracticeDtos.PerformanceByTypeRow;
import com.ksh.features.practice.dto.PracticeDtos.ReviewGroupRow;
import com.ksh.features.practice.dto.PracticeDtos.ReviewQuestionRow;
import com.ksh.features.practice.dto.PracticeDtos.EliminatedOptionExplanation;
import com.ksh.features.practice.dto.PracticeDtos.SectionResultRow;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingFindingView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingQuestionFeedbackRow;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingRubricScoreView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeTest;
import com.ksh.common.storage.AudioStorageService;
import com.ksh.entities.PracticeSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionFeedbackRow;

@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);
    private static final String SPEAKING_MIXED_CONTRACT = "speaking_mixed_v1";
    private static final String SPEAKING_AI_CONTRACT = "speaking_ai_v1";
    private static final String SPEAKING_MIXED_CONTRACT_FIELD = "_contract";
    private static final String SPEAKING_MIXED_SPEAKING_FIELD = "speaking_feedback_by_question";
    private static final String SPEAKING_MIXED_ESSAY_FIELD = "essay_feedback_by_question";
    private static final Pattern MARKDOWN_IMAGE_PATTERN =
            Pattern.compile("!\\[[^\\]]*]\\(([^)]+)\\)");
    private static final Pattern MATERIAL_CONTENT_REFERENCE_PATTERN =
            Pattern.compile("^/practice/materials/[1-9][0-9]*/content$");

    private final PracticeSetRepository setRepository;
    private final PracticeQuestionRepository questionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final PracticeTestRepository testRepository;
    private final WritingEvaluationClient evaluationClient;
    private final WritingFeedbackCompatibilityReader writingFeedbackReader;
    private final WritingFeedbackViewMapper writingFeedbackViewMapper;
    private final SpeakingFeedbackCompatibilityReader speakingFeedbackReader;
    private final SpeakingFeedbackViewMapper speakingFeedbackViewMapper;
    private SpeakingEvaluationApplicationService speakingEvaluationApplicationService;
    private PracticeSpeakingMediaService speakingMediaService;
    private final ReadingListeningExplanationService readingListeningExplanationService;
    private final AudioStorageService audioStorageService;
    private PracticePublishedVersionService publishedVersionService;
    private final ObjectMapper objectMapper;
    private final QuestionTypeResolver questionTypeResolver;
    private final AssessmentContractCodec assessmentContractCodec;
    private final AssessmentScoringEngine assessmentScoringEngine;
    private final TransactionTemplate readTransactionTemplate;
    private final TransactionTemplate nonTransactionalTemplate;
    private final TransactionTemplate writeTransactionTemplate;

    @Autowired
    public PracticeService(PracticeSetRepository setRepository,
                           PracticeQuestionRepository questionRepository,
                           PracticeQuestionGroupRepository groupRepository,
                           PracticeSectionRepository sectionRepository,
                           PracticeAttemptRepository attemptRepository,
                           PracticeTestRepository testRepository,
                           WritingEvaluationClient evaluationClient,
                           WritingFeedbackCompatibilityReader writingFeedbackReader,
                           WritingFeedbackViewMapper writingFeedbackViewMapper,
                           SpeakingFeedbackCompatibilityReader speakingFeedbackReader,
                           SpeakingFeedbackViewMapper speakingFeedbackViewMapper,
                           ReadingListeningExplanationService readingListeningExplanationService,
                           AudioStorageService audioStorageService,
                           PracticePublishedVersionService publishedVersionService,
                           ObjectMapper objectMapper,
                           PlatformTransactionManager transactionManager) {
        this.setRepository = setRepository;
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
        this.sectionRepository = sectionRepository;
        this.attemptRepository = attemptRepository;
        this.testRepository = testRepository;
        this.evaluationClient = evaluationClient;
        this.writingFeedbackReader = writingFeedbackReader;
        this.writingFeedbackViewMapper = writingFeedbackViewMapper;
        this.speakingFeedbackReader = speakingFeedbackReader;
        this.speakingFeedbackViewMapper = speakingFeedbackViewMapper;
        this.readingListeningExplanationService = readingListeningExplanationService;
        this.audioStorageService = audioStorageService;
        this.publishedVersionService = publishedVersionService;
        this.objectMapper = objectMapper;
        this.questionTypeResolver = new QuestionTypeResolver();
        this.assessmentContractCodec = new AssessmentContractCodec(objectMapper, questionTypeResolver);
        this.assessmentScoringEngine = new AssessmentScoringEngine();
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
        this.readTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.nonTransactionalTemplate = new TransactionTemplate(transactionManager);
        this.nonTransactionalTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        this.writeTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Autowired(required = false)
    void setSpeakingEvaluationApplicationService(SpeakingEvaluationApplicationService speakingEvaluationApplicationService) {
        this.speakingEvaluationApplicationService = speakingEvaluationApplicationService;
    }

    @Autowired(required = false)
    void setSpeakingMediaService(PracticeSpeakingMediaService speakingMediaService) {
        this.speakingMediaService = speakingMediaService;
    }

    void setPublishedVersionServiceForTests(PracticePublishedVersionService publishedVersionService) {
        this.publishedVersionService = publishedVersionService;
    }

    PracticeService(PracticeSetRepository setRepository,
                    PracticeQuestionRepository questionRepository,
                    PracticeQuestionGroupRepository groupRepository,
                    PracticeSectionRepository sectionRepository,
                    PracticeAttemptRepository attemptRepository,
                    PracticeTestRepository testRepository,
                    WritingEvaluationClient evaluationClient,
                    ReadingListeningExplanationService readingListeningExplanationService,
                    AudioStorageService audioStorageService,
                    ObjectMapper objectMapper) {
        this.setRepository = setRepository;
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
        this.sectionRepository = sectionRepository;
        this.attemptRepository = attemptRepository;
        this.testRepository = testRepository;
        this.evaluationClient = evaluationClient;
        this.writingFeedbackReader = new WritingFeedbackCompatibilityReader(objectMapper);
        this.writingFeedbackViewMapper = new WritingFeedbackViewMapper();
        this.speakingFeedbackReader = new SpeakingFeedbackCompatibilityReader(objectMapper, new com.ksh.features.practice.ai.speaking.SpeakingEvaluationNormalizer());
        this.speakingFeedbackViewMapper = new SpeakingFeedbackViewMapper();
        this.readingListeningExplanationService = readingListeningExplanationService;
        this.audioStorageService = audioStorageService;
        this.publishedVersionService = null;
        this.objectMapper = objectMapper;
        this.questionTypeResolver = new QuestionTypeResolver();
        this.assessmentContractCodec = new AssessmentContractCodec(objectMapper, questionTypeResolver);
        this.assessmentScoringEngine = new AssessmentScoringEngine();
        this.readTransactionTemplate = null;
        this.nonTransactionalTemplate = null;
        this.writeTransactionTemplate = null;
    }

    @Transactional(readOnly = true)
    public PracticeSetView getPractice(Long setId) {
        PracticeSet set = loadPublished(setId);
        List<PracticeQuestionGroup> dbGroups = groupRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        List<PracticeQuestion> dbQuestions = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        List<PracticeTestRow> tests = testRepository.findBySetIdOrderByDisplayOrderAsc(setId)
                .stream()
                .map(PracticeService::toTestRow)
                .toList();

        List<PracticeQuestionGroupRow> groups;
        if (dbGroups.isEmpty()) {
            groups = fallbackGrouping(dbQuestions);
        } else {
            groups = dbGroups.stream().map(g -> {
                List<PracticeQuestionRow> qRows = dbQuestions.stream()
                        .filter(q -> g.getId().equals(q.getGroupId()))
                        .map(this::toQuestionRow)
                        .toList();
                return toGroupRow(g, qRows);
            }).toList();
        }

        return new PracticeSetView(toSetRow(set), groups, List.of(), tests);
    }

    @Transactional(readOnly = true)
    public PracticeSetView getPracticeSummary(Long setId) {
        PracticeSet set = loadPublished(setId);
        List<PracticeTestRow> tests = testRepository.findBySetIdOrderByDisplayOrderAsc(setId)
                .stream()
                .map(PracticeService::toTestRow)
                .toList();
        return new PracticeSetView(toSetRow(set), List.of(), List.of(), tests);
    }

    public Long reEvaluate(Long attemptId, Long userId) {
        WritingGradingSnapshot snapshot = executeRead(() -> loadWritingReEvaluationSnapshot(attemptId, userId));
        if (snapshot != null) {
            WritingGradingResult result = executeNonTransactional(() -> gradeWritingSnapshot(snapshot, true));
            return executeWrite(() -> persistWritingReEvaluationResult(snapshot, result));
        }
        NonWritingEssayGradingSnapshot essaySnapshot =
                executeRead(() -> loadNonWritingEssayReEvaluationSnapshot(attemptId, userId));
        if (essaySnapshot != null) {
            NonWritingEssayGradingResult result =
                    executeNonTransactional(() -> gradeNonWritingEssaySnapshot(essaySnapshot, true));
            return executeWrite(() -> persistNonWritingEssayReEvaluationResult(essaySnapshot, result));
        }
        SpeakingGradingSnapshot speakingSnapshot =
                executeRead(() -> loadSpeakingReEvaluationSnapshot(attemptId, userId));
        if (speakingSnapshot != null) {
            SpeakingGradingResult result =
                    executeNonTransactional(() -> gradeSpeakingSnapshot(speakingSnapshot));
            return executeWrite(() -> persistSpeakingGradingResult(speakingSnapshot, result, false));
        }
        return executeWrite(() -> reEvaluateInTransaction(attemptId, userId));
    }

    public Long reEvaluateQuestion(Long attemptId, Long questionId, Long userId) {
        WritingQuestionReEvaluationSnapshot snapshot = executeRead(
                () -> loadWritingQuestionReEvaluationSnapshot(attemptId, questionId, userId));
        WritingGradingResult result = executeNonTransactional(() -> gradeWritingQuestionSnapshot(snapshot));
        return executeWrite(() -> persistWritingQuestionReEvaluationResult(snapshot, result));
    }

    private Long reEvaluateInTransaction(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));

        if (!attempt.getSetId().equals(section.getSetId()) ||
            !attempt.getTestId().equals(section.getTestId()) ||
            !attempt.getSkill().equals(section.getSkill())) {
            throw new IllegalArgumentException("Section metadata mismatch with attempt");
        }

        loadPublished(attempt.getSetId());

        List<QuestionSnapshot> sectionQuestions = loadQuestionSnapshots(attempt, section.getId());

        Map<String, String> submittedAnswers = readAnswers(attempt.getAnswersJson());

        if ("WRITING".equals(attempt.getSkill())) {
            throw new IllegalStateException("Writing attempt must use snapshot grading path.");
        }

        BigDecimal earnedPoints = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        Map<String, JsonNode> speakingFeedbackMap = new LinkedHashMap<>();
        String aiFeedback = null;

        for (QuestionSnapshot q : sectionQuestions) {
            total = total.add(q.points());
            String answer = submittedAnswers.getOrDefault(String.valueOf(q.questionId()), "").trim();

            Optional<AssessmentScoreResult> objectiveScore = scoreObjective(q, answer);
            if (objectiveScore.isPresent()) {
                earnedPoints = earnedPoints.add(objectiveScore.get().earnedPoints());
            } else if (PracticeQuestion.TYPE_ESSAY.equals(q.questionType())) {
                throw new IllegalStateException("Essay attempt must use snapshot grading path.");
            } else if (PracticeQuestion.TYPE_SPEAKING.equals(q.questionType())) {
                String perQuestionFeedback = mockSpeakingFeedback(q.prompt(), answer);
                speakingFeedbackMap.put(String.valueOf(q.questionId()), readFeedbackObject(perQuestionFeedback));
                earnedPoints = earnedPoints.add(earnedSpeakingPoints(q.points(), extractAiScore(perQuestionFeedback)));
            } else {
                throw new IllegalStateException("Unsupported question type for question ID "
                        + q.questionId() + ": " + q.questionType());
            }
        }
        BigDecimal score = speakingFeedbackMap.isEmpty() ? earnedPoints : toWritingAttemptPercentage(earnedPoints, total);
        if (!speakingFeedbackMap.isEmpty()) {
            aiFeedback = writeJson(speakingFeedbackMap);
        }

        if (aiFeedback == null) {
            attempt.markSubmitted(score, total, writeJson(submittedAnswers));
        } else {
            attempt.markGraded(score, total, writeJson(submittedAnswers), aiFeedback);
        }

        attemptRepository.save(attempt);
        log.info("[PracticeService] Re-evaluated PracticeAttempt id={} score={} / {}", attempt.getId(), score, total);
        return attempt.getId();
    }

    @Transactional(readOnly = true)
    public PracticeResultView getResult(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        rejectDiscardedAttempt(attempt);
        requireSubmittedForExplanation(attempt);
        PracticeSet set = setRepository.findById(attempt.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));

        Optional<PracticeVersionSnapshot> lockedSnapshot = versionSnapshot(attempt);
        List<PracticeQuestionGroupRow> groupRows = groupRowsForAttempt(attempt, section);
        List<PracticeQuestionRow> questions = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .toList();

        List<PracticeAnswerExplanationRow> answerExplanations = usesAnswerExplanations(set)
                ? answerExplanationRows(attempt.getAiFeedbackJson(), questions, attempt.getAnswersJson())
                : List.of();
        List<PracticeAnswerReviewRow> answerReviews = answerReviewRows(questions, attempt.getAnswersJson());

        List<PracticeQuestionFeedbackRow> questionFeedbacks = "WRITING".equals(section.getSkill())
                ? lockedSnapshot.isPresent()
                    ? buildQuestionFeedbackRowsFromRows(questions, attempt.getAnswersJson(), attempt.getAiFeedbackJson())
                    : buildQuestionFeedbackRows(liveSectionQuestions(attempt, section), attempt.getAnswersJson(), attempt.getAiFeedbackJson())
                : List.of();
        List<SpeakingQuestionFeedbackRow> speakingQuestionFeedbacks = "SPEAKING".equals(section.getSkill())
                ? lockedSnapshot.isPresent()
                    ? buildSpeakingQuestionFeedbackRowsFromRows(questions, attempt.getAnswersJson(), attempt.getAiFeedbackJson())
                    : buildSpeakingQuestionFeedbackRows(liveSectionQuestions(attempt, section), attempt.getAnswersJson(), attempt.getAiFeedbackJson())
                : List.of();

        return new PracticeResultView(
                attempt.getId(),
                attempt.getTestId(),
                lockedSnapshot.map(PracticeVersionSnapshot::setVersion).map(PracticeService::toSetRow).orElseGet(() -> toSetRow(set)),
                attempt.getScore(),
                attempt.getTotalPoints(),
                "SPEAKING".equals(section.getSkill())
                        ? percentageLabel(attempt.getScore())
                        : scoreLabel(attempt.getScore(), attempt.getTotalPoints()),
                attempt.getAnswersJson(),
                attempt.getAiFeedbackJson(),
                questions,
                answerReviews,
                answerExplanations,
                questionFeedbacks,
                speakingQuestionFeedbacks
        );
    }

    public List<SpeakingQuestionFeedbackRow> buildSpeakingQuestionFeedbackRows(
            List<PracticeQuestion> questions,
            String answersJson,
            String aiFeedbackJson
    ) {
        Map<String, String> answers = readAnswers(answersJson);
        JsonNode rootNode = null;
        if (aiFeedbackJson != null && !aiFeedbackJson.isBlank()) {
            try {
                rootNode = objectMapper.readTree(aiFeedbackJson);
            } catch (Exception e) {
                log.warn("[PracticeService] Failed to parse speaking aiFeedbackJson exception={}",
                        exceptionCategory(e));
            }
        }

        boolean speakingAiEnvelope = isSpeakingAiEnvelope(rootNode);
        boolean mixedEnvelope = isSpeakingMixedEnvelope(rootNode);
        List<PracticeQuestion> rowQuestions = questions.stream()
                .filter(q -> speakingAiEnvelope || mixedEnvelope || PracticeQuestion.TYPE_SPEAKING.equals(q.getQuestionType()))
                .sorted(QUESTION_ORDER)
                .toList();
        List<PracticeQuestion> speakingQuestions = rowQuestions.stream()
                .filter(q -> PracticeQuestion.TYPE_SPEAKING.equals(q.getQuestionType()))
                .toList();
        JsonNode aiSpeakingFeedback = speakingAiEnvelope ? rootNode.path(SPEAKING_MIXED_SPEAKING_FIELD) : null;
        JsonNode mixedSpeakingFeedback = mixedEnvelope ? rootNode.path(SPEAKING_MIXED_SPEAKING_FIELD) : null;
        JsonNode mixedEssayFeedback = mixedEnvelope ? rootNode.path(SPEAKING_MIXED_ESSAY_FIELD) : null;
        boolean canonicalMap = !speakingAiEnvelope && !mixedEnvelope && isCanonicalSpeakingFeedbackMap(rootNode, speakingQuestions);
        boolean unknownContract = rootNode != null
                && rootNode.isObject()
                && rootNode.has(SPEAKING_MIXED_CONTRACT_FIELD)
                && !speakingAiEnvelope
                && !mixedEnvelope;
        boolean legacyGlobal = rootNode != null && rootNode.isObject() && !speakingAiEnvelope && !mixedEnvelope && !unknownContract && !canonicalMap;

        List<SpeakingQuestionFeedbackRow> rows = new ArrayList<>();
        for (PracticeQuestion q : rowQuestions) {
            JsonNode feedbackNode = null;
            com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView legacyEssayFeedback = null;
            boolean legacyApplied = false;
            if (speakingAiEnvelope && PracticeQuestion.TYPE_SPEAKING.equals(q.getQuestionType())) {
                JsonNode candidate = aiSpeakingFeedback == null ? null : aiSpeakingFeedback.get(String.valueOf(q.getId()));
                feedbackNode = candidate != null && candidate.isObject() ? candidate : null;
            } else if (mixedEnvelope && PracticeQuestion.TYPE_SPEAKING.equals(q.getQuestionType())) {
                JsonNode candidate = mixedSpeakingFeedback == null ? null : mixedSpeakingFeedback.get(String.valueOf(q.getId()));
                feedbackNode = candidate != null && candidate.isObject() ? candidate : null;
            } else if (mixedEnvelope && PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType())) {
                JsonNode candidate = mixedEssayFeedback == null ? null : mixedEssayFeedback.get(String.valueOf(q.getId()));
                legacyEssayFeedback = writingFeedbackViewMapper.map(candidate != null && candidate.isObject() ? candidate : null);
            } else if (canonicalMap) {
                JsonNode candidate = rootNode.get(String.valueOf(q.getId()));
                feedbackNode = candidate != null && candidate.isObject() ? candidate : null;
            } else if (legacyGlobal) {
                feedbackNode = rootNode;
                legacyApplied = true;
            }
            SpeakingFeedbackView feedback = speakingFeedbackView(feedbackNode);
            rows.add(new SpeakingQuestionFeedbackRow(
                    q.getId(),
                    q.getQuestionNo(),
                    q.getQuestionType(),
                    q.getPrompt(),
                    answers.getOrDefault(String.valueOf(q.getId()), ""),
                    feedback,
                    legacyEssayFeedback,
                    feedback != null || legacyEssayFeedback != null,
                    legacyApplied
            ));
        }
        return rows;
    }

    private List<SpeakingQuestionFeedbackRow> buildSpeakingQuestionFeedbackRowsFromRows(
            List<PracticeQuestionRow> questions,
            String answersJson,
            String aiFeedbackJson
    ) {
        Map<String, String> answers = readAnswers(answersJson);
        JsonNode rootNode = null;
        if (aiFeedbackJson != null && !aiFeedbackJson.isBlank()) {
            try {
                rootNode = objectMapper.readTree(aiFeedbackJson);
            } catch (Exception e) {
                log.warn("[PracticeService] Failed to parse versioned speaking aiFeedbackJson exception={}",
                        exceptionCategory(e));
            }
        }
        boolean speakingAiEnvelope = isSpeakingAiEnvelope(rootNode);
        boolean mixedEnvelope = isSpeakingMixedEnvelope(rootNode);
        JsonNode aiSpeakingFeedback = speakingAiEnvelope ? rootNode.path(SPEAKING_MIXED_SPEAKING_FIELD) : null;
        JsonNode mixedSpeakingFeedback = mixedEnvelope ? rootNode.path(SPEAKING_MIXED_SPEAKING_FIELD) : null;
        JsonNode mixedEssayFeedback = mixedEnvelope ? rootNode.path(SPEAKING_MIXED_ESSAY_FIELD) : null;
        boolean legacyGlobal = rootNode != null && rootNode.isObject() && !speakingAiEnvelope && !mixedEnvelope;

        List<SpeakingQuestionFeedbackRow> rows = new ArrayList<>();
        for (PracticeQuestionRow q : questions.stream()
                .filter(q -> speakingAiEnvelope || mixedEnvelope || PracticeQuestion.TYPE_SPEAKING.equals(q.questionType()))
                .sorted(Comparator.comparing(PracticeQuestionRow::questionNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(PracticeQuestionRow::id, Comparator.nullsLast(Long::compareTo)))
                .toList()) {
            JsonNode feedbackNode = null;
            com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView legacyEssayFeedback = null;
            boolean legacyApplied = false;
            if (speakingAiEnvelope && PracticeQuestion.TYPE_SPEAKING.equals(q.questionType())) {
                JsonNode candidate = aiSpeakingFeedback == null ? null : aiSpeakingFeedback.get(String.valueOf(q.id()));
                feedbackNode = candidate != null && candidate.isObject() ? candidate : null;
            } else if (mixedEnvelope && PracticeQuestion.TYPE_SPEAKING.equals(q.questionType())) {
                JsonNode candidate = mixedSpeakingFeedback == null ? null : mixedSpeakingFeedback.get(String.valueOf(q.id()));
                feedbackNode = candidate != null && candidate.isObject() ? candidate : null;
            } else if (mixedEnvelope && PracticeQuestion.TYPE_ESSAY.equals(q.questionType())) {
                JsonNode candidate = mixedEssayFeedback == null ? null : mixedEssayFeedback.get(String.valueOf(q.id()));
                legacyEssayFeedback = writingFeedbackViewMapper.map(candidate != null && candidate.isObject() ? candidate : null);
            } else if (legacyGlobal) {
                JsonNode candidate = rootNode.get(String.valueOf(q.id()));
                feedbackNode = candidate != null && candidate.isObject() ? candidate : rootNode;
                legacyApplied = candidate == null;
            }
            SpeakingFeedbackView feedback = speakingFeedbackView(feedbackNode);
            rows.add(new SpeakingQuestionFeedbackRow(
                    q.id(),
                    q.questionNo(),
                    q.questionType(),
                    q.prompt(),
                    answers.getOrDefault(String.valueOf(q.id()), ""),
                    feedback,
                    legacyEssayFeedback,
                    feedback != null || legacyEssayFeedback != null,
                    legacyApplied
            ));
        }
        return rows;
    }

    private boolean isSpeakingMixedEnvelope(JsonNode rootNode) {
        return rootNode != null
                && rootNode.isObject()
                && SPEAKING_MIXED_CONTRACT.equals(rootNode.path(SPEAKING_MIXED_CONTRACT_FIELD).asText(null));
    }

    private boolean isSpeakingAiEnvelope(JsonNode rootNode) {
        return rootNode != null
                && rootNode.isObject()
                && SPEAKING_AI_CONTRACT.equals(rootNode.path(SPEAKING_MIXED_CONTRACT_FIELD).asText(null));
    }

    public String speakingAiFeedbackEnvelope(Map<Long, SpeakingEvaluationResult> feedbackByQuestionId) {
        com.fasterxml.jackson.databind.node.ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put(SPEAKING_MIXED_CONTRACT_FIELD, SPEAKING_AI_CONTRACT);
        com.fasterxml.jackson.databind.node.ObjectNode byQuestion = envelope.putObject(SPEAKING_MIXED_SPEAKING_FIELD);
        if (feedbackByQuestionId != null) {
            feedbackByQuestionId.forEach((questionId, feedback) -> {
                if (questionId != null && feedback != null) {
                    byQuestion.set(String.valueOf(questionId), objectMapper.valueToTree(feedback));
                }
            });
        }
        return writeJson(envelope);
    }

    private boolean isCanonicalSpeakingFeedbackMap(JsonNode rootNode, List<PracticeQuestion> speakingQuestions) {
        if (rootNode == null || !rootNode.isObject() || speakingQuestions.isEmpty()) {
            return false;
        }
        boolean hasQuestionEntry = false;
        for (PracticeQuestion q : speakingQuestions) {
            JsonNode entry = rootNode.get(String.valueOf(q.getId()));
            if (entry != null && entry.isObject()) {
                hasQuestionEntry = true;
            }
        }
        return hasQuestionEntry;
    }

    private SpeakingFeedbackView speakingFeedbackView(JsonNode node) {
        if (node == null || !node.isObject() || !hasSpeakingFeedbackContent(node)) {
            return null;
        }
        return speakingFeedbackViewMapper.map(speakingFeedbackReader.read(node));
    }

    private BigDecimal speakingPercentage(JsonNode node) {
        BigDecimal percentage = decimalOrNull(node, "percentage");
        if (percentage != null) {
            return clamp(percentage, BigDecimal.ZERO, BigDecimal.valueOf(100));
        }
        BigDecimal legacyBand = decimalOrNull(node, "score");
        return legacyBand == null ? null : WritingScoreMatrix.toHundredPointScale(legacyBand.doubleValue());
    }

    private boolean hasSpeakingFeedbackContent(JsonNode node) {
        return node.has("score")
                || node.has("percentage")
                || node.has("summary")
                || node.has("summary_vi")
                || node.has("rubric_scores")
                || node.has("strengths")
                || node.has("needs_improvement")
                || node.has("sample_answer")
                || node.has("corrected_version")
                || node.has("engine")
                || node.has("source")
                || node.has("evaluationStatus")
                || node.has("evaluation_status")
                || node.has("overallSummary")
                || node.has("overall_summary")
                || node.has("criterionFeedback")
                || node.has("criterion_feedback")
                || node.has("actionPlan")
                || node.has("action_plan");
    }

    private List<SpeakingRubricScoreView> speakingRubricScores(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<SpeakingRubricScoreView> rows = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isObject()) {
                rows.add(new SpeakingRubricScoreView(
                        textOrNull(item, "name"),
                        speakingPercentage(item),
                        textOrNull(item, "feedback")
                ));
            }
        }
        return rows;
    }

    private List<SpeakingFindingView> speakingFindings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<SpeakingFindingView> rows = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isObject()) {
                rows.add(new SpeakingFindingView(
                        textOrNull(item, "criterionId"),
                        textOrNull(item, "explanationVi"),
                        textOrNull(item, "correction")
                ));
            }
        }
        return rows;
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.decimalValue();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText("");
        return text.isBlank() ? null : text;
    }

    public static final Comparator<PracticeQuestion> QUESTION_ORDER =
            Comparator.comparing(PracticeQuestion::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(PracticeQuestion::getQuestionNo, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(PracticeQuestion::getId, Comparator.nullsLast(Long::compareTo));
    public final Comparator<PracticeQuestion> questionComparator = QUESTION_ORDER;

    public List<PracticeQuestionFeedbackRow> buildQuestionFeedbackRows(
            List<PracticeQuestion> questions,
            String answersJson,
            String aiFeedbackJson
    ) {
        Map<String, String> answers = new LinkedHashMap<>();
        if (answersJson != null && !answersJson.isBlank()) {
            try {
                answers = objectMapper.readValue(answersJson, new TypeReference<Map<String, String>>() {});
            } catch (Exception e) {
                log.warn("[PracticeService] Failed to parse answersJson in buildQuestionFeedbackRows exception={}",
                        exceptionCategory(e));
            }
        }

        com.fasterxml.jackson.databind.JsonNode rootNode = null;
        if (aiFeedbackJson != null && !aiFeedbackJson.isBlank()) {
            try {
                rootNode = objectMapper.readTree(aiFeedbackJson);
            } catch (Exception e) {
                log.warn("[PracticeService] Failed to parse aiFeedbackJson in buildQuestionFeedbackRows exception={}",
                        exceptionCategory(e));
            }
        }

        List<PracticeQuestion> orderedQuestions = questions.stream()
                .sorted(QUESTION_ORDER)
                .toList();
        List<PracticeQuestionFeedbackRow> rows = new ArrayList<>();
        List<PracticeQuestion> essayQuestions = orderedQuestions.stream()
                .filter(q -> PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType()))
                .toList();

        boolean isLegacy = rootNode != null && writingFeedbackReader.isLegacyFlatFeedback(rootNode);
        boolean currentMapReEvaluatable = !isLegacy
                && writingFeedbackReader.parseRoot(rootNode, essayQuestionIds(essayQuestions)).status()
                == WritingFeedbackCompatibilityReader.Status.VALID_CURRENT;

        for (PracticeQuestion q : orderedQuestions) {
            String qIdStr = String.valueOf(q.getId());
            String answer = answers.getOrDefault(qIdStr, "");
            com.fasterxml.jackson.databind.JsonNode selectedFeedbackEntry = null;

            if (rootNode != null) {
                if (PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType())) {
                    if (isLegacy) {
                        String legacyStudentText = rootNode.path("student_text").asText("");
                        if (essayQuestions.size() == 1) {
                            selectedFeedbackEntry = rootNode;
                        } else {
                            List<PracticeQuestion> matchingEssays = new ArrayList<>();
                            for (PracticeQuestion eq : essayQuestions) {
                                String eqAns = answers.getOrDefault(String.valueOf(eq.getId()), "");
                                if (!eqAns.isBlank() && eqAns.equals(legacyStudentText)) {
                                    matchingEssays.add(eq);
                                }
                            }
                            if (matchingEssays.size() == 1) {
                                if (matchingEssays.get(0).getId().equals(q.getId())) {
                                    selectedFeedbackEntry = rootNode;
                                }
                            } else if (matchingEssays.size() > 1) {
                                matchingEssays.sort(QUESTION_ORDER);
                                PracticeQuestion lastMatching = matchingEssays.get(matchingEssays.size() - 1);
                                if (lastMatching.getId().equals(q.getId())) {
                                    selectedFeedbackEntry = rootNode;
                                }
                            } else {
                                if (!essayQuestions.isEmpty()) {
                                    PracticeQuestion lastEssay = essayQuestions.get(essayQuestions.size() - 1);
                                    if (lastEssay.getId().equals(q.getId())) {
                                        selectedFeedbackEntry = rootNode;
                                    }
                                }
                            }
                        }
                    } else {
                        com.fasterxml.jackson.databind.JsonNode node = rootNode.path(qIdStr);
                        if (!node.isMissingNode() && !node.isNull()) {
                            if (node.isTextual()) {
                                try {
                                    JsonNode parsedNode = objectMapper.readTree(node.asText());
                                    selectedFeedbackEntry = parsedNode != null && parsedNode.isObject() ? parsedNode : null;
                                } catch (Exception e) {
                                    selectedFeedbackEntry = null;
                                }
                            } else {
                                selectedFeedbackEntry = node.isObject() ? node : null;
                            }
                        }
                    }
                }
            }

            rows.add(new PracticeQuestionFeedbackRow(
                    q.getId(),
                    q.getQuestionNo(),
                    q.getQuestionType(),
                    q.getPrompt(),
                    answer,
                    writingFeedbackViewMapper.map(selectedFeedbackEntry),
                    isQuestionReEvaluatable(q, essayQuestions, isLegacy, currentMapReEvaluatable)
            ));
        }
        return rows;
    }

    private List<PracticeQuestionFeedbackRow> buildQuestionFeedbackRowsFromRows(
            List<PracticeQuestionRow> questions,
            String answersJson,
            String aiFeedbackJson
    ) {
        Map<String, String> answers = readAnswers(answersJson);
        JsonNode rootNode = null;
        if (aiFeedbackJson != null && !aiFeedbackJson.isBlank()) {
            try {
                rootNode = objectMapper.readTree(aiFeedbackJson);
            } catch (Exception e) {
                log.warn("[PracticeService] Failed to parse versioned writing aiFeedbackJson exception={}",
                        exceptionCategory(e));
            }
        }

        List<PracticeQuestionRow> orderedQuestions = questions.stream()
                .sorted(Comparator.comparing(PracticeQuestionRow::questionNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(PracticeQuestionRow::id, Comparator.nullsLast(Long::compareTo)))
                .toList();
        List<PracticeQuestionRow> essayQuestions = orderedQuestions.stream()
                .filter(q -> PracticeQuestion.TYPE_ESSAY.equals(q.questionType()))
                .toList();
        List<Long> essayIds = essayQuestions.stream().map(PracticeQuestionRow::id).toList();

        boolean isLegacy = rootNode != null && writingFeedbackReader.isLegacyFlatFeedback(rootNode);
        boolean currentMapReEvaluatable = !isLegacy
                && writingFeedbackReader.parseRoot(rootNode, essayIds).status()
                == WritingFeedbackCompatibilityReader.Status.VALID_CURRENT;

        List<PracticeQuestionFeedbackRow> rows = new ArrayList<>();
        for (PracticeQuestionRow q : orderedQuestions) {
            String qIdStr = String.valueOf(q.id());
            String answer = answers.getOrDefault(qIdStr, "");
            JsonNode selectedFeedbackEntry = null;

            if (rootNode != null && PracticeQuestion.TYPE_ESSAY.equals(q.questionType())) {
                if (isLegacy) {
                    selectedFeedbackEntry = essayQuestions.size() == 1 ? rootNode : null;
                } else {
                    JsonNode node = rootNode.path(qIdStr);
                    if (!node.isMissingNode() && !node.isNull()) {
                        if (node.isTextual()) {
                            try {
                                JsonNode parsedNode = objectMapper.readTree(node.asText());
                                selectedFeedbackEntry = parsedNode != null && parsedNode.isObject() ? parsedNode : null;
                            } catch (Exception ignored) {
                                selectedFeedbackEntry = null;
                            }
                        } else {
                            selectedFeedbackEntry = node.isObject() ? node : null;
                        }
                    }
                }
            }

            rows.add(new PracticeQuestionFeedbackRow(
                    q.id(),
                    q.questionNo(),
                    q.questionType(),
                    q.prompt(),
                    answer,
                    writingFeedbackViewMapper.map(selectedFeedbackEntry),
                    PracticeQuestion.TYPE_ESSAY.equals(q.questionType()) && !isLegacy && currentMapReEvaluatable
            ));
        }
        return rows;
    }

    private List<PracticeQuestion> liveSectionQuestions(PracticeAttempt attempt, PracticeSection section) {
        List<PracticeQuestionGroupRow> groupRows = groupRowsForAttempt(attempt, section);
        List<Long> sectionQuestionIds = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .map(PracticeQuestionRow::id)
                .toList();
        return questionRepository.findBySetIdOrderByDisplayOrderAsc(attempt.getSetId()).stream()
                .filter(q -> sectionQuestionIds.contains(q.getId()))
                .toList();
    }

    private boolean isQuestionReEvaluatable(
            PracticeQuestion question,
            List<PracticeQuestion> essayQuestions,
            boolean isLegacy,
            boolean currentMapReEvaluatable
    ) {
        if (question.getId() == null || !PracticeQuestion.TYPE_ESSAY.equals(question.getQuestionType())) {
            return false;
        }
        if (isLegacy) {
            return essayQuestions.size() == 1 && essayQuestions.get(0).getId().equals(question.getId());
        }
        return currentMapReEvaluatable;
    }

    private List<Long> essayQuestionIds(List<PracticeQuestion> essayQuestions) {
        return essayQuestions.stream()
                .map(PracticeQuestion::getId)
                .toList();
    }

    private double getNormalizedAttemptScore(PracticeAttempt attempt) {
        if (attempt.getScore() == null) return 0.0;
        if (attempt.getScorePercentage() != null) {
            return attempt.getScorePercentage().doubleValue();
        }
        if ("WRITING".equals(attempt.getSkill()) || "SPEAKING".equals(attempt.getSkill())) {
            return attempt.getScore().doubleValue();
        }
        if (attempt.getTotalPoints() != null && attempt.getTotalPoints().compareTo(BigDecimal.ZERO) > 0) {
            return attempt.getScore().multiply(BigDecimal.valueOf(100))
                    .divide(attempt.getTotalPoints(), 2, RoundingMode.HALF_UP).doubleValue();
        }
        return 0.0;
    }

    private boolean isCompletedProgressAttempt(PracticeAttempt attempt) {
        return PracticeAttempt.STATUS_SUBMITTED.equals(attempt.getStatus())
                || PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus());
    }

    private void rejectDiscardedAttempt(PracticeAttempt attempt) {
        if (PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus())) {
            throw new EntityNotFoundException("Lượt làm bài không tồn tại");
        }
    }

    private boolean hasValidProgressScore(PracticeAttempt attempt) {
        return isCompletedProgressAttempt(attempt) && attempt.getScore() != null;
    }

    private List<PracticeAttempt> attemptsBySkill(List<PracticeAttempt> attempts, String skill) {
        return attempts.stream()
                .filter(a -> skill.equals(a.getSkill()))
                .toList();
    }

    private java.time.LocalDateTime progressActivityAt(PracticeAttempt attempt) {
        if (attempt.getSubmittedAt() != null) return attempt.getSubmittedAt();
        if (attempt.getUpdatedAt() != null) return attempt.getUpdatedAt();
        return attempt.getCreatedAt();
    }

    private PracticeResultSummary toAttemptSummary(PracticeAttempt attempt,
                                                   Map<Long, PracticeSet> setsById,
                                                   Map<Long, PracticeTest> testsById,
                                                   Map<Long, PracticeSection> sectionsById) {
        PracticeSet set = setsById.get(attempt.getSetId());
        PracticeTest test = testsById.get(attempt.getTestId());
        PracticeSection section = sectionsById.get(attempt.getSectionId());

        String title = set != null ? set.getTitle() : "Lượt luyện tập";
        if (test != null && test.getTitle() != null && !test.getTitle().isBlank()) {
            title += " - " + test.getTitle();
        }
        if (section != null && section.getTitle() != null && !section.getTitle().isBlank()) {
            title += " - " + section.getTitle();
        }

        return new PracticeResultSummary(
                attempt.getId(),
                title,
                attempt.getSkill(),
                attempt.getScore(),
                attempt.getTotalPoints(),
                attempt.getSubmittedAt(),
                progressActivityAt(attempt),
                attempt.getStatus(),
                attempt.getSetId(),
                attempt.getTestId(),
                attempt.getSectionId());
    }

    private Map<Long, PracticeSet> loadSetsById(List<PracticeAttempt> attempts) {
        List<Long> ids = attempts.stream()
                .map(PracticeAttempt::getSetId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, PracticeSet> result = new LinkedHashMap<>();
        for (PracticeSet set : setRepository.findAllById(ids)) {
            result.put(set.getId(), set);
        }
        return result;
    }

    private Map<Long, PracticeTest> loadTestsById(List<PracticeAttempt> attempts) {
        List<Long> ids = attempts.stream()
                .map(PracticeAttempt::getTestId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, PracticeTest> result = new LinkedHashMap<>();
        for (PracticeTest test : testRepository.findAllById(ids)) {
            result.put(test.getId(), test);
        }
        return result;
    }

    private Map<Long, PracticeSection> loadSectionsById(List<PracticeAttempt> attempts) {
        List<Long> ids = attempts.stream()
                .map(PracticeAttempt::getSectionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, PracticeSection> result = new LinkedHashMap<>();
        for (PracticeSection section : sectionRepository.findAllById(ids)) {
            result.put(section.getId(), section);
        }
        return result;
    }

    private com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview buildAttemptProgressOverview(
            String displayName, String avatarUrl, ProgressAttemptData data) {
        List<PracticeAttempt> allAttempts = data.allAttempts();
        List<PracticeAttempt> recentAttempts = data.recentAttempts();
        String[] skills = {"READING", "LISTENING", "WRITING", "SPEAKING"};

        List<com.ksh.features.practice.dto.PracticeDtos.SkillMetric> skillMetrics = new ArrayList<>();
        for (String skill : skills) {
            List<PracticeAttempt> skillAttempts = attemptsBySkill(allAttempts, skill);
            List<PracticeAttempt> scoredSkillAttempts = skillAttempts.stream()
                    .filter(this::hasValidProgressScore)
                    .toList();
            double avgScore = 0.0;
            if (!scoredSkillAttempts.isEmpty()) {
                double sum = 0.0;
                for (PracticeAttempt attempt : scoredSkillAttempts) {
                    sum += getNormalizedAttemptScore(attempt);
                }
                avgScore = Math.round((sum / scoredSkillAttempts.size()) * 100.0) / 100.0;
            }
            skillMetrics.add(new com.ksh.features.practice.dto.PracticeDtos.SkillMetric(
                    skill, PracticeDtos.getSkillLabel(skill), avgScore, skillAttempts.size(), 0.0));
        }

        if (allAttempts.isEmpty()) {
            return new com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview(
                    displayName, avatarUrl, "Chưa có dữ liệu", 0, 0, 0, 0.0,
                    skillMetrics, List.of(), List.of());
        }

        int totalAttempts = allAttempts.size();
        int totalCompleted = (int) allAttempts.stream()
                .filter(this::isCompletedProgressAttempt)
                .count();

        int totalPracticeMinutes = 0;
        for (PracticeAttempt attempt : allAttempts) {
            java.time.LocalDateTime activityAt = progressActivityAt(attempt);
            if (attempt.getStartedAt() != null && activityAt != null) {
                long diff = java.time.temporal.ChronoUnit.MINUTES.between(attempt.getStartedAt(), activityAt);
                totalPracticeMinutes += (diff > 0 && diff < 240) ? (int) diff : 30;
            } else {
                totalPracticeMinutes += 30;
            }
        }

        List<PracticeAttempt> scoredAttempts = allAttempts.stream()
                .filter(this::hasValidProgressScore)
                .toList();
        double averageSum = 0.0;
        for (PracticeAttempt attempt : scoredAttempts) {
            averageSum += getNormalizedAttemptScore(attempt);
        }
        double recentAverageScore = scoredAttempts.isEmpty()
                ? 0.0
                : Math.round((averageSum / scoredAttempts.size()) * 100.0) / 100.0;

        Map<java.time.LocalDate, Integer> counts = new LinkedHashMap<>();
        Map<java.time.LocalDate, Integer> mins = new LinkedHashMap<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 83; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            counts.put(d, 0);
            mins.put(d, 0);
        }

        for (PracticeAttempt attempt : allAttempts) {
            java.time.LocalDateTime activityAt = progressActivityAt(attempt);
            if (activityAt == null) continue;
            java.time.LocalDate date = activityAt.toLocalDate();
            if (!counts.containsKey(date)) continue;
            counts.put(date, counts.get(date) + 1);
            long diff = attempt.getStartedAt() != null
                    ? java.time.temporal.ChronoUnit.MINUTES.between(attempt.getStartedAt(), activityAt)
                    : 30;
            int minutes = (diff > 0 && diff < 240) ? (int) diff : 30;
            mins.put(date, mins.get(date) + minutes);
        }

        List<com.ksh.features.practice.dto.PracticeDtos.HeatmapCell> heatmap = new ArrayList<>();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (java.time.LocalDate d : counts.keySet()) {
            heatmap.add(new com.ksh.features.practice.dto.PracticeDtos.HeatmapCell(
                    d.format(fmt), counts.get(d), mins.get(d)));
        }

        Map<Long, PracticeSet> setsById = data.setsById();
        Map<Long, PracticeTest> testsById = data.testsById();
        Map<Long, PracticeSection> sectionsById = data.sectionsById();
        List<PracticeAttempt> recent8 = recentAttempts.subList(0, Math.min(8, recentAttempts.size()));
        List<PracticeResultSummary> recentHistory = new ArrayList<>();
        for (PracticeAttempt attempt : recent8) {
            recentHistory.add(toAttemptSummary(attempt, setsById, testsById, sectionsById));
        }

        String currentLevel = "Cần luyện thêm";
        if (recentAverageScore >= 80.0) {
            currentLevel = "Vững";
        } else if (recentAverageScore >= 65.0) {
            currentLevel = "Khá";
        } else if (recentAverageScore >= 50.0) {
            currentLevel = "Đang tiến bộ";
        }

        return new com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview(
                displayName, avatarUrl, currentLevel, totalAttempts, totalCompleted,
                totalPracticeMinutes, recentAverageScore, skillMetrics, heatmap, recentHistory);
    }

    private com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics buildAttemptPracticeAnalytics(
            ProgressAttemptData data) {
        List<PracticeAttempt> allAttempts = data.allAttempts();
        List<PracticeAttempt> recentAttempts = data.recentAttempts();

        if (allAttempts.isEmpty()) {
            return new com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics(
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime startOfThisWeek = now.minusDays(7);
        java.time.LocalDateTime startOfLastWeek = now.minusDays(14);
        String[] skills = {"READING", "LISTENING", "WRITING", "SPEAKING"};
        List<com.ksh.features.practice.dto.PracticeDtos.SkillMetric> weeklySkillMetrics = new ArrayList<>();

        for (String skill : skills) {
            double thisWeekSum = 0.0;
            int thisWeekCount = 0;
            double lastWeekSum = 0.0;
            int lastWeekCount = 0;
            for (PracticeAttempt attempt : allAttempts) {
                if (!skill.equals(attempt.getSkill()) || !hasValidProgressScore(attempt)) continue;
                java.time.LocalDateTime activityAt = progressActivityAt(attempt);
                if (activityAt == null) continue;
                double norm = getNormalizedAttemptScore(attempt);
                if (activityAt.isAfter(startOfThisWeek)) {
                    thisWeekSum += norm;
                    thisWeekCount++;
                } else if (activityAt.isAfter(startOfLastWeek)) {
                    lastWeekSum += norm;
                    lastWeekCount++;
                }
            }
            double thisWeekAvg = thisWeekCount == 0 ? 0.0 : thisWeekSum / thisWeekCount;
            double lastWeekAvg = lastWeekCount == 0 ? 0.0 : lastWeekSum / lastWeekCount;
            double delta = thisWeekCount == 0 || lastWeekCount == 0
                    ? 0.0
                    : Math.round((thisWeekAvg - lastWeekAvg) * 100.0) / 100.0;
            weeklySkillMetrics.add(new com.ksh.features.practice.dto.PracticeDtos.SkillMetric(
                    skill, PracticeDtos.getSkillLabel(skill),
                    Math.round(thisWeekAvg * 100.0) / 100.0, thisWeekCount, delta));
        }

        List<PracticeAttempt> scoredRecent = recentAttempts.stream()
                .filter(this::hasValidProgressScore)
                .toList();
        List<PracticeAttempt> last30Scored = scoredRecent.subList(0, Math.min(30, scoredRecent.size()));
        Map<Long, PracticeSet> setsById = data.setsById();
        Map<Long, PracticeTest> testsById = data.testsById();
        Map<Long, PracticeSection> sectionsById = data.sectionsById();

        List<com.ksh.features.practice.dto.PracticeDtos.ScoreTrendPoint> scoreTrend = new ArrayList<>();
        java.time.format.DateTimeFormatter trendFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = last30Scored.size() - 1; i >= 0; i--) {
            PracticeAttempt attempt = last30Scored.get(i);
            PracticeResultSummary summary = toAttemptSummary(attempt, setsById, testsById, sectionsById);
            java.time.LocalDateTime activityAt = progressActivityAt(attempt);
            scoreTrend.add(new com.ksh.features.practice.dto.PracticeDtos.ScoreTrendPoint(
                    activityAt != null ? activityAt.format(trendFmt) : "",
                    attempt.getSkill(),
                    getNormalizedAttemptScore(attempt),
                    summary.title()));
        }

        List<Long> setIds = last30Scored.stream().map(PracticeAttempt::getSetId).distinct().toList();
        List<PracticeQuestion> questions = setIds.isEmpty() ? List.of() : questionRepository.findBySetIdIn(setIds);
        Map<Long, List<PracticeQuestion>> questionsBySetId = questions.stream()
                .collect(java.util.stream.Collectors.groupingBy(PracticeQuestion::getSetId));
        Map<String, List<Double>> scoresByType = new LinkedHashMap<>();
        Map<String, java.time.LocalDateTime> lastPracticedByType = new LinkedHashMap<>();
        Map<String, String> skillByType = new LinkedHashMap<>();

        for (PracticeAttempt attempt : last30Scored) {
            Map<String, String> answers = readAnswers(attempt.getAnswersJson());
            List<PracticeQuestion> setQuestions = questionsBySetId.getOrDefault(attempt.getSetId(), List.of());
            for (PracticeQuestion q : setQuestions) {
                String type = q.getQuestionType();
                if ("WRITING".equals(attempt.getSkill())) {
                    if (q.getQuestionNo() != null) {
                        int qNo = q.getQuestionNo();
                        if (qNo == 51) type = "Q51";
                        else if (qNo == 52) type = "Q52";
                        else if (qNo == 53) type = "Q53";
                        else if (qNo == 54) type = "Q54";
                        else type = "GENERAL";
                    } else {
                        type = "GENERAL";
                    }
                }
                String ans = answers.getOrDefault(String.valueOf(q.getId()), "").trim();
                double qScore;
                if ("WRITING".equals(attempt.getSkill()) || "SPEAKING".equals(attempt.getSkill())) {
                    qScore = attempt.getScore().doubleValue();
                } else {
                    AssessmentScoreResult scoreResult = scoreObjective(toQuestionSnapshot(q), ans)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Objective progress question is not scoreable: " + q.getId()));
                    qScore = scoreResult.possiblePoints().signum() == 0
                            ? 0.0
                            : scoreResult.earnedPoints().multiply(BigDecimal.valueOf(100))
                                    .divide(scoreResult.possiblePoints(), 4, RoundingMode.HALF_UP)
                                    .doubleValue();
                }
                scoresByType.computeIfAbsent(type, k -> new ArrayList<>()).add(qScore);
                skillByType.putIfAbsent(type, attempt.getSkill());
                java.time.LocalDateTime activityAt = progressActivityAt(attempt);
                if (activityAt != null) {
                    java.time.LocalDateTime currentLast = lastPracticedByType.get(type);
                    if (currentLast == null || activityAt.isAfter(currentLast)) {
                        lastPracticedByType.put(type, activityAt);
                    }
                }
            }
        }

        List<com.ksh.features.practice.dto.PracticeDtos.QuestionTypePerf> questionTypePerf = new ArrayList<>();
        java.time.format.DateTimeFormatter dtFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Map.Entry<String, List<Double>> entry : scoresByType.entrySet()) {
            String type = entry.getKey();
            List<Double> qScores = entry.getValue();
            double avg = qScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double max = qScores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            java.time.LocalDateTime lastDt = lastPracticedByType.get(type);
            questionTypePerf.add(new com.ksh.features.practice.dto.PracticeDtos.QuestionTypePerf(
                    skillByType.getOrDefault(type, "READING"), type, getQuestionTypeLabel(type), qScores.size(),
                    Math.round(avg * 10.0) / 10.0, Math.round(max * 10.0) / 10.0,
                    lastDt != null ? lastDt.format(dtFmt) : ""));
        }

        List<com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight> highlights = new ArrayList<>();
        String mostPracticedSkill = "";
        int maxAttempts = 0;
        String needsWorkSkill = "";
        double minAvgScore = 101.0;
        List<PracticeAttempt> scoredAll = allAttempts.stream().filter(this::hasValidProgressScore).toList();
        for (String skill : skills) {
            List<PracticeAttempt> skillAttempts = attemptsBySkill(scoredAll, skill);
            if (skillAttempts.isEmpty()) continue;
            if (skillAttempts.size() > maxAttempts) {
                maxAttempts = skillAttempts.size();
                mostPracticedSkill = skill;
            }
            double sum = 0.0;
            for (PracticeAttempt attempt : skillAttempts) {
                sum += getNormalizedAttemptScore(attempt);
            }
            double avg = sum / skillAttempts.size();
            if (avg < minAvgScore) {
                minAvgScore = avg;
                needsWorkSkill = skill;
            }
        }
        if (maxAttempts >= 3 && !mostPracticedSkill.isEmpty()) {
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight(
                    "MOST_PRACTICED", "Luyện nhiều nhất",
                    PracticeDtos.getSkillLabel(mostPracticedSkill), maxAttempts, 0.0, true));
        } else {
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("MOST_PRACTICED", "Luyện nhiều nhất", "", 0, 0.0, false));
        }
        if (minAvgScore <= 100.0 && !needsWorkSkill.isEmpty()) {
            List<PracticeAttempt> skillAttempts = attemptsBySkill(scoredAll, needsWorkSkill);
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight(
                    "NEEDS_WORK", "Cần cải thiện",
                    PracticeDtos.getSkillLabel(needsWorkSkill), skillAttempts.size(),
                    Math.round(minAvgScore * 10.0) / 10.0, true));
        } else {
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("NEEDS_WORK", "Cần cải thiện", "", 0, 0.0, false));
        }
        highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("MOST_STABLE", "Ổn định nhất", "", 0, 0.0, false));
        highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("MOST_IMPROVED", "Tiến bộ nhất", "", 0, 0.0, false));

        List<PracticeResultSummary> history = new ArrayList<>();
        List<PracticeAttempt> historyAttempts = recentAttempts.subList(0, Math.min(30, recentAttempts.size()));
        for (PracticeAttempt attempt : historyAttempts) {
            history.add(toAttemptSummary(attempt, setsById, testsById, sectionsById));
        }

        return new com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics(
                weeklySkillMetrics, scoreTrend, questionTypePerf, highlights, history);
    }

    private int getSkillIndex(String skill) {
        if ("READING".equals(skill)) return 0;
        if ("LISTENING".equals(skill)) return 1;
        if ("WRITING".equals(skill)) return 2;
        if ("SPEAKING".equals(skill)) return 3;
        return 0;
    }

    private ProgressAttemptData loadProgressAttemptData(Long userId) {
        List<PracticeAttempt> recentAttempts =
                attemptRepository.findTop100ByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        userId, PracticeAttempt.STATUS_DISCARDED);
        return new ProgressAttemptData(
                recentAttempts,
                recentAttempts,
                loadSetsById(recentAttempts),
                loadTestsById(recentAttempts),
                loadSectionsById(recentAttempts));
    }

    @Transactional(readOnly = true)
    public com.ksh.features.practice.dto.PracticeDtos.PracticeProgressPageData getProgressPageData(
            Long userId, String displayName, String avatarUrl) {
        ProgressAttemptData data = loadProgressAttemptData(userId);
        return new com.ksh.features.practice.dto.PracticeDtos.PracticeProgressPageData(
                buildAttemptProgressOverview(displayName, avatarUrl, data),
                buildAttemptPracticeAnalytics(data));
    }

    private record ProgressAttemptData(
            List<PracticeAttempt> allAttempts,
            List<PracticeAttempt> recentAttempts,
            Map<Long, PracticeSet> setsById,
            Map<Long, PracticeTest> testsById,
            Map<Long, PracticeSection> sectionsById) {
    }

    private PracticeSet loadPublished(Long setId) {
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        if (!PracticeSet.STATUS_PUBLISHED.equals(set.getStatus())) {
            throw new EntityNotFoundException("Bộ luyện tập không tồn tại");
        }
        return set;
    }

    private static PracticeSetRow toSetRow(PracticeSet set) {
        return new PracticeSetRow(
                set.getId(),
                set.getTitle(),
                set.getDescription(),
                set.getSkill(),
                PracticeDtos.getSkillLabel(set.getSkill()),
                set.getMetadataJson(),
                set.getCreationMethod()
        );
    }

    private static PracticeSetRow toSetRow(PracticeSetVersion set) {
        return new PracticeSetRow(
                set.getSetId(),
                set.getTitle(),
                set.getDescription(),
                set.getSkill(),
                PracticeDtos.getSkillLabel(set.getSkill()),
                set.getMetadataJson(),
                set.getCreationMethod()
        );
    }

    private static PracticeTestRow toTestRow(PracticeTest test) {
        return new PracticeTestRow(
                test.getId(),
                test.getSetId(),
                test.getTitle(),
                test.getDescription(),
                test.getDisplayOrder(),
                test.getEstimatedMinutes()
        );
    }

    private PracticeQuestionRow toQuestionRow(PracticeQuestion question) {
        QuestionContent content = questionContentForDisplay(
                question.getQuestionContentJson(), question.getOptionsJson(), question.getQuestionType());
        String imageReference = firstNonBlank(
                safeInternalMaterialReference(content == null ? null : content.imageReference()),
                firstMarkdownImageReference(question.getPrompt()));
        String audioReference = safeInternalMaterialReference(
                content == null ? null : content.audioReference());
        return new PracticeQuestionRow(
                question.getId(),
                question.getQuestionNo(),
                question.getQuestionType(),
                stripMarkdownImages(question.getPrompt()),
                readOptions(question.getOptionsJson()),
                question.getAnswerKey(),
                question.getExplanation(),
                groupLabel(question.getQuestionNo()),
                blankToNull(imageReference),
                blankToNull(audioReference),
                optionRows(content, readOptions(question.getOptionsJson())),
                blankRows(content)
        );
    }

    private PracticeQuestionGroupRow toGroupRow(PracticeQuestionGroup g, List<PracticeQuestionRow> questions) {
        ExampleBox exampleBox = null;
        if (g.getExampleJson() != null && !g.getExampleJson().isBlank()) {
            try {
                exampleBox = objectMapper.readValue(g.getExampleJson(), ExampleBox.class);
            } catch (Exception e) {
                // ignore
            }
        }
        return new PracticeQuestionGroupRow(
                g.getId(),
                g.getSectionId(),
                g.getGroupLabel(),
                g.getQuestionFrom(),
                g.getQuestionTo(),
                stripMarkdownImages(g.getInstruction()),
                g.getStimulusType(),
                g.getPassageText(),
                g.getTranscriptText(),
                groupImageReference(g.getImageUrl(), g.getInstruction()),
                g.getStimulusProvenanceJson(),
                blankToNull(safeInternalMaterialReference(g.getAudioUrl())),
                exampleBox,
                questions
        );
    }

    private Optional<PracticeVersionSnapshot> versionSnapshot(PracticeAttempt attempt) {
        if (publishedVersionService == null) {
            return Optional.empty();
        }
        return publishedVersionService.snapshot(
                attempt.getPublishedVersionId(),
                attempt.getSetVersionId(),
                attempt.getTestVersionId(),
                attempt.getSectionVersionId());
    }

    private List<PracticeQuestionGroupRow> groupRowsForAttempt(PracticeAttempt attempt, PracticeSection liveSection) {
        return groupRowsForAttempt(attempt, liveSection, versionSnapshot(attempt));
    }

    private List<PracticeQuestionGroupRow> groupRowsForAttempt(PracticeAttempt attempt,
                                                               PracticeSection liveSection,
                                                               Optional<PracticeVersionSnapshot> snapshot) {
        if (snapshot.isEmpty()) {
            if ("SPEAKING".equals(attempt.getSkill())) {
                throw new IllegalStateException("Speaking attempt is missing an immutable delivery version.");
            }
            if (liveSection == null) {
                throw new IllegalStateException("Live section is required for a legacy attempt.");
            }
            return getQuestionGroupsForSection(attempt.getSetId(), liveSection.getId());
        }
        PracticeVersionSnapshot version = snapshot.get();
        List<PracticeQuestionVersion> questions = version.questions();
        List<PracticeQuestionGroupRow> groups = new ArrayList<>();
        for (PracticeQuestionGroupVersion group : version.groups()) {
            ExampleBox exampleBox = null;
            if (group.getExampleJson() != null && !group.getExampleJson().isBlank()) {
                try {
                    exampleBox = objectMapper.readValue(group.getExampleJson(), ExampleBox.class);
                } catch (Exception ignored) {
                    // Keep versioned rendering resilient for legacy imported example JSON.
                }
            }
            List<PracticeQuestionRow> questionRows = questions.stream()
                    .filter(q -> Objects.equals(group.getId(), q.getGroupVersionId()))
                    .map(this::toQuestionRow)
                    .toList();
            groups.add(new PracticeQuestionGroupRow(
                    group.getGroupId(),
                    version.sectionVersion().getSectionId(),
                    group.getGroupLabel(),
                    group.getQuestionFrom(),
                    group.getQuestionTo(),
                    stripMarkdownImages(group.getInstruction()),
                    group.getStimulusType(),
                    group.getPassageText(),
                    group.getTranscriptText(),
                    groupImageReference(group.getImageUrl(), group.getInstruction()),
                    group.getStimulusProvenanceJson(),
                    blankToNull(safeInternalMaterialReference(group.getAudioUrl())),
                    exampleBox,
                    questionRows
            ));
        }
        List<PracticeQuestionRow> orphanQuestionRows = questions.stream()
                .filter(q -> q.getGroupVersionId() == null)
                .map(this::toQuestionRow)
                .toList();
        if (!orphanQuestionRows.isEmpty()) {
            int from = orphanQuestionRows.stream().mapToInt(PracticeQuestionRow::questionNo).min().orElse(1);
            int to = orphanQuestionRows.stream().mapToInt(PracticeQuestionRow::questionNo).max().orElse(from);
            groups.add(new PracticeQuestionGroupRow(
                    null,
                    version.sectionVersion().getSectionId(),
                    "Phần thi",
                    from,
                    to,
                    null,
                    null,
                    null,
                    orphanQuestionRows
            ));
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public AttemptPlayerView getAttemptPlayerView(Long attemptId, Long userId) {
        PracticeAttempt attempt = getPracticeAttempt(attemptId, userId);
        rejectDiscardedAttempt(attempt);
        if ("SPEAKING".equals(attempt.getSkill())) {
            throw new IllegalArgumentException("Speaking attempts must use the dedicated speaking player.");
        }

        Optional<PracticeVersionSnapshot> snapshot = versionSnapshot(attempt);
        if (snapshot.isPresent()) {
            PracticeVersionSnapshot version = snapshot.get();
            validateAttemptSnapshot(attempt, version);
            PracticeSetView view = new PracticeSetView(
                    toSetRow(version.setVersion()),
                    redactPlayerGroups(groupRowsForAttempt(attempt, null, snapshot)));
            return new AttemptPlayerView(view, attemptSectionDelivery(version));
        }

        PracticeSet set = loadPublished(attempt.getSetId());
        PracticeSection section = liveSectionForAttempt(attempt);
        PracticeSetView view = new PracticeSetView(
                toSetRow(set),
                redactPlayerGroups(groupRowsForAttempt(attempt, section, snapshot)));
        return new AttemptPlayerView(view, attemptSectionDelivery(section));
    }

    @Transactional(readOnly = true)
    public AttemptSectionDelivery getAttemptSectionDelivery(Long attemptId, Long userId) {
        PracticeAttempt attempt = getPracticeAttempt(attemptId, userId);
        Optional<PracticeVersionSnapshot> snapshot = versionSnapshot(attempt);
        if (snapshot.isPresent()) {
            PracticeVersionSnapshot version = snapshot.get();
            validateAttemptSnapshot(attempt, version);
            return attemptSectionDelivery(version);
        }
        if ("SPEAKING".equals(attempt.getSkill())) {
            throw new IllegalStateException("Speaking attempt is missing an immutable delivery version.");
        }
        return attemptSectionDelivery(liveSectionForAttempt(attempt));
    }

    private PracticeSection liveSectionForAttempt(PracticeAttempt attempt) {
        PracticeSection section = getSection(attempt.getSectionId());
        if (!Objects.equals(attempt.getSetId(), section.getSetId())
                || !Objects.equals(attempt.getTestId(), section.getTestId())
                || !Objects.equals(attempt.getSkill(), section.getSkill())) {
            throw new IllegalStateException("Section metadata mismatch with attempt");
        }
        return section;
    }

    private static void validateAttemptSnapshot(PracticeAttempt attempt, PracticeVersionSnapshot version) {
        if (!Objects.equals(attempt.getSetId(), version.setVersion().getSetId())
                || !Objects.equals(attempt.getTestId(), version.testVersion().getTestId())
                || !Objects.equals(attempt.getSectionId(), version.sectionVersion().getSectionId())
                || !Objects.equals(attempt.getSkill(), version.sectionVersion().getSkill())) {
            throw new IllegalStateException("Attempt delivery version is inconsistent.");
        }
    }

    private static AttemptSectionDelivery attemptSectionDelivery(PracticeVersionSnapshot version) {
        return new AttemptSectionDelivery(
                version.sectionVersion().getSectionId(),
                version.sectionVersion().getTitle(),
                version.sectionVersion().getSkill(),
                version.sectionVersion().getDurationMinutes());
    }

    private static AttemptSectionDelivery attemptSectionDelivery(PracticeSection section) {
        return new AttemptSectionDelivery(
                section.getId(), section.getTitle(), section.getSkill(), section.getDurationMinutes());
    }

    public record AttemptPlayerView(
            PracticeSetView view,
            AttemptSectionDelivery delivery
    ) {
    }

    public record AttemptSectionDelivery(
            Long sectionId,
            String title,
            String skill,
            Integer durationMinutes
    ) {
    }

    @Transactional(readOnly = true)
    public ListeningPreflightDelivery getListeningPreflightDelivery(
            Long setId, Long testId, Long sectionId) {
        PracticeSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new EntityNotFoundException("Phần Listening không tồn tại."));
        if (!Objects.equals(setId, section.getSetId())
                || !Objects.equals(testId, section.getTestId())
                || !"LISTENING".equals(section.getSkill())) {
            throw new IllegalArgumentException("Phần thi không thuộc Listening đã chọn.");
        }
        String reference = listeningCheckAudioReference(
                section.getDeliveryJson(),
                groupRepository.findBySectionIdOrderByDisplayOrderAsc(sectionId).stream()
                        .map(PracticeQuestionGroup::getAudioUrl)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElse(null));
        return new ListeningPreflightDelivery(setId, testId, sectionId, section.getTitle(), reference);
    }

    @Transactional(readOnly = true)
    public ListeningPreflightDelivery getAttemptListeningPreflightDelivery(Long attemptId, Long userId) {
        PracticeAttempt attempt = getPracticeAttempt(attemptId, userId);
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new IllegalStateException("Lượt Listening đã kết thúc.");
        }
        if (!"LISTENING".equals(attempt.getSkill())) {
            throw new IllegalArgumentException("Lượt làm bài không thuộc kỹ năng Listening.");
        }
        PracticeVersionSnapshot snapshot = versionSnapshot(attempt)
                .orElseThrow(() -> new IllegalStateException(
                        "Listening attempt is missing an immutable delivery version."));
        validateAttemptSnapshot(attempt, snapshot);
        String reference = listeningCheckAudioReference(
                snapshot.sectionVersion().getDeliveryJson(),
                snapshot.groups().stream()
                        .map(PracticeQuestionGroupVersion::getAudioUrl)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElse(null));
        return new ListeningPreflightDelivery(
                attempt.getSetId(),
                attempt.getTestId(),
                attempt.getSectionId(),
                snapshot.sectionVersion().getTitle(),
                reference);
    }

    private String listeningCheckAudioReference(String deliveryJson, String legacyFallback) {
        String canonicalReference = null;
        if (deliveryJson != null && !deliveryJson.isBlank()) {
            try {
                PracticeSectionDelivery delivery = objectMapper.readValue(
                        deliveryJson, PracticeSectionDelivery.class);
                if (PracticeSectionDelivery.SCHEMA_VERSION.equals(delivery.schemaVersion())
                        && delivery.listeningDelivery() != null) {
                    canonicalReference = delivery.listeningDelivery().checkAudioReference();
                }
            } catch (Exception exception) {
                log.warn("[PracticeService] Invalid Listening section delivery reason={}",
                        exception.getMessage());
            }
        }
        String canonicalSafeReference = safeInternalMaterialReference(canonicalReference);
        String legacySafeReference = safeInternalMaterialReference(legacyFallback);
        String reference = firstNonBlank(canonicalSafeReference, legacySafeReference);
        if (isBlank(reference)) {
            throw new IllegalStateException(
                    "Phần Listening chưa có audio thử loa bất biến hợp lệ.");
        }
        return reference;
    }

    public record ListeningPreflightDelivery(
            Long setId,
            Long testId,
            Long sectionId,
            String sectionTitle,
            String checkAudioReference
    ) {
    }

    @Transactional(readOnly = true)
    public SpeakingPlayerDelivery getSpeakingPlayerDelivery(Long attemptId, Long userId) {
        PracticeAttempt attempt = getPracticeAttempt(attemptId, userId);
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new IllegalStateException("Lượt Speaking đã kết thúc.");
        }
        if (!"SPEAKING".equals(attempt.getSkill())) {
            throw new IllegalArgumentException("Lượt làm bài không thuộc kỹ năng Speaking.");
        }

        PracticeVersionSnapshot version = versionSnapshot(attempt)
                .orElseThrow(() -> new IllegalStateException(
                        "Speaking attempt is missing an immutable delivery version."));
        if (!attempt.getSetId().equals(version.setVersion().getSetId())
                || !attempt.getTestId().equals(version.testVersion().getTestId())
                || !attempt.getSectionId().equals(version.sectionVersion().getSectionId())
                || !"SPEAKING".equals(version.sectionVersion().getSkill())) {
            throw new IllegalStateException("Speaking attempt delivery version is inconsistent.");
        }

        Map<Long, PracticeQuestionGroupVersion> groupsById = version.groups().stream().collect(Collectors.toMap(
                PracticeQuestionGroupVersion::getId,
                group -> group,
                (left, right) -> left,
                LinkedHashMap::new));

        List<SpeakingPlayerQuestion> questions = version.questions().stream()
                .sorted(Comparator.comparing(
                                PracticeQuestionVersion::getDisplayOrder,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(
                                PracticeQuestionVersion::getQuestionNo,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(
                                PracticeQuestionVersion::getId,
                                Comparator.nullsLast(Long::compareTo)))
                .map(question -> toSpeakingPlayerQuestion(question, groupsById))
                .toList();
        if (questions.isEmpty()) {
            throw new IllegalStateException("Phần Speaking chưa có câu hỏi hợp lệ.");
        }

        return new SpeakingPlayerDelivery(
                attempt.getId(),
                attempt.getSetId(),
                attempt.getTestId(),
                attempt.getSectionId(),
                version.setVersion().getTitle(),
                version.testVersion().getTitle(),
                version.sectionVersion().getTitle(),
                questions);
    }

    private SpeakingPlayerQuestion toSpeakingPlayerQuestion(
            PracticeQuestionVersion question,
            Map<Long, PracticeQuestionGroupVersion> groupsById) {
        if (!PracticeQuestion.TYPE_SPEAKING.equals(question.getQuestionType())) {
            throw new IllegalStateException(
                    "Speaking delivery contains a non-SPEAKING question: " + question.getQuestionId());
        }
        PracticeQuestionGroupVersion group = groupsById.get(question.getGroupVersionId());
        QuestionContent content = legacyCompatibleSpeakingContent(question);
        QuestionContent.SpeakingDelivery delivery = content.speakingDelivery();
        String promptAudioReference = firstNonBlank(
                safeInternalMaterialReference(delivery == null ? null : delivery.promptAudioReference()),
                firstNonBlank(
                        safeInternalMaterialReference(content.audioReference()),
                        safeInternalMaterialReference(group == null ? null : group.getAudioUrl())));
        if (isBlank(promptAudioReference)) {
            throw new IllegalStateException(
                    "Speaking question is missing immutable prompt audio: " + question.getQuestionId());
        }
        String imageReference = firstNonBlank(
                safeInternalMaterialReference(content.imageReference()),
                firstNonBlank(
                        firstMarkdownImageReference(question.getPrompt()),
                        group == null ? null : groupImageReference(
                                group.getImageUrl(), group.getInstruction())));
        return new SpeakingPlayerQuestion(
                question.getQuestionId(),
                question.getQuestionNo(),
                group == null || group.getGroupLabel() == null || group.getGroupLabel().isBlank()
                        ? "Phần nói" : group.getGroupLabel(),
                stripMarkdownImages(question.getPrompt()),
                question.getPoints(),
                blankToNull(imageReference),
                promptAudioReference,
                delivery == null || delivery.promptPlayLimit() == null ? 1 : delivery.promptPlayLimit(),
                delivery == null || delivery.preparationSeconds() == null ? 30 : delivery.preparationSeconds(),
                delivery == null || delivery.responseSeconds() == null ? 60 : delivery.responseSeconds());
    }

    private QuestionContent legacyCompatibleSpeakingContent(PracticeQuestionVersion question) {
        String json = question.getQuestionContentJson();
        if (json != null && !json.isBlank()) {
            try {
                return assessmentContractCodec.readQuestionContent(json, CanonicalQuestionType.SPEAKING);
            } catch (IllegalArgumentException exception) {
                log.warn("[PracticeService] Invalid canonical Speaking content questionId={} versionId={} reason={}",
                        question.getQuestionId(), question.getId(), exception.getMessage());
            }
        }
        return assessmentContractCodec.adaptLegacyContent(
                question.getOptionsJson(), PracticeQuestion.TYPE_SPEAKING);
    }

    private static String firstMarkdownImageReference(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(value);
        return matcher.find() ? safeLegacyMarkdownImageReference(matcher.group(1).trim()) : "";
    }

    private static String safeLegacyMarkdownImageReference(String reference) {
        if (reference == null || reference.isBlank()
                || reference.contains("\n")
                || reference.contains("\r")) {
            return "";
        }
        return safeInternalMaterialReference(reference);
    }

    private static String safeInternalMaterialReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return "";
        }
        String normalized = reference.trim();
        return MATERIAL_CONTENT_REFERENCE_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private static String stripMarkdownImages(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return MARKDOWN_IMAGE_PATTERN.matcher(value).replaceAll("").trim();
    }

    private static String groupImageReference(String imageReference, String instruction) {
        return blankToNull(firstNonBlank(
                safeInternalMaterialReference(imageReference),
                firstMarkdownImageReference(instruction)));
    }

    public record SpeakingPlayerDelivery(
            Long attemptId,
            Long setId,
            Long testId,
            Long sectionId,
            String setTitle,
            String testTitle,
            String sectionTitle,
            List<SpeakingPlayerQuestion> questions
    ) {
        public SpeakingPlayerDelivery {
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    public record SpeakingPlayerQuestion(
            Long questionId,
            Integer questionNo,
            String groupLabel,
            String prompt,
            BigDecimal points,
            String imageReference,
            String promptAudioReference,
            Integer promptPlayLimit,
            Integer preparationSeconds,
            Integer responseSeconds
    ) {
    }

    private PracticeQuestionRow toQuestionRow(PracticeQuestionVersion question) {
        QuestionContent content = questionContentForDisplay(
                question.getQuestionContentJson(), question.getOptionsJson(), question.getQuestionType());
        String imageReference = firstNonBlank(
                safeInternalMaterialReference(content == null ? null : content.imageReference()),
                firstMarkdownImageReference(question.getPrompt()));
        String audioReference = safeInternalMaterialReference(
                content == null ? null : content.audioReference());
        return new PracticeQuestionRow(
                question.getQuestionId(),
                question.getQuestionNo(),
                question.getQuestionType(),
                stripMarkdownImages(question.getPrompt()),
                readOptions(question.getOptionsJson()),
                question.getAnswerKey(),
                question.getExplanation(),
                groupLabel(question.getQuestionNo()),
                blankToNull(imageReference),
                blankToNull(audioReference),
                optionRows(content, readOptions(question.getOptionsJson())),
                blankRows(content)
        );
    }

    private List<PracticeQuestionGroupRow> fallbackGrouping(List<PracticeQuestion> dbQuestions) {
        Map<String, List<PracticeQuestionRow>> grouped = new LinkedHashMap<>();
        for (PracticeQuestion q : dbQuestions) {
            String label = groupLabel(q.getQuestionNo());
            grouped.computeIfAbsent(label, k -> new ArrayList<>()).add(toQuestionRow(q));
        }

        List<PracticeQuestionGroupRow> groupRows = new ArrayList<>();
        for (Map.Entry<String, List<PracticeQuestionRow>> entry : grouped.entrySet()) {
            String label = entry.getKey();
            List<PracticeQuestionRow> questions = entry.getValue();
            int from = questions.stream().mapToInt(PracticeQuestionRow::questionNo).min().orElse(1);
            int to = questions.stream().mapToInt(PracticeQuestionRow::questionNo).max().orElse(1);
            groupRows.add(new PracticeQuestionGroupRow(
                    null,
                    null,
                    label,
                    from,
                    to,
                    "Nhóm câu " + label,
                    null,
                    null,
                    questions
            ));
        }
        return groupRows;
    }

    private List<String> readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private QuestionContent questionContentForDisplay(String questionContentJson,
                                                      String optionsJson,
                                                      String questionType) {
        try {
            CanonicalQuestionType type = questionTypeResolver.resolve(questionType);
            return isBlank(questionContentJson)
                    ? assessmentContractCodec.adaptLegacyContent(optionsJson, questionType)
                    : assessmentContractCodec.readQuestionContent(questionContentJson, type);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String questionImageReference(QuestionSnapshot question) {
        if (question == null) {
            return null;
        }
        QuestionContent content = questionContentForDisplay(
                question.questionContentJson(), question.optionsJson(), question.questionType());
        String canonicalReference = content == null ? null : content.imageReference();
        return blankToNull(firstNonBlank(
                safeInternalMaterialReference(canonicalReference),
                firstMarkdownImageReference(question.prompt())));
    }

    private static List<PracticeQuestionOptionRow> optionRows(QuestionContent content, List<String> legacyOptions) {
        if (content == null || content.options().isEmpty()) {
            if (legacyOptions == null || legacyOptions.isEmpty()) {
                return List.of();
            }
            return java.util.stream.IntStream.range(0, legacyOptions.size())
                    .mapToObj(index -> new PracticeQuestionOptionRow(
                            "opt_" + (index + 1), legacyOptions.get(index), null))
                    .toList();
        }
        return content.options().stream()
                .map(option -> new PracticeQuestionOptionRow(
                        option.id(),
                        option.text(),
                        blankToNull(safeInternalMaterialReference(option.imageReference()))))
                .toList();
    }

    private static List<com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionBlankRow> blankRows(
            QuestionContent content
    ) {
        if (content == null || content.blanks().isEmpty()) {
            return List.of();
        }
        return content.blanks().stream()
                .map(blank -> new com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionBlankRow(
                        blank.id(), blank.prompt()))
                .toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private BigDecimal extractAiScore(String aiFeedback) {
        try {
            return WritingScoringPolicy.percentageFromFeedback(objectMapper.readTree(aiFeedback));
        } catch (Exception ex) {
            // Fallback: score band 1.0 = "Không phản hồi" → 11.11/100
            return WritingScoreMatrix.toHundredPointScale(1.0);
        }
    }

    private BigDecimal earnedSpeakingPoints(BigDecimal configuredPoints, BigDecimal perQuestionPercentage) {
        if (configuredPoints == null || configuredPoints.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal percentage = clamp(
                perQuestionPercentage == null ? BigDecimal.ZERO : perQuestionPercentage,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100)
        );
        return configuredPoints.multiply(percentage)
                .divide(BigDecimal.valueOf(100), java.math.MathContext.DECIMAL128);
    }

    private JsonNode readFeedbackObject(String feedbackJson) {
        try {
            JsonNode node = objectMapper.readTree(feedbackJson);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String scoreLabel(BigDecimal score, BigDecimal total) {
        if (score == null || total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return "0%";
        }
        return score.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private static String percentageLabel(BigDecimal score) {
        if (score == null) {
            return "0%";
        }
        return score.setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private static String groupLabel(Integer questionNo) {
        if (questionNo == null) {
            return "Câu";
        }
        int q = questionNo;
        if (q == 51 || q == 52) return "51-52";
        if (q == 53) return "53";
        if (q == 54) return "54";
        if (q <= 2) return "1-2";
        if (q <= 4) return "3-4";
        if (q <= 8) return "5-8";
        if (q <= 12) return "9-12";
        if (q <= 15) return "13-15";
        if (q <= 18) return "16-18";
        if (q <= 20) return "19-20";
        if (q <= 22) return "21-22";
        if (q <= 38) return "23-38";
        if (q <= 50) return "39-50";
        return String.valueOf(q);
    }

    private static boolean isAutoScoredByKey(String questionType) {
        return questionType != null && switch (questionType) {
            case PracticeQuestion.TYPE_SINGLE_CHOICE,
                    PracticeQuestion.TYPE_TRUE_FALSE_NOT_GIVEN,
                    PracticeQuestion.TYPE_FILL_BLANK -> true;
            default -> false;
        };
    }

    private static boolean usesAnswerExplanations(PracticeSet set) {
        return PracticeSet.SKILL_READING.equals(set.getSkill()) 
                || PracticeSet.SKILL_LISTENING.equals(set.getSkill())
                || "MIXED".equals(set.getSkill());
    }

    private boolean hasWritingOrSpeaking(List<PracticeQuestion> questions) {
        for (PracticeQuestion q : questions) {
            if (PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType()) 
                    || PracticeQuestion.TYPE_SPEAKING.equals(q.getQuestionType())) {
                return true;
            }
        }
        return false;
    }

    private List<PracticeAnswerExplanationRow> answerExplanationRows(String explanationJson,
                                                                     List<PracticeQuestionRow> questions,
                                                                     String answersJson) {
        Map<String, JsonNode> explanationByQuestionId = readExplanationMap(explanationJson);
        Map<String, String> answers = readAnswers(answersJson);
        List<PracticeAnswerExplanationRow> rows = new ArrayList<>(questions.size());
        for (PracticeQuestionRow question : questions) {
            JsonNode explanation = explanationByQuestionId.get(String.valueOf(question.id()));
            List<EliminatedOptionExplanation> elims = new ArrayList<>();
            if (explanation != null && explanation.has("eliminatedOptions") && explanation.path("eliminatedOptions").isArray()) {
                for (JsonNode optNode : explanation.path("eliminatedOptions")) {
                    elims.add(new EliminatedOptionExplanation(
                            optNode.path("optionKey").asText(""),
                            optNode.path("reasonVi").asText("")
                    ));
                }
            }
            rows.add(new PracticeAnswerExplanationRow(
                    question.questionNo(),
                    question.questionType(),
                    question.prompt(),
                    answers.getOrDefault(String.valueOf(question.id()), ""),
                    question.answerKey(),
                    textOrFallback(explanation, "meaningVi", "AI chưa tách được phần dịch nghĩa cho câu này."),
                    textOrFallback(explanation, "evidenceQuote", "Không có trích dẫn."),
                    textOrFallback(explanation, "correctReasonVi", fallbackExplanation(question)),
                    textOrFallback(explanation, "relatedTranslationVi", "Không có dịch nghĩa."),
                    elims
            ));
        }
        return rows;
    }

    private Map<String, JsonNode> readExplanationMap(String explanationJson) {
        Map<String, JsonNode> rows = new LinkedHashMap<>();
        if (explanationJson == null || explanationJson.isBlank()) {
            return rows;
        }
        try {
            JsonNode root = objectMapper.readTree(explanationJson);
            for (JsonNode item : root.path("items")) {
                String questionId = item.path("questionId").asText("");
                if (!questionId.isBlank()) {
                    rows.put(questionId, item);
                }
            }
        } catch (Exception ex) {
            return Map.of();
        }
        return rows;
    }

    private Map<String, String> readAnswers(String answersJson) {
        if (answersJson == null || answersJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(answersJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<PracticeAnswerReviewRow> answerReviewRows(List<PracticeQuestionRow> questions, String answersJson) {
        Map<String, String> answers = readAnswers(answersJson);
        return questions.stream()
                .map(question -> new PracticeAnswerReviewRow(
                        question.id(),
                        question.questionNo(),
                        question.questionType(),
                        question.prompt(),
                        answers.getOrDefault(String.valueOf(question.id()), "")))
                .toList();
    }

    private static String textOrFallback(JsonNode node, String field, String fallback) {
        if (node != null && node.path(field).isTextual() && !node.path(field).asText().isBlank()) {
            return node.path(field).asText();
        }
        return fallback;
    }

    private static String fallbackExplanation(PracticeQuestionRow question) {
        if (question.explanation() != null && !question.explanation().isBlank()) {
            return question.explanation();
        }
        return "Đáp án đúng được chấm theo key đã lưu: " + (question.answerKey() == null ? "" : question.answerKey());
    }

    private static boolean answersMatch(String answer, String answerKey) {
        String left = normalizeKey(answer);
        String right = normalizeKey(answerKey);
        if (left.isBlank() || right.isBlank()) {
            return false;
        }
        return left.equals(right);
    }

    private Optional<AssessmentScoreResult> scoreObjective(QuestionSnapshot question, String rawAnswer) {
        Optional<CanonicalQuestionType> resolvedType = questionTypeResolver.resolveOptional(
                question.questionType());
        if (resolvedType.isEmpty()) {
            if (isAutoScoredByKey(question.questionType())) {
                return Optional.of(legacyBinaryScore(question, rawAnswer));
            }
            throw new IllegalStateException("Unsupported practice question type for question ID "
                    + question.questionId() + ": " + question.questionType());
        }

        CanonicalQuestionType type = resolvedType.get();
        if (!type.isObjective()) {
            return Optional.empty();
        }

        if (isBlank(question.answerSpecJson())) {
            if (!isBlank(question.questionContentJson())) {
                throw new IllegalStateException("Question content has no answer spec for question ID "
                        + question.questionId());
            }
        }

        boolean legacyContract = isBlank(question.questionContentJson()) && isBlank(question.answerSpecJson());
        try {
            QuestionContent content = isBlank(question.questionContentJson())
                    ? assessmentContractCodec.adaptLegacyContent(question.optionsJson(), question.questionType())
                    : assessmentContractCodec.readQuestionContent(question.questionContentJson(), type);
            AnswerSpec spec = isBlank(question.answerSpecJson())
                    ? assessmentContractCodec.adaptLegacyAnswerSpec(question.questionType(), question.answerKey(), content)
                    : assessmentContractCodec.readAnswerSpec(question.answerSpecJson(), content);
            LearnerAnswer answer = readLearnerAnswer(type, rawAnswer, content);
            return Optional.of(assessmentScoringEngine.score(spec, answer, question.points()));
        } catch (IllegalArgumentException exception) {
            if (legacyContract) {
                return Optional.of(legacyBinaryScore(question, rawAnswer));
            }
            throw new IllegalStateException("Cannot score question ID " + question.questionId()
                    + " with its assessment contract", exception);
        }
    }

    private LearnerAnswer readLearnerAnswer(CanonicalQuestionType type,
                                            String rawAnswer,
                                            QuestionContent content) {
        if (isBlank(rawAnswer)) {
            return new LearnerAnswer(
                    LearnerAnswer.SCHEMA_VERSION,
                    type,
                    List.of(),
                    null,
                    Map.of(),
                    null
            );
        }
        if (rawAnswer.trim().startsWith("{")) {
            LearnerAnswer typed = assessmentContractCodec.readLearnerAnswer(rawAnswer);
            if (typed.questionType() != type) {
                throw new IllegalArgumentException("Learner answer type does not match question type");
            }
            return typed;
        }
        return assessmentContractCodec.adaptLegacyLearnerAnswer(type.name(), rawAnswer, content);
    }

    private AssessmentScoreResult legacyBinaryScore(QuestionSnapshot question, String rawAnswer) {
        boolean correct = answersMatch(rawAnswer, question.answerKey());
        return new AssessmentScoreResult(
                correct ? AssessmentScoreStatus.CORRECT
                        : (isBlank(rawAnswer) ? AssessmentScoreStatus.NOT_ANSWERED : AssessmentScoreStatus.INCORRECT),
                correct ? question.points() : BigDecimal.ZERO,
                question.points(),
                ScoringPolicyCode.ALL_OR_NOTHING,
                correct ? 1 : 0,
                1
        );
    }

    private AssessmentScoreResult scoreResultForRow(Map<Long, QuestionSnapshot> questions,
                                                    Long questionId,
                                                    String rawAnswer) {
        QuestionSnapshot question = questions.get(questionId);
        if (question == null) {
            throw new IllegalStateException("Missing scoring snapshot for question ID " + questionId);
        }
        return scoreObjective(question, rawAnswer)
                .orElseThrow(() -> new IllegalStateException(
                        "Question ID " + questionId + " is not objectively scoreable"));
    }

    private String explanationForAttempt(PracticeAttempt attempt,
                                         PracticeSet set,
                                         Optional<PracticeVersionSnapshot> lockedSnapshot,
                                         QuestionSnapshot question,
                                         PracticeQuestionGroupRow group,
                                         String rawLearnerAnswer,
                                         String optionLabelMode) {
        if (question == null) {
            throw new IllegalStateException("Missing question snapshot for explanation");
        }
        try {
            CanonicalQuestionType type = questionTypeResolver.resolve(
                    question.questionType());
            QuestionContent content = isBlank(question.questionContentJson())
                    ? assessmentContractCodec.adaptLegacyContent(question.optionsJson(), question.questionType())
                    : assessmentContractCodec.readQuestionContent(question.questionContentJson(), type);
            AnswerSpec answerSpec = isBlank(question.answerSpecJson())
                    ? assessmentContractCodec.adaptLegacyAnswerSpec(
                            question.questionType(), question.answerKey(), content)
                    : assessmentContractCodec.readAnswerSpec(question.answerSpecJson(), content);
            LearnerAnswer learnerAnswer = readLearnerAnswer(type, rawLearnerAnswer, content);
            AssessmentSkill skill = AssessmentSkill.valueOf(attempt.getSkill());
            AssessmentStimulus stimulus = skill == AssessmentSkill.READING
                    ? AssessmentStimulus.readingPassage(
                            firstNonBlank(group.passageText(), group.instruction()),
                            stimulusProvenance(group, "PUBLISHED_GROUP_SNAPSHOT"))
                    : AssessmentStimulus.listeningAudio(
                            group.audioUrl(),
                            group.transcriptText(),
                            stimulusProvenance(group, "PUBLISHED_GROUP_SNAPSHOT"),
                            stimulusApproved(group));
            ExplanationContext context = new ExplanationContext(
                    ExplanationContext.SCHEMA_VERSION,
                    question.questionId(),
                    question.questionVersionId(),
                    question.questionNo(),
                    skill,
                    type,
                    question.prompt(),
                    content,
                    answerSpec,
                    learnerAnswer,
                    stimulus,
                    question.teacherExplanation(),
                    "vi",
                    optionLabelMode
            );
            String generated = readingListeningExplanationService.getOrCreateExplanation(
                    context,
                    attempt.getTestId(),
                    attempt.getUserId(),
                    firstNonBlank(
                            safeInternalMaterialReference(content.imageReference()),
                            safeInternalMaterialReference(group.imageUrl())));
            return isBlank(generated) ? question.teacherExplanation() : generated;
        } catch (IllegalArgumentException exception) {
            log.warn("[PracticeService] Explanation contract unavailable questionId={} type={} exception={}",
                    question.questionId(), question.questionType(), exceptionCategory(exception));
            return question.teacherExplanation();
        }
    }

    private static void requireSubmittedForExplanation(PracticeAttempt attempt) {
        if (!PracticeAttempt.STATUS_SUBMITTED.equals(attempt.getStatus())
                && !PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus())) {
            throw new IllegalStateException("Kết quả chỉ khả dụng sau khi bài làm đã được nộp.");
        }
    }

    private static String scoreRatioLabel(BigDecimal earned, BigDecimal possible) {
        if (possible == null || possible.signum() <= 0 || earned == null) {
            return "0%";
        }
        return earned.multiply(BigDecimal.valueOf(100))
                .divide(possible, 0, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String stimulusProvenance(PracticeQuestionGroupRow group, String fallback) {
        if (isBlank(group.stimulusProvenanceJson())) {
            return fallback;
        }
        try {
            String source = objectMapper.readTree(group.stimulusProvenanceJson()).path("source").asText("");
            return source.isBlank() ? fallback : source;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean stimulusApproved(PracticeQuestionGroupRow group) {
        if (isBlank(group.stimulusProvenanceJson())) {
            return !isBlank(group.transcriptText());
        }
        try {
            return objectMapper.readTree(group.stimulusProvenanceJson()).path("approved").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("\\s+", " ")
                .replace("／", "/")
                .replace("，", ",")
                .toUpperCase();
    }






    private String mockSpeakingFeedback(String prompt, String answer) {
        Map<String, Object> feedback = new LinkedHashMap<>();
        int wordCount = answer == null || answer.isBlank() ? 0 : answer.trim().split("\\s+").length;
        double score = wordCount < 8 ? 3.0 : wordCount < 25 ? 5.5 : 7.0;
        feedback.put("score", score);
        feedback.put("overall_score", score);
        feedback.put("percentage", WritingScoreMatrix.toHundredPointScale(score));
        feedback.put("engine", "text_simulated_mock");
        feedback.put("source", "practice_speaking_mock");
        feedback.put("summary_vi", "Đây là đánh giá mô phỏng cho kỹ năng nói. Hệ thống ghi nhận độ dài câu trả lời, mức độ bám câu hỏi và sự mạch lạc cơ bản.");
        feedback.put("rubric_scores", List.of(
                Map.of("name", "Nội dung & Thực hiện nhiệm vụ (내용 및 과제 수행)", "score", score, "feedback", "Câu trả lời cần bám sát câu hỏi và có ví dụ cụ thể hơn."),
                Map.of("name", "Khả năng xây dựng bài nói (담화 구성)", "score", Math.max(1.0, score - 0.5), "feedback", "Nên mở ý, giải thích và kết luận ngắn gọn để bài nói mạch lạc."),
                Map.of("name", "Khả năng kiểm soát ngôn ngữ (언어 수행)", "score", Math.max(1.0, score - 1.0), "feedback", "Nên dùng từ và cấu trúc câu rõ ràng, phù hợp với nội dung trả lời.")
        ));
        feedback.put("strengths", List.of(Map.of(
                "criterionId", "S_FLUENCY",
                "explanationVi", "Bạn đã có phản hồi cho câu hỏi, đây là điểm khởi đầu tốt để luyện nói.",
                "correction", ""
        )));
        feedback.put("needs_improvement", List.of(Map.of(
                "criterionId", "S_TEXT_CLARITY_IMPROVEMENT",
                "explanationVi", "Hãy phát triển câu trả lời rõ ý hơn bằng lý do và ví dụ cụ thể.",
                "correction", "답변을 더 길고 구체적으로 말해 보세요."
        )));
        feedback.put("sample_answer", "저는 이 질문에 대해 먼저 제 경험을 말하고, 그 이유를 설명한 다음 짧게 결론을 말하겠습니다.");
        feedback.put("corrected_version", "Hãy trả lời theo cấu trúc: trả lời trực tiếp - lý do - ví dụ - kết luận ngắn.");
        return writeJson(feedback);
    }

    @Transactional(readOnly = true)
    public PracticeSection getSection(Long sectionId) {
        return sectionRepository.findById(sectionId)
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));
    }

    @Transactional(readOnly = true)
    public PracticeAttempt getPracticeAttempt(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Lượt làm bài không tồn tại"));
        rejectDiscardedAttempt(attempt);
        return attempt;
    }

    @Transactional(readOnly = true)
    public List<PracticeSection> getSectionsForTest(Long setId, Long testId) {
        return sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId).stream()
                .filter(s -> testId.equals(s.getTestId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PracticeQuestionGroupRow> getQuestionGroupsForSection(Long setId, Long sectionId) {
        PracticeSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Section không tồn tại"));
        Long testId = section.getTestId();

        List<PracticeQuestionGroup> dbGroups = groupRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        List<PracticeQuestion> dbQuestions = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        List<PracticeSection> testSections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId).stream()
                .filter(s -> testId.equals(s.getTestId()))
                .toList();

        // Step 1: Strict match
        List<PracticeQuestionGroup> secGroups = dbGroups.stream()
                .filter(g -> sectionId.equals(g.getSectionId()))
                .toList();

        // Step 2: Legacy fallback for single-section test
        if (secGroups.isEmpty() && testSections.size() == 1) {
            secGroups = dbGroups.stream()
                    .filter(g -> g.getSectionId() == null)
                    .toList();
        }

        // Step 3: Multi-section protection
        if (secGroups.isEmpty() && testSections.size() > 1) {
            throw new IllegalStateException("Không thể xác định câu hỏi cho sectionId=" + sectionId 
                    + " vì nhóm câu hỏi rỗng và bài thi có nhiều phần.");
        }

        List<PracticeQuestionGroupRow> groups = new ArrayList<>();
        for (PracticeQuestionGroup g : secGroups) {
            List<PracticeQuestionRow> qRows = dbQuestions.stream()
                    .filter(q -> java.util.Objects.equals(g.getId(), q.getGroupId()))
                    .map(this::toQuestionRow)
                    .toList();
            groups.add(toGroupRow(g, qRows));
        }

        // Dummy group for orphan questions in single-section test
        if (testSections.size() == 1) {
            List<PracticeQuestionRow> orphanQuestions = dbQuestions.stream()
                    .filter(q -> q.getGroupId() == null)
                    .map(this::toQuestionRow)
                    .toList();
            if (!orphanQuestions.isEmpty()) {
                groups.add(new PracticeQuestionGroupRow(
                        null,
                        sectionId,
                        "Phần thi",
                        1,
                        orphanQuestions.size(),
                        null,
                        null,
                        null,
                        orphanQuestions
                ));
            }
        }

        return groups;
    }

    @Transactional(readOnly = true)
    public List<PracticeQuestionGroupRow> getPlayerQuestionGroupsForAttempt(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Lượt làm bài không tồn tại"));
        rejectDiscardedAttempt(attempt);
        Optional<PracticeVersionSnapshot> snapshot = versionSnapshot(attempt);
        if (snapshot.isPresent()) {
            validateAttemptSnapshot(attempt, snapshot.get());
            return redactPlayerGroups(groupRowsForAttempt(attempt, null, snapshot));
        }
        if ("SPEAKING".equals(attempt.getSkill())) {
            throw new IllegalStateException("Speaking attempt is missing an immutable delivery version.");
        }
        return redactPlayerGroups(groupRowsForAttempt(attempt, liveSectionForAttempt(attempt), snapshot));
    }

    private static List<PracticeQuestionGroupRow> redactPlayerGroups(
            List<PracticeQuestionGroupRow> groups) {
        return groups.stream()
                .map(group -> new PracticeQuestionGroupRow(
                        group.id(),
                        group.sectionId(),
                        group.groupLabel(),
                        group.questionFrom(),
                        group.questionTo(),
                        group.instruction(),
                        group.stimulusType(),
                        group.passageText(),
                        null,
                        group.imageUrl(),
                        null,
                        group.audioUrl(),
                        group.exampleBox(),
                        group.questions().stream()
                                .map(question -> new PracticeQuestionRow(
                                        question.id(),
                                        question.questionNo(),
                                        question.questionType(),
                                        question.prompt(),
                                        question.options(),
                                        null,
                                        null,
                                        question.groupLabel(),
                                        question.imageReference(),
                                        question.audioReference(),
                                        question.optionRows(),
                                        question.blankRows()))
                                .toList()))
                .toList();
    }

    @Transactional
    public Long startAttempt(Long setId, Long testId, Long sectionId, Long userId) {
        PracticeSet set = setRepository.findById(setId)
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        if (!PracticeSet.STATUS_PUBLISHED.equals(set.getStatus())) {
            throw new EntityNotFoundException("Bộ luyện tập chưa được xuất bản");
        }

        PracticeTest test = testRepository.findById(testId)
                .orElseThrow(() -> new EntityNotFoundException("Bài thi không tồn tại"));
        if (!setId.equals(test.getSetId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Bài thi không thuộc bộ luyện tập này");
        }

        PracticeSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));
        if (!setId.equals(section.getSetId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Section không thuộc bộ luyện tập này");
        }
        if (!testId.equals(section.getTestId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Section không thuộc bài thi này");
        }

        String liveSkill = section.getSkill();
        if (!isSupportedSkill(liveSkill)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Skill không hợp lệ");
        }

        PracticeSet lockedSet = setRepository.findByIdForUpdate(setId)
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        if (!PracticeSet.STATUS_PUBLISHED.equals(lockedSet.getStatus())) {
            throw new EntityNotFoundException("Bộ luyện tập chưa được xuất bản");
        }
        PracticeTest lockedTest = testRepository.findByIdForShare(testId)
                .orElseThrow(() -> new EntityNotFoundException("Bài thi không tồn tại"));
        if (!setId.equals(lockedTest.getSetId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Bài thi không thuộc bộ luyện tập này");
        }
        PracticeSection lockedSection = sectionRepository.findByIdForShare(sectionId)
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));
        if (!setId.equals(lockedSection.getSetId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Section không thuộc bộ luyện tập này");
        }
        if (!testId.equals(lockedSection.getTestId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Section không thuộc bài thi này");
        }
        if (!liveSkill.equals(lockedSection.getSkill())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Skill không hợp lệ");
        }

        Optional<PracticeAttemptVersionLock> versionLock = Optional.empty();
        String deliverySkill = liveSkill;
        if (publishedVersionService != null) {
            versionLock = publishedVersionService.latestLock(setId, testId, sectionId);
            if (versionLock.isEmpty()) {
                throw new EntityNotFoundException("Bộ luyện tập chưa có phiên bản xuất bản hợp lệ");
            }
            PracticeAttemptVersionLock lock = versionLock.get();
            PracticeVersionSnapshot snapshot = publishedVersionService.snapshot(
                            lock.publishedVersionId(), lock.setVersionId(),
                            lock.testVersionId(), lock.sectionVersionId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Bộ luyện tập chưa có phiên bản xuất bản nhất quán"));
            if (!setId.equals(snapshot.setVersion().getSetId())
                    || !testId.equals(snapshot.testVersion().getTestId())
                    || !sectionId.equals(snapshot.sectionVersion().getSectionId())
                    || !isSupportedSkill(snapshot.sectionVersion().getSkill())) {
                throw new IllegalStateException("Immutable attempt delivery does not match the requested section.");
            }
            deliverySkill = snapshot.sectionVersion().getSkill();
            if (!deliverySkill.equals(liveSkill)) {
                log.warn("Using immutable skill={} instead of mutable live skill={} for set={}, test={}, section={}",
                        deliverySkill, liveSkill, setId, testId, sectionId);
            }
        }

        Optional<PracticeAttempt> existing = attemptRepository
                .findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(
                        userId, testId, sectionId, PracticeAttempt.STATUS_IN_PROGRESS);

        if (existing.isPresent()) {
            PracticeAttempt attempt = existing.get();
            if (setId.equals(attempt.getSetId()) &&
                testId.equals(attempt.getTestId()) &&
                sectionId.equals(attempt.getSectionId()) &&
                deliverySkill.equals(attempt.getSkill()) &&
                userId.equals(attempt.getUserId()) &&
                PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
                if (versionLock.isEmpty() || hasVersionLock(attempt, versionLock.get())) {
                    log.info("[PracticeService] Reusing existing IN_PROGRESS PracticeAttempt id={}", attempt.getId());
                    return attempt.getId();
                }
            }
            attempt.discard(LocalDateTime.now());
            attemptRepository.save(attempt);
            log.warn("[PracticeService] Discarded stale IN_PROGRESS PracticeAttempt id={} before restart",
                    attempt.getId());
        }

        PracticeAttempt attempt = new PracticeAttempt(userId, setId, testId, deliverySkill, sectionId);
        versionLock.ifPresent(lock -> attempt.lockPublishedVersion(
                lock.publishedVersionId(), lock.setVersionId(), lock.testVersionId(), lock.sectionVersionId()));
        attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS);
        PracticeAttempt saved = attemptRepository.save(attempt);
        log.info("[PracticeService] Created new PracticeAttempt id={} section={}", saved.getId(), sectionId);
        return saved.getId();
    }

    private static boolean isSupportedSkill(String skill) {
        return "READING".equals(skill) || "LISTENING".equals(skill)
                || "WRITING".equals(skill) || "SPEAKING".equals(skill);
    }

    private static boolean hasVersionLock(PracticeAttempt attempt, PracticeAttemptVersionLock lock) {
        return Objects.equals(attempt.getPublishedVersionId(), lock.publishedVersionId())
                && Objects.equals(attempt.getSetVersionId(), lock.setVersionId())
                && Objects.equals(attempt.getTestVersionId(), lock.testVersionId())
                && Objects.equals(attempt.getSectionVersionId(), lock.sectionVersionId());
    }

    public Long submitAttempt(Long attemptId, Long userId, Map<String, String> form) {
        WritingGradingSnapshot snapshot = executeRead(() -> loadWritingSubmitSnapshot(attemptId, userId, form));
        if (snapshot != null) {
            WritingGradingResult result = executeNonTransactional(() -> gradeWritingSnapshot(snapshot, false));
            return executeWrite(() -> persistWritingSubmitResult(snapshot, result));
        }
        NonWritingEssayGradingSnapshot essaySnapshot =
                executeRead(() -> loadNonWritingEssaySubmitSnapshot(attemptId, userId, form));
        if (essaySnapshot != null) {
            NonWritingEssayGradingResult result =
                    executeNonTransactional(() -> gradeNonWritingEssaySnapshot(essaySnapshot, false));
            return executeWrite(() -> persistNonWritingEssaySubmitResult(essaySnapshot, result));
        }
        SpeakingGradingSnapshot speakingSnapshot =
                executeRead(() -> loadSpeakingSubmitSnapshot(attemptId, userId, form));
        if (speakingSnapshot != null) {
            SpeakingGradingResult result =
                    executeNonTransactional(() -> gradeSpeakingSnapshot(speakingSnapshot));
            return executeWrite(() -> persistSpeakingGradingResult(speakingSnapshot, result, true));
        }
        return executeWrite(() -> submitAttemptInTransaction(attemptId, userId, form));
    }

    private Long submitAttemptInTransaction(Long attemptId, Long userId, Map<String, String> form) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy lượt làm bài"));

        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new IllegalStateException("Lượt làm bài đã được nộp hoặc chấm điểm.");
        }

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));

        if (!attempt.getSetId().equals(section.getSetId()) ||
            !attempt.getTestId().equals(section.getTestId()) ||
            !attempt.getSkill().equals(section.getSkill())) {
            throw new IllegalArgumentException("Section metadata mismatch with attempt");
        }

        String skill = attempt.getSkill();
        if (skill == null || (!"READING".equals(skill) && !"LISTENING".equals(skill) &&
            !"WRITING".equals(skill) && !"SPEAKING".equals(skill))) {
            throw new IllegalArgumentException("Skill không hợp lệ");
        }

        loadPublished(attempt.getSetId());

        List<QuestionSnapshot> sectionQuestions = loadQuestionSnapshots(attempt, section.getId());

        Map<String, String> answers = new LinkedHashMap<>();
        if (attempt.getAnswersJson() != null && !attempt.getAnswersJson().isBlank()) {
            try {
                Map<String, String> prev = objectMapper.readValue(attempt.getAnswersJson(), new TypeReference<Map<String, String>>() {});
                answers.putAll(prev);
            } catch (Exception e) {
                log.warn("[submitAttempt] Failed to parse previous in-progress answers exception={}",
                        exceptionCategory(e));
            }
        }

        // Process only form fields that belong to sectionQuestions
        PracticeAnswerFormMapper.mergeAllowedQuestionAnswers(
                answers,
                form,
                sectionQuestions.stream().map(QuestionSnapshot::questionId).toList());

        if ("WRITING".equals(skill)) {
            throw new IllegalStateException("Writing attempt must use snapshot grading path.");
        }

        BigDecimal earnedPoints = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        Map<String, JsonNode> speakingFeedbackMap = new LinkedHashMap<>();
        String aiFeedback = null;

        for (QuestionSnapshot q : sectionQuestions) {
            total = total.add(q.points());
            String answer = answers.getOrDefault(String.valueOf(q.questionId()), "").trim();

            Optional<AssessmentScoreResult> objectiveScore = scoreObjective(q, answer);
            if (objectiveScore.isPresent()) {
                earnedPoints = earnedPoints.add(objectiveScore.get().earnedPoints());
            } else if (PracticeQuestion.TYPE_ESSAY.equals(q.questionType())) {
                throw new IllegalStateException("Essay attempt must use snapshot grading path.");
            } else if (PracticeQuestion.TYPE_SPEAKING.equals(q.questionType())) {
                String perQuestionFeedback = mockSpeakingFeedback(q.prompt(), answer);
                speakingFeedbackMap.put(String.valueOf(q.questionId()), readFeedbackObject(perQuestionFeedback));
                earnedPoints = earnedPoints.add(earnedSpeakingPoints(q.points(), extractAiScore(perQuestionFeedback)));
            } else {
                throw new IllegalStateException("Unsupported question type for question ID "
                        + q.questionId() + ": " + q.questionType());
            }
        }
        BigDecimal score = speakingFeedbackMap.isEmpty() ? earnedPoints : toWritingAttemptPercentage(earnedPoints, total);
        if (!speakingFeedbackMap.isEmpty()) {
            aiFeedback = writeJson(speakingFeedbackMap);
        }

        if (aiFeedback == null) {
            attempt.markSubmitted(score, total, writeJson(answers));
        } else {
            attempt.markGraded(score, total, writeJson(answers), aiFeedback);
        }

        attemptRepository.save(attempt);
        log.info("[PracticeService] Submitted PracticeAttempt id={} score={} / {}", attempt.getId(), score, total);
        return attempt.getId();
    }

    @Transactional
    public void saveInProgressAnswers(Long attemptId, Long userId, Map<String, String> form) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy lượt làm bài"));

        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new IllegalStateException("Chỉ có thể lưu nháp cho lượt làm bài chưa hoàn thành.");
        }

        Map<String, String> currentAnswers = new LinkedHashMap<>();
        if (attempt.getAnswersJson() != null && !attempt.getAnswersJson().isBlank()) {
            try {
                currentAnswers.putAll(objectMapper.readValue(attempt.getAnswersJson(), new TypeReference<Map<String, String>>() {}));
            } catch (Exception e) {
                log.warn("[saveInProgress] Failed to parse previous answers JSON exception={}",
                        exceptionCategory(e));
            }
        }

        // Merge new answers
        PracticeAnswerFormMapper.mergeAllAnswerFields(currentAnswers, form);

        attempt.setAnswersJson(writeJson(currentAnswers));
        attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS);
        attemptRepository.save(attempt);
    }

    /**
     * Unified result builder — replaces the old rl-result / result split.
     * PracticeSection.skill is the source of truth for which template fragment to render.
     */
    @Transactional(readOnly = true)
    public PracticeAttemptResultView getAttemptResult(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        rejectDiscardedAttempt(attempt);
        requireSubmittedForExplanation(attempt);
        PracticeSet set = setRepository.findById(attempt.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));

        Optional<PracticeVersionSnapshot> lockedSnapshot = versionSnapshot(attempt);
        List<PracticeQuestionGroupRow> groupRows = groupRowsForAttempt(attempt, section);
        List<PracticeQuestionRow> dbQuestions = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .toList();
        Map<Long, QuestionSnapshot> scoringQuestions = loadQuestionSnapshots(attempt, section.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuestionSnapshot::questionId,
                        question -> question,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        Map<String, String> submittedAnswers = readAnswers(attempt.getAnswersJson());
        String optionLabelMode = getOptionLabelMode(set);

        BigDecimal sectionScore = attempt.getScore() != null ? attempt.getScore() : BigDecimal.ZERO;
        BigDecimal sectionTotal = attempt.getTotalPoints() != null ? attempt.getTotalPoints() : BigDecimal.ZERO;

        String skill = section.getSkill();
        boolean isObjective = "READING".equals(skill) || "LISTENING".equals(skill);

        int correctCount = 0;
        int incorrectCount = 0;
        String sectionAiFeedback = null;

        // Performance breakdown by question type
        Map<String, List<PracticeQuestionRow>> byType = new LinkedHashMap<>();
        for (PracticeQuestionRow q : dbQuestions) {
            byType.computeIfAbsent(q.questionType(), k -> new ArrayList<>()).add(q);
        }

        List<PerformanceByTypeRow> perfByType = new ArrayList<>();
        for (Map.Entry<String, List<PracticeQuestionRow>> entry : byType.entrySet()) {
            String type = entry.getKey();
            List<PracticeQuestionRow> qs = entry.getValue();
            int total = qs.size();
            int correct = 0;
            BigDecimal earnedByType = BigDecimal.ZERO;
            BigDecimal possibleByType = BigDecimal.ZERO;
            for (PracticeQuestionRow q : qs) {
                String ans = submittedAnswers.getOrDefault(String.valueOf(q.id()), "").trim();
                if (isObjective || isAutoScoredByKey(type)) {
                    AssessmentScoreResult scoreResult = scoreResultForRow(scoringQuestions, q.id(), ans);
                    earnedByType = earnedByType.add(scoreResult.earnedPoints());
                    possibleByType = possibleByType.add(scoreResult.possiblePoints());
                    if (scoreResult.fullyCorrect()) {
                        correct++;
                    }
                }
            }
            if (isObjective) {
                int incorrect = total - correct;
                correctCount += correct;
                incorrectCount += incorrect;
                String accuracyPct = scoreRatioLabel(earnedByType, possibleByType);
                perfByType.add(new PerformanceByTypeRow(type, getQuestionTypeLabel(type), total, correct, incorrect, accuracyPct));
            }
        }

        if (!isObjective) {
            sectionAiFeedback = attempt.getAiFeedbackJson();
        }

        // Build review groups
        List<ReviewGroupRow> reviewGroups = new ArrayList<>();
        if (isObjective) {
            for (PracticeQuestionGroupRow g : groupRows) {
                List<ReviewQuestionRow> qRows = new ArrayList<>();
                for (PracticeQuestionRow q : g.questions()) {
                    String ans = submittedAnswers.getOrDefault(String.valueOf(q.id()), "").trim();
                    boolean isCorrect = scoreResultForRow(scoringQuestions, q.id(), ans).fullyCorrect();
                    String explanationJson = null;
                    try {
                        explanationJson = explanationForAttempt(
                                attempt,
                                set,
                                lockedSnapshot,
                                scoringQuestions.get(q.id()),
                                g,
                                ans,
                                optionLabelMode
                        );
                    } catch (Exception e) {
                        log.warn("[PracticeService] Failed to get explanation for question id={} exception={}",
                                q.id(), exceptionCategory(e));
                    }
                    qRows.add(new ReviewQuestionRow(q.id(), q.questionNo(), q.questionType(),
                            q.prompt(), q.options(), q.answerKey() != null ? q.answerKey() : "",
                            ans, isCorrect, explanationJson));
                }
                reviewGroups.add(new ReviewGroupRow(g.groupLabel(), g.instruction(),
                        firstNonBlank(g.passageText(), firstNonBlank(g.transcriptText(), g.instruction())),
                        audioStorageService.resolveUrlSafe(g.audioUrl()), qRows));
            }
        }

        List<SectionResultRow> sectionRows = new ArrayList<>();
        sectionRows.add(new SectionResultRow(
                lockedSnapshot.map(v -> v.sectionVersion().getSectionId()).orElse(section.getId()),
                lockedSnapshot.map(v -> v.sectionVersion().getTitle()).orElse(section.getTitle()),
                skill,
                correctCount,
                incorrectCount,
                dbQuestions.size(),
                sectionScore,
                sectionTotal,
                perfByType,
                reviewGroups,
                sectionAiFeedback,
                optionLabelMode
        ));

        String scoreLabel = sectionTotal.compareTo(BigDecimal.ZERO) == 0 ? "0%"
                : sectionScore.divide(sectionTotal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP) + "%";

        return new PracticeAttemptResultView(
                attempt.getId(),
                lockedSnapshot.map(PracticeVersionSnapshot::setVersion).map(PracticeService::toSetRow).orElseGet(() -> toSetRow(set)),
                sectionScore,
                sectionTotal,
                scoreLabel,
                attempt.getSubmittedAt(),
                sectionRows
        );
    }

    @Transactional(readOnly = true)
    public ReadingListeningResultView getReadingListeningResult(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        rejectDiscardedAttempt(attempt);
        requireSubmittedForExplanation(attempt);
        PracticeSet set = setRepository.findById(attempt.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));

        Optional<PracticeVersionSnapshot> lockedSnapshot = versionSnapshot(attempt);
        List<PracticeQuestionGroupRow> groupRows = groupRowsForAttempt(attempt, section);
        List<PracticeQuestionRow> dbQuestions = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .toList();
        Map<Long, QuestionSnapshot> scoringQuestions = loadQuestionSnapshots(attempt, section.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuestionSnapshot::questionId,
                        question -> question,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        Map<String, String> submittedAnswers = readAnswers(attempt.getAnswersJson());

        int correctCount = 0;
        int incorrectCount = 0;
        int totalCount = dbQuestions.size();

        // Calculate performance by question type
        Map<String, List<PracticeQuestionRow>> questionsByType = new LinkedHashMap<>();
        for (PracticeQuestionRow q : dbQuestions) {
            questionsByType.computeIfAbsent(q.questionType(), k -> new ArrayList<>()).add(q);
        }

        List<PerformanceByTypeRow> performanceByType = new ArrayList<>();
        for (Map.Entry<String, List<PracticeQuestionRow>> entry : questionsByType.entrySet()) {
            String type = entry.getKey();
            List<PracticeQuestionRow> qs = entry.getValue();
            int total = qs.size();
            int correct = 0;
            int incorrect = 0;
            BigDecimal earnedByType = BigDecimal.ZERO;
            BigDecimal possibleByType = BigDecimal.ZERO;
            for (PracticeQuestionRow q : qs) {
                String ans = submittedAnswers.getOrDefault(String.valueOf(q.id()), "").trim();
                AssessmentScoreResult scoreResult = scoreResultForRow(scoringQuestions, q.id(), ans);
                boolean isCorrect = scoreResult.fullyCorrect();
                earnedByType = earnedByType.add(scoreResult.earnedPoints());
                possibleByType = possibleByType.add(scoreResult.possiblePoints());
                if (isCorrect) {
                    correct++;
                } else {
                    incorrect++;
                }
            }
            correctCount += correct;
            incorrectCount += incorrect;

            String accuracyPct = scoreRatioLabel(earnedByType, possibleByType);
            performanceByType.add(new PerformanceByTypeRow(
                    type,
                    getQuestionTypeLabel(type),
                    total,
                    correct,
                    incorrect,
                    accuracyPct
            ));
        }

        String optionLabelMode = getOptionLabelMode(set);

        // Build group rows
        List<ReviewGroupRow> groups = new ArrayList<>();
        for (PracticeQuestionGroupRow g : groupRows) {
            List<ReviewQuestionRow> questions = new ArrayList<>();
            for (PracticeQuestionRow q : g.questions()) {
                String ans = submittedAnswers.getOrDefault(String.valueOf(q.id()), "").trim();
                boolean isCorrect = scoreResultForRow(scoringQuestions, q.id(), ans).fullyCorrect();
                String explanationJson = null;
                try {
                    explanationJson = explanationForAttempt(
                            attempt,
                            set,
                            lockedSnapshot,
                            scoringQuestions.get(q.id()),
                            g,
                            ans,
                            optionLabelMode
                    );
                } catch (Exception e) {
                    log.warn("[PracticeService] Failed to get explanation for question id={} exception={}",
                            q.id(), exceptionCategory(e));
                }
                questions.add(new ReviewQuestionRow(
                        q.id(),
                        q.questionNo(),
                        q.questionType(),
                        q.prompt(),
                        q.options(),
                        q.answerKey() != null ? q.answerKey() : "",
                        ans,
                        isCorrect,
                        explanationJson
                ));
            }
            groups.add(new ReviewGroupRow(
                    g.groupLabel(),
                    g.instruction(),
                    firstNonBlank(g.passageText(), firstNonBlank(g.transcriptText(), g.instruction())),
                    audioStorageService.resolveUrlSafe(g.audioUrl()),
                    questions
            ));
        }

        return new ReadingListeningResultView(
                attempt.getId(),
                attempt.getTestId(),
                lockedSnapshot.map(PracticeVersionSnapshot::setVersion).map(PracticeService::toSetRow).orElseGet(() -> toSetRow(set)),
                attempt.getScore(),
                attempt.getTotalPoints(),
                correctCount,
                incorrectCount,
                totalCount,
                performanceByType,
                groups,
                attempt.getAnswersJson(),
                optionLabelMode
        );
    }

    private static String getQuestionTypeLabel(String type) {
        if (type == null) return "객관식 (Trắc nghiệm)";
        return switch (type) {
            case PracticeQuestion.TYPE_SINGLE_CHOICE -> "객관식 (Trắc nghiệm)";
            case PracticeQuestion.TYPE_TRUE_FALSE_NOT_GIVEN -> "맞다/틀리다 (Đúng/Sai)";
            case PracticeQuestion.TYPE_FILL_BLANK -> "빈칸 채우기 (Điền từ)";
            case PracticeQuestion.TYPE_ESSAY -> "쓰기/주관식 (Tự luận)";
            case PracticeQuestion.TYPE_SPEAKING -> "말하기 (Nói)";
            default -> type;
        };
    }

    private String getOptionLabelMode(PracticeSet set) {
        return PracticeDtos.getOptionLabelMode(set.getTitle(), set.getMetadataJson());
    }

    private <T> T executeRead(Supplier<T> action) {
        if (readTransactionTemplate == null) {
            return action.get();
        }
        return readTransactionTemplate.execute(status -> action.get());
    }

    private <T> T executeNonTransactional(Supplier<T> action) {
        if (nonTransactionalTemplate == null) {
            return action.get();
        }
        return nonTransactionalTemplate.execute(status -> action.get());
    }

    private <T> T executeWrite(Supplier<T> action) {
        if (writeTransactionTemplate == null) {
            return action.get();
        }
        return writeTransactionTemplate.execute(status -> action.get());
    }

    private WritingGradingSnapshot loadWritingSubmitSnapshot(Long attemptId, Long userId, Map<String, String> form) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy lượt làm bài"));

        if (!"WRITING".equals(attempt.getSkill())) {
            return null;
        }
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new IllegalStateException("Lượt làm bài đã được nộp hoặc chấm điểm.");
        }

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));
        validateAttemptSection(attempt, section);
        loadPublished(attempt.getSetId());

        List<QuestionSnapshot> questions = loadQuestionSnapshots(attempt, section.getId());
        validateWritingQuestionPoints(questions);

        Map<String, String> answers = new LinkedHashMap<>();
        if (attempt.getAnswersJson() != null && !attempt.getAnswersJson().isBlank()) {
            try {
                Map<String, String> prev = objectMapper.readValue(attempt.getAnswersJson(), new TypeReference<Map<String, String>>() {});
                answers.putAll(prev);
            } catch (Exception e) {
                log.warn("[submitAttempt] Failed to parse previous in-progress answers exception={}",
                        exceptionCategory(e));
            }
        }
        PracticeAnswerFormMapper.mergeAllowedQuestionAnswers(
                answers,
                form,
                questions.stream().map(QuestionSnapshot::questionId).toList());

        return new WritingGradingSnapshot(
                attempt.getId(),
                attempt.getUserId(),
                attempt.getSectionId(),
                attempt.getSkill(),
                PracticeAttempt.STATUS_IN_PROGRESS,
                attempt.getLockVersion(),
                attempt.getAnswersJson(),
                writeJson(answers),
                answers,
                questions
        );
    }

    private WritingGradingSnapshot loadWritingReEvaluationSnapshot(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));

        if (!"WRITING".equals(attempt.getSkill())) {
            return null;
        }

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));
        validateAttemptSection(attempt, section);
        loadPublished(attempt.getSetId());

        List<QuestionSnapshot> questions = loadQuestionSnapshots(attempt, section.getId());
        validateWritingQuestionPoints(questions);
        Map<String, String> answers = readAnswers(attempt.getAnswersJson());

        return new WritingGradingSnapshot(
                attempt.getId(),
                attempt.getUserId(),
                attempt.getSectionId(),
                attempt.getSkill(),
                attempt.getStatus(),
                attempt.getLockVersion(),
                attempt.getAnswersJson(),
                writeJson(answers),
                answers,
                questions
        );
    }

    private WritingQuestionReEvaluationSnapshot loadWritingQuestionReEvaluationSnapshot(
            Long attemptId,
            Long questionId,
            Long userId
    ) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Ket qua khong ton tai"));

        if (!"WRITING".equals(attempt.getSkill())) {
            throw new IllegalArgumentException("Chi co the cham lai tung cau cho bai Writing.");
        }

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section khong ton tai"));
        validateAttemptSection(attempt, section);
        loadPublished(attempt.getSetId());

        List<QuestionSnapshot> questions = loadQuestionSnapshots(attempt, section.getId());
        validateWritingQuestionPoints(questions);
        QuestionSnapshot target = questions.stream()
                .filter(q -> q.questionId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cau hoi khong thuoc bai lam nay."));
        if (!PracticeQuestion.TYPE_ESSAY.equals(target.questionType())) {
            throw new IllegalArgumentException("Chi co the cham lai cau Writing ESSAY.");
        }

        return new WritingQuestionReEvaluationSnapshot(
                attempt.getId(),
                attempt.getUserId(),
                attempt.getSectionId(),
                attempt.getSkill(),
                attempt.getStatus(),
                attempt.getLockVersion(),
                attempt.getAnswersJson(),
                attempt.getAiFeedbackJson(),
                readAnswers(attempt.getAnswersJson()),
                questions,
                target
        );
    }

    private NonWritingEssayGradingSnapshot loadNonWritingEssaySubmitSnapshot(
            Long attemptId,
            Long userId,
            Map<String, String> form
    ) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy lượt làm bài"));
        if ("WRITING".equals(attempt.getSkill())) {
            throw new IllegalStateException("Writing attempt must use snapshot grading path.");
        }
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new IllegalStateException("Lượt làm bài đã được nộp hoặc chấm điểm.");
        }

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));
        validateAttemptSection(attempt, section);
        validateKnownSkill(attempt.getSkill());
        loadPublished(attempt.getSetId());

        List<QuestionSnapshot> questions = loadQuestionSnapshots(attempt, section.getId());
        if (!containsEssay(questions)) {
            return null;
        }

        Map<String, String> answers = new LinkedHashMap<>();
        if (attempt.getAnswersJson() != null && !attempt.getAnswersJson().isBlank()) {
            try {
                Map<String, String> prev = objectMapper.readValue(attempt.getAnswersJson(), new TypeReference<Map<String, String>>() {});
                answers.putAll(prev);
            } catch (Exception e) {
                log.warn("[submitAttempt] Failed to parse previous in-progress answers exception={}",
                        exceptionCategory(e));
            }
        }
        PracticeAnswerFormMapper.mergeAllowedQuestionAnswers(
                answers,
                form,
                questions.stream().map(QuestionSnapshot::questionId).toList());

        return new NonWritingEssayGradingSnapshot(
                attempt.getId(),
                attempt.getUserId(),
                attempt.getSetId(),
                attempt.getTestId(),
                attempt.getSectionId(),
                attempt.getSkill(),
                PracticeAttempt.STATUS_IN_PROGRESS,
                attempt.getLockVersion(),
                attempt.getAnswersJson(),
                attempt.getAiFeedbackJson(),
                attempt.getScore(),
                attempt.getTotalPoints(),
                writeJson(answers),
                answers,
                questions
        );
    }

    private NonWritingEssayGradingSnapshot loadNonWritingEssayReEvaluationSnapshot(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        if ("WRITING".equals(attempt.getSkill())) {
            throw new IllegalStateException("Writing attempt must use snapshot grading path.");
        }

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));
        validateAttemptSection(attempt, section);
        validateKnownSkill(attempt.getSkill());
        loadPublished(attempt.getSetId());

        List<QuestionSnapshot> questions = loadQuestionSnapshots(attempt, section.getId());
        if (!containsEssay(questions)) {
            return null;
        }
        Map<String, String> answers = readAnswers(attempt.getAnswersJson());

        return new NonWritingEssayGradingSnapshot(
                attempt.getId(),
                attempt.getUserId(),
                attempt.getSetId(),
                attempt.getTestId(),
                attempt.getSectionId(),
                attempt.getSkill(),
                attempt.getStatus(),
                attempt.getLockVersion(),
                attempt.getAnswersJson(),
                attempt.getAiFeedbackJson(),
                attempt.getScore(),
                attempt.getTotalPoints(),
                writeJson(answers),
                answers,
                questions
        );
    }

    private SpeakingGradingSnapshot loadSpeakingSubmitSnapshot(
            Long attemptId,
            Long userId,
            Map<String, String> form
    ) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy lượt làm bài"));
        if (!"SPEAKING".equals(attempt.getSkill())) {
            return null;
        }
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new IllegalStateException("Lượt làm bài đã được nộp hoặc chấm điểm.");
        }

        PracticeVersionSnapshot version = versionSnapshot(attempt)
                .orElseThrow(() -> new IllegalStateException(
                        "Speaking attempt is missing an immutable delivery version."));
        if (!attempt.getSetId().equals(version.setVersion().getSetId())
                || !attempt.getTestId().equals(version.testVersion().getTestId())
                || !attempt.getSectionId().equals(version.sectionVersion().getSectionId())
                || !"SPEAKING".equals(version.sectionVersion().getSkill())) {
            throw new IllegalStateException("Speaking attempt delivery version is inconsistent.");
        }
        loadPublished(attempt.getSetId());
        List<QuestionSnapshot> questions = loadQuestionSnapshots(attempt, attempt.getSectionId());
        if (questions.isEmpty()
                || questions.stream().anyMatch(question ->
                        !PracticeQuestion.TYPE_SPEAKING.equals(question.questionType()))) {
            throw new IllegalStateException("Speaking section may only contain canonical SPEAKING questions.");
        }
        validateWritingQuestionPoints(questions);
        if (speakingMediaService == null) {
            throw new IllegalStateException("Speaking media validation is unavailable.");
        }
        List<Long> questionIds = questions.stream().map(QuestionSnapshot::questionId).toList();
        speakingMediaService.requireReadyMediaForOwner(userId, attemptId, questionIds);

        Map<String, String> answers = new LinkedHashMap<>();
        for (Long questionId : questionIds) {
            answers.put(String.valueOf(questionId), "AUDIO_SUBMITTED");
        }

        return new SpeakingGradingSnapshot(
                attempt.getId(),
                attempt.getUserId(),
                attempt.getSetId(),
                attempt.getTestId(),
                attempt.getSectionId(),
                attempt.getSkill(),
                PracticeAttempt.STATUS_IN_PROGRESS,
                attempt.getLockVersion(),
                attempt.getAnswersJson(),
                attempt.getAiFeedbackJson(),
                attempt.getScore(),
                attempt.getTotalPoints(),
                writeJson(answers),
                answers,
                questions);
    }

    private SpeakingGradingSnapshot loadSpeakingReEvaluationSnapshot(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy lượt làm bài"));
        if (!"SPEAKING".equals(attempt.getSkill())) {
            return null;
        }
        if (attempt.getPublishedVersionId() == null
                || attempt.getSetVersionId() == null
                || attempt.getTestVersionId() == null
                || attempt.getSectionVersionId() == null) {
            return null;
        }

        PracticeVersionSnapshot version = versionSnapshot(attempt)
                .orElseThrow(() -> new IllegalStateException(
                        "Speaking attempt is missing an immutable delivery version."));
        if (!attempt.getSetId().equals(version.setVersion().getSetId())
                || !attempt.getTestId().equals(version.testVersion().getTestId())
                || !attempt.getSectionId().equals(version.sectionVersion().getSectionId())
                || !"SPEAKING".equals(version.sectionVersion().getSkill())) {
            throw new IllegalStateException("Speaking attempt delivery version is inconsistent.");
        }
        loadPublished(attempt.getSetId());
        List<QuestionSnapshot> questions = loadQuestionSnapshots(attempt, attempt.getSectionId());
        if (questions.isEmpty()
                || questions.stream().anyMatch(question ->
                        !PracticeQuestion.TYPE_SPEAKING.equals(question.questionType()))) {
            throw new IllegalStateException("Speaking section may only contain canonical SPEAKING questions.");
        }
        validateWritingQuestionPoints(questions);
        Map<String, String> answers = readAnswers(attempt.getAnswersJson());

        return new SpeakingGradingSnapshot(
                attempt.getId(),
                attempt.getUserId(),
                attempt.getSetId(),
                attempt.getTestId(),
                attempt.getSectionId(),
                attempt.getSkill(),
                attempt.getStatus(),
                attempt.getLockVersion(),
                attempt.getAnswersJson(),
                attempt.getAiFeedbackJson(),
                attempt.getScore(),
                attempt.getTotalPoints(),
                writeJson(answers),
                answers,
                questions);
    }

    private List<QuestionSnapshot> loadQuestionSnapshots(PracticeAttempt attempt, Long sectionId) {
        Optional<PracticeVersionSnapshot> snapshot = versionSnapshot(attempt);
        if (snapshot.isPresent()) {
            return snapshot.get().questions().stream()
                    .sorted(Comparator.comparing(PracticeQuestionVersion::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(PracticeQuestionVersion::getQuestionNo, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(PracticeQuestionVersion::getId, Comparator.nullsLast(Long::compareTo)))
                    .map(q -> new QuestionSnapshot(
                            q.getQuestionId(),
                            q.getId(),
                            q.getQuestionNo(),
                            q.getDisplayOrder(),
                            q.getPrompt(),
                            q.getQuestionType(),
                            q.getQuestionContentJson(),
                            q.getOptionsJson(),
                            q.getAnswerKey(),
                            q.getAnswerSpecJson(),
                            q.getExplanation(),
                            q.getPoints(),
                            q.getWritingTaskType()
                    ))
                    .toList();
        }
        Long setId = attempt.getSetId();
        List<PracticeQuestionGroupRow> groupRows = getQuestionGroupsForSection(setId, sectionId);
        List<Long> sectionQuestionIds = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .map(com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionRow::id)
                .toList();

        List<PracticeQuestion> allQuestions = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        return allQuestions.stream()
                .filter(q -> sectionQuestionIds.contains(q.getId()))
                .sorted(QUESTION_ORDER)
                .map(this::toQuestionSnapshot)
                .toList();
    }

    private QuestionSnapshot toQuestionSnapshot(PracticeQuestion question) {
        return new QuestionSnapshot(
                question.getId(),
                null,
                question.getQuestionNo(),
                question.getDisplayOrder(),
                question.getPrompt(),
                question.getQuestionType(),
                question.getQuestionContentJson(),
                question.getOptionsJson(),
                question.getAnswerKey(),
                question.getAnswerSpecJson(),
                question.getExplanation(),
                question.getPoints(),
                question.getWritingTaskType()
        );
    }

    private boolean containsEssay(List<QuestionSnapshot> questions) {
        return questions.stream().anyMatch(q -> PracticeQuestion.TYPE_ESSAY.equals(q.questionType()));
    }

    private boolean containsSpeaking(List<QuestionSnapshot> questions) {
        return questions.stream().anyMatch(q -> PracticeQuestion.TYPE_SPEAKING.equals(q.questionType()));
    }

    private void validateAttemptSection(PracticeAttempt attempt, PracticeSection section) {
        if (!attempt.getSetId().equals(section.getSetId()) ||
            !attempt.getTestId().equals(section.getTestId()) ||
            !attempt.getSkill().equals(section.getSkill())) {
            throw new IllegalArgumentException("Section metadata mismatch with attempt");
        }
    }

    private void validateKnownSkill(String skill) {
        if (skill == null || (!"READING".equals(skill) && !"LISTENING".equals(skill) &&
            !"WRITING".equals(skill) && !"SPEAKING".equals(skill))) {
            throw new IllegalArgumentException("Skill không hợp lệ");
        }
    }

    private void validateWritingQuestionPoints(List<QuestionSnapshot> questions) {
        for (QuestionSnapshot q : questions) {
            if (q.points() == null || q.points().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Invalid configured points for question ID: " + q.questionId());
            }
        }
    }

    private NonWritingEssayGradingResult gradeNonWritingEssaySnapshot(
            NonWritingEssayGradingSnapshot snapshot,
            boolean isReEvaluate
    ) {
        BigDecimal earnedPoints = BigDecimal.ZERO;
        BigDecimal legacyFlatScore = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        String aiFeedback = null;
        Map<String, JsonNode> speakingFeedbackByQuestion = new LinkedHashMap<>();
        Map<String, JsonNode> essayFeedbackByQuestion = new LinkedHashMap<>();

        for (QuestionSnapshot q : snapshot.questions()) {
            total = total.add(q.points());
            String answer = snapshot.answers().getOrDefault(String.valueOf(q.questionId()), "").trim();

            Optional<AssessmentScoreResult> objectiveScore = scoreObjective(q, answer);
            if (objectiveScore.isPresent()) {
                legacyFlatScore = legacyFlatScore.add(objectiveScore.get().earnedPoints());
                earnedPoints = earnedPoints.add(objectiveScore.get().earnedPoints());
            } else if (PracticeQuestion.TYPE_ESSAY.equals(q.questionType())) {
                String perQuestionFeedback = evaluateWriting(
                        snapshot.userId(), q.prompt(), answer, isReEvaluate,
                        q.writingTaskType(), questionImageReference(q));
                essayFeedbackByQuestion.put(String.valueOf(q.questionId()), readFeedbackObject(perQuestionFeedback));
                aiFeedback = perQuestionFeedback;
                legacyFlatScore = extractAiScore(perQuestionFeedback);
                earnedPoints = earnedPoints.add(earnedSpeakingPoints(q.points(), extractAiScore(perQuestionFeedback)));
            } else if (PracticeQuestion.TYPE_SPEAKING.equals(q.questionType())) {
                String perQuestionFeedback = mockSpeakingFeedback(q.prompt(), answer);
                speakingFeedbackByQuestion.put(String.valueOf(q.questionId()), readFeedbackObject(perQuestionFeedback));
                aiFeedback = perQuestionFeedback;
                legacyFlatScore = extractAiScore(perQuestionFeedback);
                earnedPoints = earnedPoints.add(earnedSpeakingPoints(q.points(), extractAiScore(perQuestionFeedback)));
            } else {
                throw new IllegalStateException("Unsupported question type for question ID "
                        + q.questionId() + ": " + q.questionType());
            }
        }
        BigDecimal score = speakingFeedbackByQuestion.isEmpty()
                ? legacyFlatScore
                : toWritingAttemptPercentage(earnedPoints, total);
        if (!speakingFeedbackByQuestion.isEmpty() && !essayFeedbackByQuestion.isEmpty()) {
            aiFeedback = writeJson(speakingMixedEnvelope(speakingFeedbackByQuestion, essayFeedbackByQuestion));
        }

        return new NonWritingEssayGradingResult(score, total, snapshot.answersToPersistJson(), aiFeedback);
    }

    private SpeakingGradingResult gradeSpeakingSnapshot(SpeakingGradingSnapshot snapshot) {
        if (speakingEvaluationApplicationService == null || !speakingEvaluationApplicationService.enabled()) {
            BigDecimal total = snapshot.questions().stream()
                    .map(QuestionSnapshot::points)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new SpeakingGradingResult(
                    null, total, snapshot.answersToPersistJson(), null);
        }

        BigDecimal earnedPoints = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        boolean allSpeakingScoreBearing = true;
        Map<Long, SpeakingEvaluationResult> feedbackByQuestion = new LinkedHashMap<>();
        Map<Long, SpeakingEvaluationResult> storedByQuestion = storedSpeakingResults(snapshot.expectedAiFeedbackJson());

        for (QuestionSnapshot q : snapshot.questions()) {
            total = total.add(q.points());
            if (PracticeQuestion.TYPE_SPEAKING.equals(q.questionType())) {
                SpeakingEvaluationApplicationService.Evaluation evaluation =
                        speakingEvaluationApplicationService.evaluateQuestion(
                                new SpeakingEvaluationApplicationService.EvaluationInput(
                                        snapshot.userId(),
                                        snapshot.attemptId(),
                                        q.questionId(),
                                        q.prompt(),
                                        null,
                                        q.answerKey(),
                                        questionImageReference(q),
                                        "",
                                        storedByQuestion.get(q.questionId())));
                SpeakingEvaluationResult result = evaluation.result();
                if (result == null) {
                    allSpeakingScoreBearing = false;
                    continue;
                }
                feedbackByQuestion.put(q.questionId(), result);
                if (!result.scoreAvailable() || result.overallScore() == null) {
                    allSpeakingScoreBearing = false;
                    continue;
                }
                BigDecimal earnedQuestionPoints = clamp(result.overallScore(), BigDecimal.ZERO, BigDecimal.valueOf(100))
                        .multiply(q.points())
                        .divide(BigDecimal.valueOf(100), java.math.MathContext.DECIMAL128);
                earnedPoints = earnedPoints.add(earnedQuestionPoints);
            } else {
                throw new IllegalStateException("Unsupported SPEAKING question type for question ID "
                        + q.questionId() + ": " + q.questionType());
            }
        }
        String feedbackJson = feedbackByQuestion.isEmpty() ? null : speakingAiFeedbackEnvelope(feedbackByQuestion);
        BigDecimal score = allSpeakingScoreBearing && !feedbackByQuestion.isEmpty()
                ? toWritingAttemptPercentage(earnedPoints, total)
                : null;
        return new SpeakingGradingResult(score, total, snapshot.answersToPersistJson(), feedbackJson);
    }

    private Map<Long, SpeakingEvaluationResult> storedSpeakingResults(String aiFeedbackJson) {
        JsonNode root = null;
        if (aiFeedbackJson != null && !aiFeedbackJson.isBlank()) {
            try {
                root = objectMapper.readTree(aiFeedbackJson);
            } catch (Exception ex) {
                return Map.of();
            }
        }
        if (!isSpeakingAiEnvelope(root)) {
            return Map.of();
        }
        JsonNode byQuestion = root.path(SPEAKING_MIXED_SPEAKING_FIELD);
        if (!byQuestion.isObject()) {
            return Map.of();
        }
        Map<Long, SpeakingEvaluationResult> results = new LinkedHashMap<>();
        byQuestion.fields().forEachRemaining(entry -> {
            try {
                Long questionId = Long.valueOf(entry.getKey());
                if (entry.getValue() != null && entry.getValue().isObject()) {
                    results.put(questionId, speakingFeedbackReader.read(entry.getValue()));
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed stored question keys during reuse; fresh evaluation can replace them.
            }
        });
        return results;
    }

    private Map<String, Object> speakingMixedEnvelope(
            Map<String, JsonNode> speakingFeedbackByQuestion,
            Map<String, JsonNode> essayFeedbackByQuestion
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(SPEAKING_MIXED_CONTRACT_FIELD, SPEAKING_MIXED_CONTRACT);
        envelope.put(SPEAKING_MIXED_SPEAKING_FIELD, speakingFeedbackByQuestion);
        envelope.put(SPEAKING_MIXED_ESSAY_FIELD, essayFeedbackByQuestion);
        return envelope;
    }

    private WritingGradingResult gradeWritingSnapshot(WritingGradingSnapshot snapshot, boolean isReEvaluate) {
        BigDecimal attemptTotalPoints = BigDecimal.ZERO;
        BigDecimal attemptEarnedPoints = BigDecimal.ZERO;
        com.fasterxml.jackson.databind.node.ObjectNode feedbackMap = objectMapper.createObjectNode();
        boolean allEvaluationsScoreBearing = true;

        for (QuestionSnapshot q : snapshot.questions()) {
            BigDecimal configuredPoints = q.points();
            attemptTotalPoints = attemptTotalPoints.add(configuredPoints);
            String answer = snapshot.answers().getOrDefault(String.valueOf(q.questionId()), "").trim();

            Optional<AssessmentScoreResult> objectiveScore = scoreObjective(q, answer);
            if (objectiveScore.isPresent()) {
                attemptEarnedPoints = attemptEarnedPoints.add(objectiveScore.get().earnedPoints());
            } else if (PracticeQuestion.TYPE_ESSAY.equals(q.questionType())) {
                String singleFeedback = evaluateWriting(snapshot.userId(), q.prompt(), answer,
                        isReEvaluate, q.writingTaskType(), questionImageReference(q));
                com.fasterxml.jackson.databind.node.ObjectNode node = readWritingFeedbackObject(q.questionId(), singleFeedback);

                WritingEvaluationResult evaluation = readGeneratedWritingScore(node, q.questionId());
                if (!evaluation.scoreAvailableFlag()) {
                    allEvaluationsScoreBearing = false;
                    feedbackMap.set(String.valueOf(q.questionId()), node);
                    continue;
                }
                BigDecimal rawScore = evaluation.rawScore();
                BigDecimal rawScoreMax = evaluation.rawScoreMax();
                rawScore = clamp(rawScore, BigDecimal.ZERO, rawScoreMax);

                BigDecimal earnedQuestionPoints = rawScore.multiply(configuredPoints)
                        .divide(rawScoreMax, java.math.MathContext.DECIMAL128);
                attemptEarnedPoints = attemptEarnedPoints.add(earnedQuestionPoints);
                feedbackMap.set(String.valueOf(q.questionId()), node);
            } else {
                throw new IllegalStateException("Unsupported WRITING question type for question ID " + q.questionId()
                        + ": " + q.questionType());
            }
        }

        BigDecimal attemptScore = allEvaluationsScoreBearing
                ? toWritingAttemptPercentage(attemptEarnedPoints, attemptTotalPoints)
                : null;

        String feedbackJson;
        try {
            feedbackJson = objectMapper.writeValueAsString(feedbackMap);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize writing feedback map", e);
        }

        return new WritingGradingResult(attemptScore, attemptTotalPoints, snapshot.answersToPersistJson(), feedbackJson);
    }

    private WritingGradingResult gradeWritingQuestionSnapshot(WritingQuestionReEvaluationSnapshot snapshot) {
        com.fasterxml.jackson.databind.node.ObjectNode feedbackMap = buildValidatedFeedbackMapBeforeTargetEvaluation(snapshot);

        String targetAnswer = snapshot.answers().getOrDefault(String.valueOf(snapshot.targetQuestion().questionId()), "").trim();
        String targetFeedback = evaluateWriting(
                snapshot.userId(),
                snapshot.targetQuestion().prompt(),
                targetAnswer,
                true,
                snapshot.targetQuestion().writingTaskType(),
                questionImageReference(snapshot.targetQuestion()));
        com.fasterxml.jackson.databind.node.ObjectNode targetNode =
                readWritingFeedbackObject(snapshot.targetQuestion().questionId(), targetFeedback);
        WritingEvaluationResult targetScore = readStoredWritingScore(targetNode, snapshot.targetQuestion().questionId());
        if (!targetScore.scoreAvailableFlag()) {
            return new WritingGradingResult(null, null, snapshot.expectedAnswersJson(), snapshot.expectedAiFeedbackJson());
        }
        feedbackMap.set(String.valueOf(snapshot.targetQuestion().questionId()), targetNode);

        WritingScoreAggregate aggregate = aggregateWritingScore(snapshot.questions(), snapshot.answers(), feedbackMap);
        String feedbackJson;
        try {
            feedbackJson = objectMapper.writeValueAsString(feedbackMap);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize writing feedback map", e);
        }
        return new WritingGradingResult(aggregate.score(), aggregate.totalPoints(), snapshot.expectedAnswersJson(), feedbackJson);
    }

    private String evaluateWriting(Long userId,
                                   String prompt,
                                   String answer,
                                   boolean reEvaluate,
                                   WritingTaskType taskType,
                                   String imageReference) {
        if (imageReference == null || imageReference.isBlank()) {
            return evaluationClient.evaluate(userId, prompt, answer, reEvaluate, taskType);
        }
        return evaluationClient.evaluate(
                userId, prompt, answer, reEvaluate, taskType, imageReference);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode buildValidatedFeedbackMapBeforeTargetEvaluation(
            WritingQuestionReEvaluationSnapshot snapshot
    ) {
        List<QuestionSnapshot> essayQuestions = snapshot.questions().stream()
                .filter(q -> PracticeQuestion.TYPE_ESSAY.equals(q.questionType()))
                .toList();
        JsonNode root = readExistingWritingFeedbackRoot(snapshot.expectedAiFeedbackJson());
        com.fasterxml.jackson.databind.node.ObjectNode feedbackMap = objectMapper.createObjectNode();

        if (writingFeedbackReader.isLegacyFlatFeedback(root)) {
            if (essayQuestions.size() != 1 || !essayQuestions.get(0).questionId().equals(snapshot.targetQuestion().questionId())) {
                throw unsupportedPerQuestionFeedback();
            }
            return feedbackMap;
        }

        if (!root.isObject()) {
            throw unsupportedPerQuestionFeedback();
        }

        com.fasterxml.jackson.databind.node.ObjectNode rootObject = (com.fasterxml.jackson.databind.node.ObjectNode) root;
        feedbackMap = rootObject.deepCopy();
        for (QuestionSnapshot q : essayQuestions) {
            if (q.questionId().equals(snapshot.targetQuestion().questionId())) {
                continue;
            }
            JsonNode entry = feedbackMap.get(String.valueOf(q.questionId()));
            if (entry == null || entry.isNull() || !entry.isObject()) {
                throw unsupportedPerQuestionFeedback();
            }
            readStoredWritingScore(entry, q.questionId());
        }
        return feedbackMap;
    }

    private JsonNode readExistingWritingFeedbackRoot(String feedbackJson) {
        if (feedbackJson == null || feedbackJson.isBlank()) {
            throw unsupportedPerQuestionFeedback();
        }
        try {
            JsonNode root = objectMapper.readTree(feedbackJson);
            if (root == null || !root.isObject()) {
                throw unsupportedPerQuestionFeedback();
            }
            return root;
        } catch (PracticeAttemptConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unsupportedPerQuestionFeedback();
        }
    }

    private WritingScoreAggregate aggregateWritingScore(
            List<QuestionSnapshot> questions,
            Map<String, String> answers,
            com.fasterxml.jackson.databind.node.ObjectNode feedbackMap
    ) {
        BigDecimal attemptTotalPoints = BigDecimal.ZERO;
        BigDecimal attemptEarnedPoints = BigDecimal.ZERO;

        for (QuestionSnapshot q : questions) {
            BigDecimal configuredPoints = q.points();
            attemptTotalPoints = attemptTotalPoints.add(configuredPoints);
            String answer = answers.getOrDefault(String.valueOf(q.questionId()), "").trim();

            Optional<AssessmentScoreResult> objectiveScore = scoreObjective(q, answer);
            if (objectiveScore.isPresent()) {
                attemptEarnedPoints = attemptEarnedPoints.add(objectiveScore.get().earnedPoints());
            } else if (PracticeQuestion.TYPE_ESSAY.equals(q.questionType())) {
                JsonNode node = feedbackMap.get(String.valueOf(q.questionId()));
                if (node == null || node.isNull() || !node.isObject()) {
                    throw unsupportedPerQuestionFeedback();
                }
                WritingEvaluationResult storedScore = readStoredWritingScore(node, q.questionId());
                if (!storedScore.scoreAvailableFlag()) {
                    return new WritingScoreAggregate(null, attemptTotalPoints);
                }
                BigDecimal rawScore = clamp(storedScore.rawScore(), BigDecimal.ZERO, storedScore.rawScoreMax());
                BigDecimal earnedQuestionPoints = rawScore.multiply(configuredPoints)
                        .divide(storedScore.rawScoreMax(), java.math.MathContext.DECIMAL128);
                attemptEarnedPoints = attemptEarnedPoints.add(earnedQuestionPoints);
            } else {
                throw new IllegalStateException("Unsupported WRITING question type for question ID " + q.questionId()
                        + ": " + q.questionType());
            }
        }

        return new WritingScoreAggregate(toWritingAttemptPercentage(attemptEarnedPoints, attemptTotalPoints), attemptTotalPoints);
    }

    private BigDecimal toWritingAttemptPercentage(BigDecimal earnedPoints, BigDecimal totalPoints) {
        BigDecimal attemptScore = BigDecimal.ZERO;
        if (totalPoints.compareTo(BigDecimal.ZERO) > 0) {
            attemptScore = earnedPoints.multiply(BigDecimal.valueOf(100))
                    .divide(totalPoints, 2, RoundingMode.HALF_UP);
        }
        attemptScore = clamp(attemptScore, BigDecimal.ZERO, BigDecimal.valueOf(100));
        if (attemptScore.compareTo(BigDecimal.ZERO) == 0) {
            attemptScore = BigDecimal.ZERO;
        }
        return attemptScore;
    }

    private WritingEvaluationResult readStoredWritingScore(JsonNode node, Long questionId) {
        WritingFeedbackCompatibilityReader.EntryResult parsed = writingFeedbackReader.parseStoredEntry(node);
        if (parsed.status() != WritingFeedbackCompatibilityReader.Status.VALID_CURRENT) {
            throw unsupportedPerQuestionFeedback();
        }
        return parsed.value();
    }

    private WritingEvaluationResult readGeneratedWritingScore(JsonNode node, Long questionId) {
        WritingFeedbackCompatibilityReader.EntryResult parsed = writingFeedbackReader.parseGeneratedEntry(node);
        if (parsed.status() != WritingFeedbackCompatibilityReader.Status.VALID_CURRENT) {
            throw new IllegalStateException("AI feedback missing numeric score fields for question ID: " + questionId);
        }
        return parsed.value();
    }

    private Long persistNonWritingEssaySubmitResult(
            NonWritingEssayGradingSnapshot snapshot,
            NonWritingEssayGradingResult result
    ) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(snapshot.attemptId(), snapshot.userId())
                .orElseThrow(() -> new EntityNotFoundException("Bài làm đã thay đổi trong lúc chấm. Vui lòng tải lại và thử lại."));
        verifyNonWritingSnapshotIdentity(attempt, snapshot);
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw conflict();
        }
        if (!Objects.equals(normalizeJsonForCompare(snapshot.expectedExistingAnswersJson()), normalizeJsonForCompare(attempt.getAnswersJson()))) {
            throw conflict();
        }
        verifyNonWritingSnapshotVersion(attempt, snapshot);
        if (result.aiFeedbackJson() == null) {
            attempt.markSubmitted(result.score(), result.totalPoints(), result.answersJson());
        } else {
            attempt.markGraded(result.score(), result.totalPoints(), result.answersJson(), result.aiFeedbackJson());
        }
        flushAttempt(attempt);
        log.info("[PracticeService] Submitted PracticeAttempt id={} score={} / {}", attempt.getId(), result.score(), result.totalPoints());
        return attempt.getId();
    }

    private Long persistNonWritingEssayReEvaluationResult(
            NonWritingEssayGradingSnapshot snapshot,
            NonWritingEssayGradingResult result
    ) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(snapshot.attemptId(), snapshot.userId())
                .orElseThrow(() -> new EntityNotFoundException("Bài làm đã thay đổi trong lúc chấm. Vui lòng tải lại và thử lại."));
        verifyNonWritingSnapshotIdentity(attempt, snapshot);
        if (!Objects.equals(snapshot.expectedStatus(), attempt.getStatus())) {
            throw conflict();
        }
        if (!Objects.equals(normalizeJsonForCompare(snapshot.expectedExistingAnswersJson()), normalizeJsonForCompare(attempt.getAnswersJson()))) {
            throw conflict();
        }
        if (!Objects.equals(snapshot.expectedAiFeedbackJson(), attempt.getAiFeedbackJson())) {
            throw conflict();
        }
        if (!Objects.equals(snapshot.expectedScore(), attempt.getScore())
                || !Objects.equals(snapshot.expectedTotalPoints(), attempt.getTotalPoints())) {
            throw conflict();
        }
        verifyNonWritingSnapshotVersion(attempt, snapshot);
        if (result.aiFeedbackJson() == null) {
            attempt.markSubmitted(result.score(), result.totalPoints(), result.answersJson());
        } else {
            attempt.markGraded(result.score(), result.totalPoints(), result.answersJson(), result.aiFeedbackJson());
        }
        flushAttempt(attempt);
        log.info("[PracticeService] Re-evaluated PracticeAttempt id={} score={} / {}", attempt.getId(), result.score(), result.totalPoints());
        return attempt.getId();
    }

    private Long persistSpeakingGradingResult(
            SpeakingGradingSnapshot snapshot,
            SpeakingGradingResult result,
            boolean submit
    ) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(snapshot.attemptId(), snapshot.userId())
                .orElseThrow(() -> new EntityNotFoundException("Bai lam da thay doi trong luc cham. Vui long tai lai va thu lai."));
        verifySpeakingSnapshotIdentity(attempt, snapshot);
        if (submit && !PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw conflict();
        }
        if (!submit && !Objects.equals(snapshot.expectedStatus(), attempt.getStatus())) {
            throw conflict();
        }
        if (!Objects.equals(normalizeJsonForCompare(snapshot.expectedExistingAnswersJson()), normalizeJsonForCompare(attempt.getAnswersJson()))) {
            throw conflict();
        }
        if (!submit && !Objects.equals(snapshot.expectedAiFeedbackJson(), attempt.getAiFeedbackJson())) {
            throw conflict();
        }
        verifySpeakingSnapshotVersion(attempt, snapshot);
        if (result.aiFeedbackJson() == null) {
            attempt.markSubmitted(result.score(), result.totalPoints(), result.answersJson());
        } else {
            attempt.markGraded(result.score(), result.totalPoints(), result.answersJson(), result.aiFeedbackJson());
        }
        flushAttempt(attempt);
        log.info("[PracticeService] {} PracticeAttempt id={} score={} / {}",
                submit ? "Submitted" : "Re-evaluated", attempt.getId(), result.score(), result.totalPoints());
        return attempt.getId();
    }

    private void verifySpeakingSnapshotIdentity(PracticeAttempt attempt, SpeakingGradingSnapshot snapshot) {
        if (!Objects.equals(attempt.getSetId(), snapshot.setId())
                || !Objects.equals(attempt.getTestId(), snapshot.testId())
                || !Objects.equals(attempt.getSectionId(), snapshot.sectionId())
                || !Objects.equals(attempt.getSkill(), snapshot.skill())) {
            throw conflict();
        }
    }

    private void verifySpeakingSnapshotVersion(PracticeAttempt attempt, SpeakingGradingSnapshot snapshot) {
        if (snapshot.lockVersion() != null && !Objects.equals(attempt.getLockVersion(), snapshot.lockVersion())) {
            throw conflict();
        }
    }

    private Long persistWritingSubmitResult(WritingGradingSnapshot snapshot, WritingGradingResult result) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(snapshot.attemptId(), snapshot.userId())
                .orElseThrow(() -> new EntityNotFoundException("Bài làm đã thay đổi trong lúc chấm. Vui lòng tải lại và thử lại."));
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw conflict();
        }
        if (!Objects.equals(normalizeJsonForCompare(snapshot.expectedExistingAnswersJson()), normalizeJsonForCompare(attempt.getAnswersJson()))) {
            throw conflict();
        }
        verifySnapshotVersion(attempt, snapshot);
        if (result.score() == null) {
            attempt.markSubmitted(null, result.totalPoints(), result.answersJson());
            attempt.setAiFeedbackJson(result.feedbackJson());
        } else {
            attempt.markGraded(result.score(), result.totalPoints(), result.answersJson(), result.feedbackJson());
        }
        flushAttempt(attempt);
        log.info("[PracticeService] Submitted WRITING PracticeAttempt id={} score={} / {}", attempt.getId(), attempt.getScore(), attempt.getTotalPoints());
        return attempt.getId();
    }

    private Long persistWritingReEvaluationResult(WritingGradingSnapshot snapshot, WritingGradingResult result) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(snapshot.attemptId(), snapshot.userId())
                .orElseThrow(() -> new EntityNotFoundException("Bài làm đã thay đổi trong lúc chấm. Vui lòng tải lại và thử lại."));
        if (!Objects.equals(snapshot.expectedStatus(), attempt.getStatus())) {
            throw conflict();
        }
        if (!Objects.equals(normalizeJsonForCompare(snapshot.expectedExistingAnswersJson()), normalizeJsonForCompare(attempt.getAnswersJson()))) {
            throw conflict();
        }
        verifySnapshotVersion(attempt, snapshot);
        if (result.score() == null) {
            return attempt.getId();
        }
        attempt.markGraded(result.score(), result.totalPoints(), result.answersJson(), result.feedbackJson());
        flushAttempt(attempt);
        log.info("[PracticeService] Re-evaluated WRITING PracticeAttempt id={} score={} / {}", attempt.getId(), attempt.getScore(), attempt.getTotalPoints());
        return attempt.getId();
    }

    private Long persistWritingQuestionReEvaluationResult(
            WritingQuestionReEvaluationSnapshot snapshot,
            WritingGradingResult result
    ) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(snapshot.attemptId(), snapshot.userId())
                .orElseThrow(() -> new EntityNotFoundException("Bai lam da thay doi trong luc cham. Vui long tai lai va thu lai."));
        if (!Objects.equals(snapshot.expectedStatus(), attempt.getStatus())) {
            throw conflict();
        }
        if (!Objects.equals(snapshot.expectedAnswersJson(), attempt.getAnswersJson())) {
            throw conflict();
        }
        if (!Objects.equals(snapshot.expectedAiFeedbackJson(), attempt.getAiFeedbackJson())) {
            throw conflict();
        }
        verifyQuestionSnapshotVersion(attempt, snapshot);
        if (result.score() == null) {
            return attempt.getId();
        }
        attempt.markGraded(result.score(), result.totalPoints(), result.answersJson(), result.feedbackJson());
        flushAttempt(attempt);
        log.info("[PracticeService] Re-evaluated WRITING question PracticeAttempt id={} questionId={} score={} / {}",
                attempt.getId(), snapshot.targetQuestion().questionId(), attempt.getScore(), attempt.getTotalPoints());
        return attempt.getId();
    }

    private void verifySnapshotVersion(PracticeAttempt attempt, WritingGradingSnapshot snapshot) {
        if (!Objects.equals(attempt.getLockVersion(), snapshot.lockVersion())) {
            throw conflict();
        }
    }

    private void verifyNonWritingSnapshotVersion(PracticeAttempt attempt, NonWritingEssayGradingSnapshot snapshot) {
        if (!Objects.equals(attempt.getLockVersion(), snapshot.lockVersion())) {
            throw conflict();
        }
    }

    private void verifyNonWritingSnapshotIdentity(PracticeAttempt attempt, NonWritingEssayGradingSnapshot snapshot) {
        if (!Objects.equals(attempt.getSetId(), snapshot.setId())
                || !Objects.equals(attempt.getTestId(), snapshot.testId())
                || !Objects.equals(attempt.getSectionId(), snapshot.sectionId())
                || !Objects.equals(attempt.getSkill(), snapshot.skill())) {
            throw conflict();
        }
    }

    private void verifyQuestionSnapshotVersion(PracticeAttempt attempt, WritingQuestionReEvaluationSnapshot snapshot) {
        if (!Objects.equals(attempt.getLockVersion(), snapshot.lockVersion())) {
            throw conflict();
        }
    }

    private void flushAttempt(PracticeAttempt attempt) {
        try {
            attemptRepository.saveAndFlush(attempt);
        } catch (OptimisticLockingFailureException ex) {
            throw conflict();
        }
    }

    private PracticeAttemptConflictException conflict() {
        return new PracticeAttemptConflictException("Bài làm đã thay đổi trong lúc chấm. Vui lòng tải lại và thử lại.");
    }

    private PracticeAttemptConflictException unsupportedPerQuestionFeedback() {
        return new PracticeAttemptConflictException("Du lieu phan hoi cu khong ho tro cham lai tung cau. Vui long cham lai toan bai.");
    }

    private String normalizeJsonForCompare(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private record WritingGradingSnapshot(
            Long attemptId,
            Long userId,
            Long sectionId,
            String skill,
            String expectedStatus,
            Long lockVersion,
            String expectedExistingAnswersJson,
            String answersToPersistJson,
            Map<String, String> answers,
            List<QuestionSnapshot> questions
    ) {
    }

    private record WritingQuestionReEvaluationSnapshot(
            Long attemptId,
            Long userId,
            Long sectionId,
            String skill,
            String expectedStatus,
            Long lockVersion,
            String expectedAnswersJson,
            String expectedAiFeedbackJson,
            Map<String, String> answers,
            List<QuestionSnapshot> questions,
            QuestionSnapshot targetQuestion
    ) {
    }

    private record NonWritingEssayGradingSnapshot(
            Long attemptId,
            Long userId,
            Long setId,
            Long testId,
            Long sectionId,
            String skill,
            String expectedStatus,
            Long lockVersion,
            String expectedExistingAnswersJson,
            String expectedAiFeedbackJson,
            BigDecimal expectedScore,
            BigDecimal expectedTotalPoints,
            String answersToPersistJson,
            Map<String, String> answers,
            List<QuestionSnapshot> questions
    ) {
    }

    private record SpeakingGradingSnapshot(
            Long attemptId,
            Long userId,
            Long setId,
            Long testId,
            Long sectionId,
            String skill,
            String expectedStatus,
            Long lockVersion,
            String expectedExistingAnswersJson,
            String expectedAiFeedbackJson,
            BigDecimal expectedScore,
            BigDecimal expectedTotalPoints,
            String answersToPersistJson,
            Map<String, String> answers,
            List<QuestionSnapshot> questions
    ) {
    }

    private record QuestionSnapshot(
            Long questionId,
            Long questionVersionId,
            Integer questionNo,
            Integer displayOrder,
            String prompt,
            String questionType,
            String questionContentJson,
            String optionsJson,
            String answerKey,
            String answerSpecJson,
            String teacherExplanation,
            BigDecimal points,
            WritingTaskType writingTaskType
    ) {
    }

    private record WritingScoreAggregate(
            BigDecimal score,
            BigDecimal totalPoints
    ) {
    }

    private record WritingGradingResult(
            BigDecimal score,
            BigDecimal totalPoints,
            String answersJson,
            String feedbackJson
    ) {
    }

    private record NonWritingEssayGradingResult(
            BigDecimal score,
            BigDecimal totalPoints,
            String answersJson,
            String aiFeedbackJson
    ) {
    }

    private record SpeakingGradingResult(
            BigDecimal score,
            BigDecimal totalPoints,
            String answersJson,
            String aiFeedbackJson
    ) {
    }

    private com.fasterxml.jackson.databind.node.ObjectNode readWritingFeedbackObject(Long questionId, String feedbackJson) {
        try {
            JsonNode node = objectMapper.readTree(feedbackJson);
            if (node == null || !node.isObject()) {
                throw new IllegalStateException("AI feedback root must be an object for question ID: " + questionId);
            }
            return (com.fasterxml.jackson.databind.node.ObjectNode) node;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid AI feedback JSON for question ID: " + questionId, e);
        }
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    private static String exceptionCategory(Exception ex) {
        return ex == null ? "unknown" : ex.getClass().getSimpleName();
    }
}
