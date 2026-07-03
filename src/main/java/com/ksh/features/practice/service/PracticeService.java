package com.ksh.features.practice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeSubmission;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.dto.PracticeDtos;
import com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionGroupRow;
import com.ksh.features.practice.dto.PracticeDtos.ExampleBox;
import com.ksh.features.practice.ai.AnswerExplanationClient;
import com.ksh.features.practice.ai.WritingEvaluationClient;
import com.ksh.features.practice.ai.WritingScoreMatrix;
import com.ksh.features.practice.dto.PracticeDtos.LearningProfileView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAnswerExplanationRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAnswerReviewRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultSummary;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetView;
import com.ksh.features.practice.dto.PracticeDtos.ReadingListeningResultView;
import com.ksh.features.practice.dto.PracticeDtos.PerformanceByTypeRow;
import com.ksh.features.practice.dto.PracticeDtos.ReviewGroupRow;
import com.ksh.features.practice.dto.PracticeDtos.ReviewQuestionRow;
import com.ksh.features.practice.dto.PracticeDtos.QuestionExplanationRow;
import com.ksh.features.practice.dto.PracticeDtos.EliminatedOptionExplanation;
import com.ksh.features.practice.dto.PracticeDtos.SectionResultRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSubmissionRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeTest;
import com.ksh.common.storage.AudioStorageService;
import com.ksh.entities.PracticeSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final PracticeSetRepository setRepository;
    private final PracticeQuestionRepository questionRepository;
    private final PracticeSubmissionRepository submissionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final PracticeTestRepository testRepository;
    private final WritingEvaluationClient evaluationClient;
    private final AnswerExplanationClient answerExplanationClient;
    private final ReadingListeningExplanationService readingListeningExplanationService;
    private final AudioStorageService audioStorageService;
    private final ObjectMapper objectMapper;

    public PracticeService(PracticeSetRepository setRepository,
                           PracticeQuestionRepository questionRepository,
                           PracticeSubmissionRepository submissionRepository,
                           PracticeQuestionGroupRepository groupRepository,
                           PracticeSectionRepository sectionRepository,
                           PracticeAttemptRepository attemptRepository,
                           PracticeTestRepository testRepository,
                           WritingEvaluationClient evaluationClient,
                           AnswerExplanationClient answerExplanationClient,
                           ReadingListeningExplanationService readingListeningExplanationService,
                           AudioStorageService audioStorageService,
                           ObjectMapper objectMapper) {
        this.setRepository = setRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.groupRepository = groupRepository;
        this.sectionRepository = sectionRepository;
        this.attemptRepository = attemptRepository;
        this.testRepository = testRepository;
        this.evaluationClient = evaluationClient;
        this.answerExplanationClient = answerExplanationClient;
        this.readingListeningExplanationService = readingListeningExplanationService;
        this.audioStorageService = audioStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PracticeSetRow> listPublished() {
        return setRepository.findByStatusOrderByCreatedAtDesc(PracticeSet.STATUS_PUBLISHED)
                .stream()
                .map(PracticeService::toSetRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public PracticeSetView getPractice(Long setId) {
        PracticeSet set = loadPublished(setId);
        List<PracticeQuestionGroup> dbGroups = groupRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        List<PracticeQuestion> dbQuestions = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId);

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

        return new PracticeSetView(toSetRow(set), groups);
    }

    @Transactional
    public Long reEvaluate(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));

        if (!attempt.getSetId().equals(section.getSetId()) ||
            !attempt.getTestId().equals(section.getTestId()) ||
            !attempt.getSkill().equals(section.getSkill())) {
            throw new IllegalArgumentException("Section metadata mismatch with attempt");
        }

        PracticeSet set = loadPublished(attempt.getSetId());

        List<PracticeQuestionGroupRow> groupRows = getQuestionGroupsForSection(attempt.getSetId(), section.getId());
        List<Long> sectionQuestionIds = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .map(com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionRow::id)
                .toList();

        List<PracticeQuestion> allQuestions = questionRepository.findBySetIdOrderByDisplayOrderAsc(attempt.getSetId());
        List<PracticeQuestion> sectionQuestions = allQuestions.stream()
                .filter(q -> sectionQuestionIds.contains(q.getId()))
                .toList();

        Map<String, String> submittedAnswers = readAnswers(attempt.getAnswersJson());

        BigDecimal score = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        String aiFeedback = null;

        for (PracticeQuestion q : sectionQuestions) {
            total = total.add(q.getPoints());
            String answer = submittedAnswers.getOrDefault(String.valueOf(q.getId()), "").trim();

            if (PracticeQuestion.TYPE_MCQ.equals(q.getQuestionType())) {
                if (answersMatch(answer, q.getAnswerKey())) {
                    score = score.add(q.getPoints());
                }
            } else if (PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType())) {
                aiFeedback = evaluationClient.evaluate(q.getPrompt(), answer, true);
                score = extractAiScore(aiFeedback);
            } else if (PracticeQuestion.TYPE_SPEAKING.equals(q.getQuestionType())) {
                aiFeedback = mockSpeakingFeedback(q.getPrompt(), answer);
                score = extractAiScore(aiFeedback);
            } else if (isAutoScoredByKey(q.getQuestionType())) {
                if (answersMatch(answer, q.getAnswerKey())) {
                    score = score.add(q.getPoints());
                }
            }
        }

        if (usesAnswerExplanations(set) && !hasWritingOrSpeaking(sectionQuestions)) {
            Map<String, Object> explanationAnswers = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : submittedAnswers.entrySet()) {
                explanationAnswers.put(entry.getKey(), entry.getValue());
            }
            aiFeedback = answerExplanationClient.explain(set, sectionQuestions, explanationAnswers);
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
        PracticeSet set = setRepository.findById(attempt.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));

        List<PracticeQuestionGroupRow> groupRows = getQuestionGroupsForSection(attempt.getSetId(), section.getId());
        List<Long> sectionQuestionIds = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .map(com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionRow::id)
                .toList();

        List<PracticeQuestion> allQuestions = questionRepository.findBySetIdOrderByDisplayOrderAsc(attempt.getSetId());
        List<PracticeQuestion> sectionQuestions = allQuestions.stream()
                .filter(q -> sectionQuestionIds.contains(q.getId()))
                .toList();

        List<PracticeQuestionRow> questions = sectionQuestions.stream()
                .map(this::toQuestionRow)
                .toList();

        List<PracticeAnswerExplanationRow> answerExplanations = usesAnswerExplanations(set)
                ? answerExplanationRows(attempt.getAiFeedbackJson(), questions, attempt.getAnswersJson())
                : List.of();
        List<PracticeAnswerReviewRow> answerReviews = answerReviewRows(questions, attempt.getAnswersJson());

        return new PracticeResultView(
                attempt.getId(),
                toSetRow(set),
                attempt.getScore(),
                attempt.getTotalPoints(),
                scoreLabel(attempt.getScore(), attempt.getTotalPoints()),
                attempt.getAnswersJson(),
                attempt.getAiFeedbackJson(),
                questions,
                answerReviews,
                answerExplanations
        );
    }



    private double getNormalizedScore(PracticeSubmission s, String skill) {
        if (s.getScore() == null) return 0.0;
        if ("WRITING".equals(skill) || "SPEAKING".equals(skill)) {
            return s.getScore().doubleValue();
        } else {
            if (s.getTotalPoints() != null && s.getTotalPoints().compareTo(BigDecimal.ZERO) > 0) {
                return s.getScore().multiply(BigDecimal.valueOf(100))
                        .divide(s.getTotalPoints(), 2, RoundingMode.HALF_UP).doubleValue();
            }
        }
        return 0.0;
    }

    private double getNormalizedAttemptScore(PracticeAttempt attempt) {
        if (attempt.getScore() == null) return 0.0;
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
        Map<Long, PracticeSection> result = new LinkedHashMap<>();
        for (PracticeSection section : sectionRepository.findAllById(ids)) {
            result.put(section.getId(), section);
        }
        return result;
    }

    private com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview buildAttemptProgressOverview(
            Long userId, String displayName, String avatarUrl) {
        List<PracticeAttempt> allAttempts = attemptRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<PracticeAttempt> recentAttempts = attemptRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId);
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
                    displayName, avatarUrl, "TOPIK II", 0, 0, 0, 0.0,
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

        Map<Long, PracticeSet> setsById = loadSetsById(recentAttempts);
        Map<Long, PracticeTest> testsById = loadTestsById(recentAttempts);
        Map<Long, PracticeSection> sectionsById = loadSectionsById(recentAttempts);
        List<PracticeAttempt> recent8 = recentAttempts.subList(0, Math.min(8, recentAttempts.size()));
        List<PracticeResultSummary> recentHistory = new ArrayList<>();
        for (PracticeAttempt attempt : recent8) {
            recentHistory.add(toAttemptSummary(attempt, setsById, testsById, sectionsById));
        }

        String currentLevel = "TOPIK II Cáº¥p 3";
        if (recentAverageScore >= 80.0) {
            currentLevel = "TOPIK II Cáº¥p 6";
        } else if (recentAverageScore >= 70.0) {
            currentLevel = "TOPIK II Cáº¥p 5";
        } else if (recentAverageScore >= 60.0) {
            currentLevel = "TOPIK II Cáº¥p 4";
        } else if (recentAverageScore < 40.0) {
            currentLevel = "TOPIK I Cáº¥p 1";
        } else if (recentAverageScore < 50.0) {
            currentLevel = "TOPIK I Cáº¥p 2";
        }

        return new com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview(
                displayName, avatarUrl, currentLevel, totalAttempts, totalCompleted,
                totalPracticeMinutes, recentAverageScore, skillMetrics, heatmap, recentHistory);
    }

    private com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics buildAttemptPracticeAnalytics(Long userId) {
        List<PracticeAttempt> allAttempts = attemptRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<PracticeAttempt> recentAttempts = attemptRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId);

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
        Map<Long, PracticeSet> setsById = loadSetsById(recentAttempts);
        Map<Long, PracticeTest> testsById = loadTestsById(recentAttempts);
        Map<Long, PracticeSection> sectionsById = loadSectionsById(recentAttempts);

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
                double qScore = ("WRITING".equals(attempt.getSkill()) || "SPEAKING".equals(attempt.getSkill()))
                        ? attempt.getScore().doubleValue()
                        : (answersMatch(ans, q.getAnswerKey()) ? 100.0 : 0.0);
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
                    "MOST_PRACTICED", "Luyá»‡n nhiá»u nháº¥t",
                    PracticeDtos.getSkillLabel(mostPracticedSkill), maxAttempts, 0.0, true));
        } else {
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("MOST_PRACTICED", "Luyá»‡n nhiá»u nháº¥t", "", 0, 0.0, false));
        }
        if (minAvgScore <= 100.0 && !needsWorkSkill.isEmpty()) {
            List<PracticeAttempt> skillAttempts = attemptsBySkill(scoredAll, needsWorkSkill);
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight(
                    "NEEDS_WORK", "Cáº§n cáº£i thiá»‡n",
                    PracticeDtos.getSkillLabel(needsWorkSkill), skillAttempts.size(),
                    Math.round(minAvgScore * 10.0) / 10.0, true));
        } else {
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("NEEDS_WORK", "Cáº§n cáº£i thiá»‡n", "", 0, 0.0, false));
        }
        highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("MOST_STABLE", "á»”n Ä‘á»‹nh nháº¥t", "", 0, 0.0, false));
        highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("MOST_IMPROVED", "Tiáº¿n bá»™ nháº¥t", "", 0, 0.0, false));

        List<PracticeResultSummary> history = new ArrayList<>();
        List<PracticeAttempt> historyAttempts = recentAttempts.subList(0, Math.min(30, recentAttempts.size()));
        for (PracticeAttempt attempt : historyAttempts) {
            history.add(toAttemptSummary(attempt, setsById, testsById, sectionsById));
        }

        return new com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics(
                weeklySkillMetrics, scoreTrend, questionTypePerf, highlights, history);
    }

    private List<PracticeSubmission> subsBySkill(List<PracticeSubmission> subs, String skill) {
        List<PracticeSubmission> res = new ArrayList<>();
        for (PracticeSubmission s : subs) {
            PracticeSet set = setRepository.findById(s.getSetId()).orElse(null);
            if (set != null && skill.equals(set.getSkill())) {
                res.add(s);
            }
        }
        return res;
    }

    private int getSkillIndex(String skill) {
        if ("READING".equals(skill)) return 0;
        if ("LISTENING".equals(skill)) return 1;
        if ("WRITING".equals(skill)) return 2;
        if ("SPEAKING".equals(skill)) return 3;
        return 0;
    }

    @Transactional(readOnly = true)
    public com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview getLearningProgressOverview(
            Long userId, String displayName, String avatarUrl) {
        return buildAttemptProgressOverview(userId, displayName, avatarUrl);
    }

    private com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview legacySubmissionProgressOverview(
            Long userId, String displayName, String avatarUrl) {
        List<PracticeSubmission> submissions = submissionRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(
                userId, PracticeSubmission.STATUS_IN_PROGRESS);

        if (submissions.isEmpty()) {
            return new com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview(
                    displayName, avatarUrl, "TOPIK II", 0, 0, 0, 0.0,
                    List.of(
                            new com.ksh.features.practice.dto.PracticeDtos.SkillMetric("READING", "Đọc", 0.0, 0, 0.0),
                            new com.ksh.features.practice.dto.PracticeDtos.SkillMetric("LISTENING", "Nghe", 0.0, 0, 0.0),
                            new com.ksh.features.practice.dto.PracticeDtos.SkillMetric("WRITING", "Viết", 0.0, 0, 0.0),
                            new com.ksh.features.practice.dto.PracticeDtos.SkillMetric("SPEAKING", "Nói", 0.0, 0, 0.0)
                    ),
                    List.of(),
                    List.of()
            );
        }

        // Submissions statistics
        int totalAttempts = submissions.size();
        int totalCompleted = (int) submissions.stream()
                .filter(s -> PracticeSubmission.STATUS_GRADED.equals(s.getStatus()) || PracticeSubmission.STATUS_SUBMITTED.equals(s.getStatus()))
                .count();

        // Compute total minutes (estimating duration if start/end are set)
        int totalPracticeMinutes = 0;
        for (PracticeSubmission s : submissions) {
            if (s.getStartedAt() != null && s.getSubmittedAt() != null) {
                long diff = java.time.temporal.ChronoUnit.MINUTES.between(s.getStartedAt(), s.getSubmittedAt());
                if (diff > 0 && diff < 240) {
                    totalPracticeMinutes += (int) diff;
                } else {
                    totalPracticeMinutes += 30; // default/fallback
                }
            } else {
                totalPracticeMinutes += 30;
            }
        }

        // Calculate average normalized score of recent 20 submissions
        List<PracticeSubmission> recent20 = submissions.subList(0, Math.min(20, submissions.size()));
        double recentAvgSum = 0.0;
        int recentAvgCount = 0;
        for (PracticeSubmission s : recent20) {
            PracticeSet set = setRepository.findById(s.getSetId()).orElse(null);
            if (set != null) {
                recentAvgSum += getNormalizedScore(s, set.getSkill());
                recentAvgCount++;
            }
        }
        double recentAverageScore = recentAvgCount == 0 ? 0.0 : Math.round((recentAvgSum / recentAvgCount) * 100.0) / 100.0;

        // Group by skill
        Map<String, List<PracticeSubmission>> subsBySkillMap = new LinkedHashMap<>();
        subsBySkillMap.put("READING", new ArrayList<>());
        subsBySkillMap.put("LISTENING", new ArrayList<>());
        subsBySkillMap.put("WRITING", new ArrayList<>());
        subsBySkillMap.put("SPEAKING", new ArrayList<>());

        for (PracticeSubmission s : submissions) {
            PracticeSet set = setRepository.findById(s.getSetId()).orElse(null);
            if (set != null && subsBySkillMap.containsKey(set.getSkill())) {
                subsBySkillMap.get(set.getSkill()).add(s);
            }
        }

        List<com.ksh.features.practice.dto.PracticeDtos.SkillMetric> skillMetrics = new ArrayList<>();
        String[] skills = {"READING", "LISTENING", "WRITING", "SPEAKING"};
        String[] skillLabels = {"Đọc", "Nghe", "Viết", "Nói"};
        
        for (int i = 0; i < skills.length; i++) {
            String sk = skills[i];
            String label = skillLabels[i];
            List<PracticeSubmission> skillSubs = subsBySkillMap.get(sk);
            double avgScore = 0.0;
            if (!skillSubs.isEmpty()) {
                double sum = 0.0;
                for (PracticeSubmission s : skillSubs) {
                    sum += getNormalizedScore(s, sk);
                }
                avgScore = Math.round((sum / skillSubs.size()) * 100.0) / 100.0;
            }
            skillMetrics.add(new com.ksh.features.practice.dto.PracticeDtos.SkillMetric(
                    sk, label, avgScore, skillSubs.size(), 0.0));
        }

        // Build study frequency heatmap for the last 84 days (12 weeks)
        Map<java.time.LocalDate, Integer> counts = new LinkedHashMap<>();
        Map<java.time.LocalDate, Integer> mins = new LinkedHashMap<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 83; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            counts.put(d, 0);
            mins.put(d, 0);
        }

        for (PracticeSubmission s : submissions) {
            if (s.getSubmittedAt() != null) {
                java.time.LocalDate sDate = s.getSubmittedAt().toLocalDate();
                if (counts.containsKey(sDate)) {
                    counts.put(sDate, counts.get(sDate) + 1);
                    long diff = java.time.temporal.ChronoUnit.MINUTES.between(s.getStartedAt(), s.getSubmittedAt());
                    int m = (diff > 0 && diff < 240) ? (int) diff : 30;
                    mins.put(sDate, mins.get(sDate) + m);
                }
            }
        }

        List<com.ksh.features.practice.dto.PracticeDtos.HeatmapCell> heatmap = new ArrayList<>();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (java.time.LocalDate d : counts.keySet()) {
            heatmap.add(new com.ksh.features.practice.dto.PracticeDtos.HeatmapCell(
                    d.format(fmt), counts.get(d), mins.get(d)));
        }

        // Recent history: last 8 submissions
        List<PracticeSubmission> recent8 = submissions.subList(0, Math.min(8, submissions.size()));
        List<PracticeResultSummary> recentHistory = new ArrayList<>();
        for (PracticeSubmission s : recent8) {
            PracticeSet set = setRepository.findById(s.getSetId()).orElse(null);
            if (set != null) {
                recentHistory.add(new PracticeResultSummary(
                        s.getId(), set.getTitle(), set.getSkill(), s.getScore(), s.getTotalPoints(), s.getSubmittedAt()));
            }
        }

        // TOPIK level logic: predict level based on average score
        String currentLevel = "TOPIK II Cấp 3";
        if (recentAverageScore >= 80.0) {
            currentLevel = "TOPIK II Cấp 6";
        } else if (recentAverageScore >= 70.0) {
            currentLevel = "TOPIK II Cấp 5";
        } else if (recentAverageScore >= 60.0) {
            currentLevel = "TOPIK II Cấp 4";
        } else if (recentAverageScore < 40.0) {
            currentLevel = "TOPIK I Cấp 1";
        } else if (recentAverageScore < 50.0) {
            currentLevel = "TOPIK I Cấp 2";
        }

        return new com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview(
                displayName, avatarUrl, currentLevel, totalAttempts, totalCompleted,
                totalPracticeMinutes, recentAverageScore, skillMetrics, heatmap, recentHistory);
    }

    @Transactional(readOnly = true)
    public com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics getPracticeAnalytics(Long userId) {
        return buildAttemptPracticeAnalytics(userId);
    }

    private com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics legacySubmissionPracticeAnalytics(Long userId) {
        List<PracticeSubmission> submissions = submissionRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(
                userId, PracticeSubmission.STATUS_IN_PROGRESS);

        if (submissions.isEmpty()) {
            return new com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics(
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // 1. Weekly Skill Metrics: This week vs last week
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime startOfThisWeek = now.minusDays(7);
        java.time.LocalDateTime startOfLastWeek = now.minusDays(14);

        String[] skills = {"READING", "LISTENING", "WRITING", "SPEAKING"};
        String[] skillLabels = {"Đọc", "Nghe", "Viết", "Nói"};
        List<com.ksh.features.practice.dto.PracticeDtos.SkillMetric> weeklySkillMetrics = new ArrayList<>();

        for (int i = 0; i < skills.length; i++) {
            String sk = skills[i];
            String label = skillLabels[i];

            double thisWeekSum = 0.0;
            int thisWeekCount = 0;
            double lastWeekSum = 0.0;
            int lastWeekCount = 0;

            for (PracticeSubmission s : submissions) {
                if (s.getSubmittedAt() == null) continue;
                PracticeSet set = setRepository.findById(s.getSetId()).orElse(null);
                if (set != null && sk.equals(set.getSkill())) {
                    double norm = getNormalizedScore(s, sk);
                    if (s.getSubmittedAt().isAfter(startOfThisWeek)) {
                        thisWeekSum += norm;
                        thisWeekCount++;
                    } else if (s.getSubmittedAt().isAfter(startOfLastWeek)) {
                        lastWeekSum += norm;
                        lastWeekCount++;
                    }
                }
            }

            double thisWeekAvg = thisWeekCount == 0 ? 0.0 : thisWeekSum / thisWeekCount;
            double lastWeekAvg = lastWeekCount == 0 ? 0.0 : lastWeekSum / lastWeekCount;
            double delta = thisWeekCount == 0 || lastWeekCount == 0 ? 0.0 : Math.round((thisWeekAvg - lastWeekAvg) * 100.0) / 100.0;

            weeklySkillMetrics.add(new com.ksh.features.practice.dto.PracticeDtos.SkillMetric(
                    sk, label, Math.round(thisWeekAvg * 100.0) / 100.0, thisWeekCount, delta));
        }

        // 2. Score Trend: last 30 attempts, chronological order (oldest first)
        List<PracticeSubmission> last30 = submissions.subList(0, Math.min(30, submissions.size()));
        List<com.ksh.features.practice.dto.PracticeDtos.ScoreTrendPoint> scoreTrend = new ArrayList<>();
        java.time.format.DateTimeFormatter trendFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = last30.size() - 1; i >= 0; i--) {
            PracticeSubmission s = last30.get(i);
            PracticeSet set = setRepository.findById(s.getSetId()).orElse(null);
            if (set != null) {
                scoreTrend.add(new com.ksh.features.practice.dto.PracticeDtos.ScoreTrendPoint(
                        s.getSubmittedAt() != null ? s.getSubmittedAt().format(trendFmt) : "",
                        set.getSkill(),
                        getNormalizedScore(s, set.getSkill()),
                        set.getTitle()
                ));
            }
        }

        // 3. Question Type Performance / All-time Report
        // Fetch all questions for the sets the user completed
        List<Long> setIds = last30.stream().map(PracticeSubmission::getSetId).distinct().toList();
        List<PracticeQuestion> questions = setIds.isEmpty() ? List.of() : questionRepository.findBySetIdIn(setIds);
        Map<Long, List<PracticeQuestion>> questionsBySetId = questions.stream()
                .collect(java.util.stream.Collectors.groupingBy(PracticeQuestion::getSetId));

        Map<String, List<Double>> scoresByType = new LinkedHashMap<>();
        Map<String, java.time.LocalDateTime> lastPracticedByType = new LinkedHashMap<>();

        // Group by question type
        for (PracticeSubmission s : last30) {
            PracticeSet set = setRepository.findById(s.getSetId()).orElse(null);
            if (set == null) continue;

            Map<String, String> answers = readAnswers(s.getAnswersJson());
            List<PracticeQuestion> setQuestions = questionsBySetId.getOrDefault(s.getSetId(), List.of());

            for (PracticeQuestion q : setQuestions) {
                String type = q.getQuestionType();
                // Map Writing specific question numbers to Q51, Q52, Q53, Q54
                if ("WRITING".equals(set.getSkill())) {
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
                double qScore = 0.0;
                if ("WRITING".equals(set.getSkill()) || "SPEAKING".equals(set.getSkill())) {
                    // For W/S, score is normalized overall. We approximate this question's score by set score.
                    qScore = s.getScore() != null ? s.getScore().doubleValue() : 0.0;
                } else {
                    // MCQ
                    boolean isCorrect = answersMatch(ans, q.getAnswerKey());
                    qScore = isCorrect ? 100.0 : 0.0;
                }

                scoresByType.computeIfAbsent(type, k -> new ArrayList<>()).add(qScore);
                if (s.getSubmittedAt() != null) {
                    java.time.LocalDateTime currentLast = lastPracticedByType.get(type);
                    if (currentLast == null || s.getSubmittedAt().isAfter(currentLast)) {
                        lastPracticedByType.put(type, s.getSubmittedAt());
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

            String skill = "READING";
            if (type.startsWith("Q") || "GENERAL".equals(type)) {
                skill = "WRITING";
            } else if (PracticeQuestion.TYPE_SPEAKING.equals(type)) {
                skill = "SPEAKING";
            } else {
                skill = "READING"; // fallback
            }

            questionTypePerf.add(new com.ksh.features.practice.dto.PracticeDtos.QuestionTypePerf(
                    skill, type, getQuestionTypeLabel(type), qScores.size(),
                    Math.round(avg * 10.0) / 10.0, Math.round(max * 10.0) / 10.0,
                    lastDt != null ? lastDt.format(dtFmt) : ""
            ));
        }

        // 4. Performance Highlights
        List<com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight> highlights = new ArrayList<>();
        String mostPracticedSkill = "";
        int maxAttempts = 0;
        String needsWorkSkill = "";
        double minAvgScore = 101.0;

        for (int i = 0; i < skills.length; i++) {
            String sk = skills[i];
            List<PracticeSubmission> skillSubs = subsBySkill(submissions, sk);
            if (!skillSubs.isEmpty()) {
                if (skillSubs.size() > maxAttempts) {
                    maxAttempts = skillSubs.size();
                    mostPracticedSkill = sk;
                }
                double sum = 0.0;
                for (PracticeSubmission s : skillSubs) {
                    sum += getNormalizedScore(s, sk);
                }
                double avg = sum / skillSubs.size();
                if (avg < minAvgScore) {
                    minAvgScore = avg;
                    needsWorkSkill = sk;
                }
            }
        }

        if (maxAttempts >= 3 && !mostPracticedSkill.isEmpty()) {
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight(
                    "MOST_PRACTICED", "Luyện nhiều nhất", skillLabels[getSkillIndex(mostPracticedSkill)], maxAttempts, 0.0, true));
        } else {
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("MOST_PRACTICED", "Luyện nhiều nhất", "", 0, 0.0, false));
        }

        if (minAvgScore <= 100.0 && !needsWorkSkill.isEmpty()) {
            List<PracticeSubmission> skillSubs = subsBySkill(submissions, needsWorkSkill);
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight(
                    "NEEDS_WORK", "Cần cải thiện", skillLabels[getSkillIndex(needsWorkSkill)], skillSubs.size(), Math.round(minAvgScore * 10.0) / 10.0, true));
        } else {
            highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("NEEDS_WORK", "Cần cải thiện", "", 0, 0.0, false));
        }

        highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("MOST_STABLE", "Ổn định nhất", "", 0, 0.0, false));
        highlights.add(new com.ksh.features.practice.dto.PracticeDtos.PerformanceHighlight("MOST_IMPROVED", "Tiến bộ nhất", "", 0, 0.0, false));

        // 5. History: last 30 completed submissions
        List<PracticeResultSummary> history = new ArrayList<>();
        for (PracticeSubmission s : last30) {
            PracticeSet set = setRepository.findById(s.getSetId()).orElse(null);
            if (set != null) {
                history.add(new PracticeResultSummary(
                        s.getId(), set.getTitle(), set.getSkill(), s.getScore(), s.getTotalPoints(), s.getSubmittedAt()));
            }
        }

        return new com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics(
                weeklySkillMetrics, scoreTrend, questionTypePerf, highlights, history);
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
                set.getTopikLevel(),
                PracticeDtos.getCategoryLabel(set.getTopikLevel()),
                badgeText(set.getSkill(), set.getTopikLevel()),
                set.getMetadataJson(),
                set.getCreationMethod()
        );
    }

    private PracticeQuestionRow toQuestionRow(PracticeQuestion question) {
        return new PracticeQuestionRow(
                question.getId(),
                question.getQuestionNo(),
                question.getQuestionType(),
                question.getPrompt(),
                readOptions(question.getOptionsJson()),
                question.getAnswerKey(),
                question.getExplanation(),
                groupLabel(question.getQuestionNo())
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
                g.getInstruction(),
                g.getAudioUrl(),
                exampleBox,
                questions
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private BigDecimal extractAiScore(String aiFeedback) {
        try {
            JsonNode root = objectMapper.readTree(aiFeedback);
            double score = root.path("score").asDouble(root.path("overall_score").asDouble(1.0));
            if (score <= 0.0) {
                return BigDecimal.ZERO;
            }
            return WritingScoreMatrix.toHundredPointScale(score);
        } catch (Exception ex) {
            // Fallback: score band 1.0 = "Không phản hồi" → 11.11/100
            return WritingScoreMatrix.toHundredPointScale(1.0);
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
            case PracticeQuestion.TYPE_MCQ,
                    PracticeQuestion.TYPE_SHORT_TEXT,
                    PracticeQuestion.TYPE_TRUE_FALSE_NOT_GIVEN,
                    PracticeQuestion.TYPE_MATCHING_INFORMATION,
                    PracticeQuestion.TYPE_FILL_BLANK,
                    PracticeQuestion.TYPE_ORDERING,
                    PracticeQuestion.TYPE_TEXT_COMPLETION -> true;
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

    private static String badgeText(String skill, String topikLevel) {
        String skillLbl = PracticeDtos.getSkillLabel(skill);
        String catLbl = PracticeDtos.getCategoryLabel(topikLevel);
        if ("Chưa phân loại".equals(catLbl)) {
            return skillLbl;
        }
        return skillLbl + " · " + catLbl;
    }






    private String mockSpeakingFeedback(String prompt, String answer) {
        Map<String, Object> feedback = new LinkedHashMap<>();
        int wordCount = answer == null || answer.isBlank() ? 0 : answer.trim().split("\\s+").length;
        double score = wordCount < 8 ? 3.0 : wordCount < 25 ? 5.5 : 7.0;
        feedback.put("score", score);
        feedback.put("overall_score", score);
        feedback.put("summary_vi", "Đây là đánh giá mô phỏng cho kỹ năng nói. Hệ thống ghi nhận độ dài câu trả lời, mức độ bám câu hỏi và sự mạch lạc cơ bản.");
        feedback.put("rubric_scores", List.of(
                Map.of("name", "Nội dung & Thực hiện nhiệm vụ (내용 및 과제 수행)", "score", score, "feedback", "Câu trả lời cần bám sát câu hỏi và có ví dụ cụ thể hơn."),
                Map.of("name", "Khả năng xây dựng bài nói (담화 구성)", "score", Math.max(1.0, score - 0.5), "feedback", "Nên mở ý, giải thích và kết luận ngắn gọn để bài nói mạch lạc."),
                Map.of("name", "Khả năng kiểm soát ngôn ngữ (언어 수행)", "score", Math.max(1.0, score - 1.0), "feedback", "Mock evaluator chưa phân tích âm thanh thật; bước sau sẽ nối transcription/phát âm.")
        ));
        feedback.put("strengths", List.of(Map.of(
                "criterionId", "S_FLUENCY",
                "evidence", answer == null || answer.isBlank() ? "응답 없음" : answer,
                "explanationVi", "Bạn đã có phản hồi cho câu hỏi, đây là điểm khởi đầu tốt để luyện nói.",
                "correction", ""
        )));
        feedback.put("needs_improvement", List.of(Map.of(
                "criterionId", "S_PRONUNCIATION_IMPROVEMENT",
                "evidence", answer == null || answer.isBlank() ? "응답 없음" : answer,
                "explanationVi", "Hiện đang là mock evaluator nên chưa chấm phát âm thật. Hãy nói rõ, đủ câu và ghi âm ở bản nâng cấp.",
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
        return attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Lượt làm bài không tồn tại"));
    }

    @Transactional(readOnly = true)
    public List<PracticeSection> getSectionsForTest(Long setId, Long testId) {
        return sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId).stream()
                .filter(s -> testId.equals(s.getTestId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, PracticeAttempt> getInProgressAttemptsBySection(Long testId, Long userId) {
        List<PracticeAttempt> attempts = attemptRepository.findByTestIdAndUserIdOrderByCreatedAtDesc(testId, userId);
        Map<Long, PracticeAttempt> map = new LinkedHashMap<>();
        for (PracticeAttempt att : attempts) {
            if (PracticeAttempt.STATUS_IN_PROGRESS.equals(att.getStatus())) {
                map.putIfAbsent(att.getSectionId(), att);
            }
        }
        return map;
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

        String skill = section.getSkill();
        if (skill == null || (!"READING".equals(skill) && !"LISTENING".equals(skill) &&
            !"WRITING".equals(skill) && !"SPEAKING".equals(skill))) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Skill không hợp lệ");
        }

        Optional<PracticeAttempt> existing = attemptRepository
                .findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(
                        userId, testId, sectionId, PracticeAttempt.STATUS_IN_PROGRESS);

        if (existing.isPresent()) {
            PracticeAttempt attempt = existing.get();
            if (setId.equals(attempt.getSetId()) &&
                testId.equals(attempt.getTestId()) &&
                sectionId.equals(attempt.getSectionId()) &&
                skill.equals(attempt.getSkill()) &&
                userId.equals(attempt.getUserId()) &&
                PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
                log.info("[PracticeService] Reusing existing IN_PROGRESS PracticeAttempt id={} for user={}", attempt.getId(), userId);
                return attempt.getId();
            }
        }

        PracticeAttempt attempt = new PracticeAttempt(userId, setId, testId, skill, sectionId);
        attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS);
        PracticeAttempt saved = attemptRepository.save(attempt);
        log.info("[PracticeService] Created new PracticeAttempt id={} for user={} section={}", saved.getId(), userId, sectionId);
        return saved.getId();
    }

    @Transactional
    public Long submitAttempt(Long attemptId, Long userId, Map<String, String> form) {
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

        PracticeSet set = loadPublished(attempt.getSetId());

        // Re-use logic to load and grade only the questions of the current section
        List<PracticeQuestionGroupRow> groupRows = getQuestionGroupsForSection(attempt.getSetId(), section.getId());
        List<Long> sectionQuestionIds = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .map(com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionRow::id)
                .toList();

        List<PracticeQuestion> allQuestions = questionRepository.findBySetIdOrderByDisplayOrderAsc(attempt.getSetId());
        List<PracticeQuestion> sectionQuestions = allQuestions.stream()
                .filter(q -> sectionQuestionIds.contains(q.getId()))
                .toList();

        Map<String, String> answers = new LinkedHashMap<>();
        if (attempt.getAnswersJson() != null && !attempt.getAnswersJson().isBlank()) {
            try {
                Map<String, String> prev = objectMapper.readValue(attempt.getAnswersJson(), new TypeReference<Map<String, String>>() {});
                answers.putAll(prev);
            } catch (Exception e) {
                log.warn("[submitAttempt] Failed to parse previous in-progress answers", e);
            }
        }

        // Process only form fields that belong to sectionQuestions
        for (PracticeQuestion q : sectionQuestions) {
            String key = "answer_" + q.getId();
            if (form.containsKey(key)) {
                String answer = form.get(key).trim();
                answers.put(String.valueOf(q.getId()), answer);
            }
        }

        BigDecimal score = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        String aiFeedback = null;

        for (PracticeQuestion q : sectionQuestions) {
            total = total.add(q.getPoints());
            String answer = answers.getOrDefault(String.valueOf(q.getId()), "").trim();

            if (PracticeQuestion.TYPE_MCQ.equals(q.getQuestionType())) {
                if (answersMatch(answer, q.getAnswerKey())) {
                    score = score.add(q.getPoints());
                }
            } else if (PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType())) {
                aiFeedback = evaluationClient.evaluate(q.getPrompt(), answer);
                score = extractAiScore(aiFeedback);
            } else if (PracticeQuestion.TYPE_SPEAKING.equals(q.getQuestionType())) {
                aiFeedback = mockSpeakingFeedback(q.getPrompt(), answer);
                score = extractAiScore(aiFeedback);
            } else if (isAutoScoredByKey(q.getQuestionType())) {
                if (answersMatch(answer, q.getAnswerKey())) {
                    score = score.add(q.getPoints());
                }
            }
        }

        // Generate explanation only for objective Reading/Listening sections if requested
        if (usesAnswerExplanations(set) && !hasWritingOrSpeaking(sectionQuestions)) {
            Map<String, Object> explanationAnswers = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : answers.entrySet()) {
                explanationAnswers.put(entry.getKey(), entry.getValue());
            }
            try {
                aiFeedback = answerExplanationClient.explain(set, sectionQuestions, explanationAnswers);
            } catch (Exception ex) {
                log.warn("[PracticeService] Gemini API key is out of quota or failed, skipping AI explanation: {}", ex.getMessage());
                aiFeedback = null;
            }
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
                log.warn("[saveInProgress] Failed to parse previous answers JSON", e);
            }
        }

        // Merge new answers
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (entry.getKey().startsWith("answer_")) {
                String qId = entry.getKey().substring(7);
                currentAnswers.put(qId, entry.getValue().trim());
            }
        }

        attempt.setAnswersJson(writeJson(currentAnswers));
        attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS);
        attemptRepository.save(attempt);
    }

    @Transactional
    public void discardAttempt(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lượt làm bài"));
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new IllegalStateException("Chỉ có thể hủy lượt làm bài chưa hoàn thành.");
        }
        attemptRepository.delete(attempt);
        log.info("[PracticeService] Discarded in-progress PracticeAttempt id={} for user={}", attemptId, userId);
    }

    @Transactional(readOnly = true)
    public PracticeSubmission getPracticeSubmission(Long submissionId, Long userId) {
        return submissionRepository.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Lượt làm bài không tồn tại"));
    }

    @Transactional(readOnly = true)
    public List<PracticeSubmission> getAttempts(Long setId, Long userId) {
        return submissionRepository.findBySetIdAndUserIdOrderByCreatedAtDesc(setId, userId);
    }

    /**
     * Unified result builder — replaces the old rl-result / result split.
     * PracticeSection.skill is the source of truth for which template fragment to render.
     */
    @Transactional(readOnly = true)
    public PracticeAttemptResultView getAttemptResult(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        PracticeSet set = setRepository.findById(attempt.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));

        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));

        List<PracticeQuestionGroupRow> groupRows = getQuestionGroupsForSection(attempt.getSetId(), section.getId());
        List<PracticeQuestionRow> dbQuestions = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .toList();

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
            for (PracticeQuestionRow q : qs) {
                String ans = submittedAnswers.getOrDefault(String.valueOf(q.id()), "").trim();
                if (isObjective || isAutoScoredByKey(type)) {
                    boolean ok = answersMatch(ans, q.answerKey());
                    if (ok) {
                        correct++;
                    }
                }
            }
            if (isObjective) {
                int incorrect = total - correct;
                correctCount += correct;
                incorrectCount += incorrect;
                String accuracyPct = total == 0 ? "0%" : Math.round(((double) correct / total) * 100) + "%";
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
                    boolean isCorrect = answersMatch(ans, q.answerKey());
                    String explanationJson = null;
                    try {
                        PracticeQuestion pq = questionRepository.findById(q.id()).orElse(null);
                        if (pq != null) {
                            explanationJson = readingListeningExplanationService.getOrCreateExplanation(
                                    pq, g.instruction(), skill, set.getId(), optionLabelMode);
                        }
                    } catch (Exception e) {
                        log.warn("[PracticeService] Failed to get explanation for question id={}: {}", q.id(), e.getMessage());
                    }
                    qRows.add(new ReviewQuestionRow(q.id(), q.questionNo(), q.questionType(),
                            q.prompt(), q.options(), q.answerKey() != null ? q.answerKey() : "",
                            ans, isCorrect, explanationJson));
                }
                reviewGroups.add(new ReviewGroupRow(g.groupLabel(), g.instruction(),
                        g.instruction() != null ? g.instruction() : "",
                        audioStorageService.resolveUrlSafe(g.audioUrl()), qRows));
            }
        }

        List<SectionResultRow> sectionRows = new ArrayList<>();
        sectionRows.add(new SectionResultRow(
                section.getId(),
                section.getTitle(),
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
                toSetRow(set),
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
        PracticeSet set = setRepository.findById(attempt.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        PracticeSection section = sectionRepository.findById(attempt.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException("Section không tồn tại"));

        List<PracticeQuestionGroupRow> groupRows = getQuestionGroupsForSection(attempt.getSetId(), section.getId());
        List<PracticeQuestionRow> dbQuestions = groupRows.stream()
                .flatMap(g -> g.questions().stream())
                .toList();

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
            for (PracticeQuestionRow q : qs) {
                String ans = submittedAnswers.getOrDefault(String.valueOf(q.id()), "").trim();
                boolean isCorrect = answersMatch(ans, q.answerKey());
                if (isCorrect) {
                    correct++;
                } else {
                    incorrect++;
                }
            }
            correctCount += correct;
            incorrectCount += incorrect;

            String accuracyPct = total == 0 ? "0%" : Math.round(((double) correct / total) * 100) + "%";
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
                boolean isCorrect = answersMatch(ans, q.answerKey());
                String explanationJson = null;
                try {
                    PracticeQuestion pq = questionRepository.findById(q.id()).orElse(null);
                    if (pq != null) {
                        explanationJson = readingListeningExplanationService.getOrCreateExplanation(
                                pq, g.instruction(), section.getSkill(), set.getId(), optionLabelMode
                        );
                    }
                } catch (Exception e) {
                    log.warn("[PracticeService] Failed to get explanation for question id={}: {}", q.id(), e.getMessage());
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
                    g.instruction() != null ? g.instruction() : "",
                    audioStorageService.resolveUrlSafe(g.audioUrl()),
                    questions
            ));
        }

        return new ReadingListeningResultView(
                attempt.getId(),
                toSetRow(set),
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
            case PracticeQuestion.TYPE_MCQ -> "객관식 (Trắc nghiệm)";
            case PracticeQuestion.TYPE_TRUE_FALSE_NOT_GIVEN -> "맞다/틀리다 (Đúng/Sai)";
            case PracticeQuestion.TYPE_MATCHING_INFORMATION -> "선 잇기 (Nối thông tin)";
            case PracticeQuestion.TYPE_FILL_BLANK -> "빈칸 채우기 (Điền từ)";
            case PracticeQuestion.TYPE_ORDERING -> "순서 배열 (Sắp xếp thứ tự)";
            case PracticeQuestion.TYPE_TEXT_COMPLETION -> "문장 완성 (Hoàn thành câu)";
            case PracticeQuestion.TYPE_SHORT_TEXT -> "단답형 (Trả lời ngắn)";
            case PracticeQuestion.TYPE_ESSAY -> "쓰기/주관식 (Tự luận)";
            case PracticeQuestion.TYPE_SPEAKING -> "말하기 (Nói)";
            default -> type;
        };
    }

    private String getOptionLabelMode(PracticeSet set) {
        return PracticeDtos.getOptionLabelMode(set.getTitle(), set.getMetadataJson());
    }
}
