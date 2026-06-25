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

@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final PracticeSetRepository setRepository;
    private final PracticeQuestionRepository questionRepository;
    private final PracticeSubmissionRepository submissionRepository;
    private final PracticeQuestionGroupRepository groupRepository;
    private final PracticeSectionRepository sectionRepository;
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
    public Long reEvaluate(Long submissionId, Long userId) {
        PracticeSubmission submission = submissionRepository.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        PracticeSet set = setRepository.findById(submission.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        List<PracticeQuestion> questions = questionRepository.findBySetIdOrderByDisplayOrderAsc(set.getId());
        Map<String, String> submittedAnswers = readAnswers(submission.getAnswersJson());

        BigDecimal score = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        String aiFeedback = null;

        for (PracticeQuestion q : questions) {
            total = total.add(q.getPoints());
            String answer = submittedAnswers.getOrDefault(String.valueOf(q.getId()), "").trim();

            if (PracticeQuestion.TYPE_MCQ.equals(q.getQuestionType())) {
                if (answersMatch(answer, q.getAnswerKey())) {
                    score = score.add(q.getPoints());
                }
            } else if (PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType())) {
                try {
                    aiFeedback = evaluationClient.evaluate(q.getPrompt(), answer, true);
                } catch (Exception ex) {
                    log.warn("[PracticeService] AI essay re-evaluation failed, falling back to mock: {}", ex.getMessage());
                    aiFeedback = mockWritingFeedback(q.getPrompt(), answer);
                }
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

        if (usesAnswerExplanations(set) && !hasWritingOrSpeaking(questions)) {
            Map<String, Object> answersForExplanation = new LinkedHashMap<>(submittedAnswers);
            aiFeedback = answerExplanationClient.explain(set, questions, answersForExplanation);
        }

        submission.updateEvaluation(score, total, writeJson(submittedAnswers), aiFeedback);
        return submission.getId();
    }

    @Transactional(readOnly = true)
    public PracticeResultView getResult(Long submissionId, Long userId) {
        PracticeSubmission submission = submissionRepository.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        PracticeSet set = setRepository.findById(submission.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        List<PracticeQuestionRow> questions = questionRepository.findBySetIdOrderByDisplayOrderAsc(set.getId())
                .stream()
                .map(this::toQuestionRow)
                .toList();
        List<PracticeAnswerExplanationRow> answerExplanations = usesAnswerExplanations(set)
                ? answerExplanationRows(submission.getAiFeedbackJson(), questions, submission.getAnswersJson())
                : List.of();
        List<PracticeAnswerReviewRow> answerReviews = answerReviewRows(questions, submission.getAnswersJson());

        return new PracticeResultView(
                submission.getId(),
                toSetRow(set),
                submission.getScore(),
                submission.getTotalPoints(),
                scoreLabel(submission.getScore(), submission.getTotalPoints()),
                submission.getAnswersJson(),
                submission.getAiFeedbackJson(),
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

    private String mockWritingFeedback(String prompt, String answer) {
        Map<String, Object> feedback = new LinkedHashMap<>();
        int length = answer == null ? 0 : answer.trim().length();
        double score = length < 50 ? 3.5 : length < 150 ? 5.5 : length < 300 ? 7.5 : 8.5;
        double rawScore = score * 10.0;

        feedback.put("score", score);
        feedback.put("overall_score", score);
        feedback.put("raw_score", rawScore);
        feedback.put("raw_score_max", 100.0);
        feedback.put("task_type", "GENERAL");
        feedback.put("student_text", answer == null ? "" : answer);
        feedback.put("summary_vi", "Bài viết đã đáp ứng được yêu cầu cơ bản của đề. Bố cục tương đối rõ ràng, có sử dụng một số từ liên kết. Tuy nhiên, cần cải thiện cấu trúc ngữ pháp và chính tả để đạt điểm cao hơn trong TOPIK.");

        // rubric_scores: 3 entries, fine with Map.of
        feedback.put("rubric_scores", List.of(
            buildMap("name", "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score", score, "feedback", "Nội dung trả lời đúng chủ đề, có lập luận cơ bản."),
            buildMap("name", "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score", Math.max(1.0, score - 0.5), "feedback", "Bố cục mở-thân-kết nhận ra được, nhưng cần dùng từ nối phong phú hơn."),
            buildMap("name", "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score", Math.max(1.0, score - 1.0), "feedback", "Một số lỗi đuôi câu và khoảng cách chữ (띄어쓰기) cần khắc phục.")
        ));

        // Strengths with full schema (12 keys — must use LinkedHashMap)
        String evidenceStr1 = (answer != null && answer.length() > 30)
            ? answer.substring(0, Math.min(answer.length(), 35)) : "한국어를 배우는";
        String evidenceStr2 = (answer != null && answer.contains("때문"))
            ? "때문" : (answer != null && answer.length() > 60 ? answer.substring(30, Math.min(answer.length(), 55)) : "한국 문화");

        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("criterionId", "W_NATURAL_KOREAN_EXPRESSIONS");
        s1.put("category", "Diễn đạt tự nhiên");
        s1.put("subcategory", "Paraphrase đề tự nhiên");
        s1.put("evidence", evidenceStr1);
        s1.put("explanationVi", "Bạn đã diễn đạt lại chủ đề bằng từ ngữ tự nhiên, không chép nguyên văn đề bài.");
        s1.put("correction", "");
        s1.put("severity", "LOW");
        s1.put("displayType", "PHRASE");
        s1.put("uiLabel", "Diễn đạt tự nhiên");
        s1.put("errorType", "");
        s1.put("whyItIsGood", "Khả năng paraphrase thể hiện vốn từ chủ động và sự hiểu biết sâu về chủ đề.");
        s1.put("topikTip", "TOPIK chấm cao những bài dùng từ thay thế phong phú thay vì chép lại đề.");

        Map<String, Object> s2 = new LinkedHashMap<>();
        s2.put("criterionId", "W_APPROPRIATE_VOCABULARY_USAGE");
        s2.put("category", "Từ vựng phù hợp");
        s2.put("subcategory", "Tránh khẩu ngữ");
        s2.put("evidence", evidenceStr2);
        s2.put("explanationVi", "Từ dùng phù hợp văn phong viết, tránh được khẩu ngữ thông thường.");
        s2.put("correction", "");
        s2.put("severity", "LOW");
        s2.put("displayType", "PHRASE");
        s2.put("uiLabel", "Từ vựng văn viết");
        s2.put("errorType", "");
        s2.put("whyItIsGood", "Dùng từ văn viết trang trọng thể hiện trình độ tiếng Hàn nâng cao.");
        s2.put("topikTip", "Thay 좋다, 많다 bằng các từ như 우수하다, 다양하다 để tăng điểm từ vựng.");

        feedback.put("strengths", List.of(s1, s2));

        // Needs improvement with full schema
        Map<String, Object> n1 = new LinkedHashMap<>();
        n1.put("criterionId", "W_SPELLING_SPACING_ERRORS");
        n1.put("category", "Lỗi chính tả & cách chữ");
        n1.put("subcategory", "Lỗi 받침 / đuôi phụ âm");
        n1.put("evidence", answer != null && answer.contains("배운") ? "배운" : "있습니다");
        n1.put("explanationVi", "Lỗi chính tả khi ghép phụ âm cuối. Cần kiểm tra lại quy tắc 받침 trong văn viết.");
        n1.put("correction", answer != null && answer.contains("배운") ? "배우는" : "있습니다");
        n1.put("severity", "MEDIUM");
        n1.put("displayType", "WORD");
        n1.put("uiLabel", "Lỗi 받침");
        n1.put("errorType", "띄어쓰기 오류");
        n1.put("whyItIsGood", "");
        n1.put("topikTip", "Trong TOPIK, lỗi chính tả bị trừ điểm trực tiếp ở tiêu chí 언어 사용.");

        Map<String, Object> n2 = new LinkedHashMap<>();
        n2.put("criterionId", "W_GRAMMAR_ERRORS");
        n2.put("category", "Lỗi ngữ pháp");
        n2.put("subcategory", "Sai đuôi chỉ nguyên nhân");
        n2.put("evidence", answer != null && answer.contains("하여서") ? "하여서" : "공부해서");
        n2.put("explanationVi", "Văn viết TOPIK nên dùng -(으)므로 hoặc -기 때문에 thay vì -여서/-아서 trong văn nghị luận.");
        n2.put("correction", answer != null && answer.contains("하여서") ? "하므로" : "공부하므로");
        n2.put("severity", "MEDIUM");
        n2.put("displayType", "WORD");
        n2.put("uiLabel", "Sai đuôi nguyên nhân");
        n2.put("errorType", "호응 오류");
        n2.put("whyItIsGood", "");
        n2.put("topikTip", "Câu 54 TOPIK yêu cầu văn phong nghị luận: -(으)므로, -기 때문에 trang trọng hơn -아서.");

        Map<String, Object> n3 = new LinkedHashMap<>();
        n3.put("criterionId", "W_REGISTER_CONSISTENCY_ISSUES");
        n3.put("category", "Văn phong");
        n3.put("subcategory", "Trộn văn nói & văn viết");
        n3.put("evidence", answer != null && answer.contains("해요") ? "해요" : (answer != null && answer.contains("좋아해") ? "좋아해" : "합니다"));
        n3.put("explanationVi", "Đuôi 해요체 không phù hợp trong bài viết nghị luận TOPIK. Nên thống nhất dùng 합니다체 hoặc 해라체.");
        n3.put("correction", answer != null && answer.contains("해요") ? "합니다" : "좋아합니다");
        n3.put("severity", "HIGH");
        n3.put("displayType", "WORD");
        n3.put("uiLabel", "Trộn văn nói");
        n3.put("errorType", "존칭 오류");
        n3.put("whyItIsGood", "");
        n3.put("topikTip", "Bài nghị luận TOPIK phải nhất quán 해라체. Tránh tuyệt đối 해요체.");

        feedback.put("needs_improvement", List.of(n1, n2, n3));

        // Build annotations manually for mock
        String studentText = answer == null ? "" : answer;
        List<Map<String, Object>> annotations = new ArrayList<>();
        if (!studentText.isBlank()) {
            if (studentText.length() > 10) {
                String ev1 = studentText.substring(0, Math.min(studentText.length(), 35));
                Map<String, Object> ann1 = new LinkedHashMap<>();
                ann1.put("id", "ann_s1");
                ann1.put("kind", "strength");
                ann1.put("criterionId", "W_NATURAL_KOREAN_EXPRESSIONS");
                ann1.put("category", "Diễn đạt tự nhiên");
                ann1.put("evidence", ev1);
                ann1.put("start", 0);
                ann1.put("end", ev1.length());
                ann1.put("explanationVi", "Diễn đạt lại chủ đề tự nhiên.");
                ann1.put("correction", "");
                ann1.put("severity", "LOW");
                ann1.put("displayType", "PHRASE");
                ann1.put("index", 1);
                annotations.add(ann1);
            }
            int needIdx = studentText.contains("배운") ? studentText.indexOf("배운")
                         : studentText.contains("있습니다") ? studentText.indexOf("있습니다") : -1;
            if (needIdx >= 0) {
                String ev2 = studentText.contains("배운") ? "배운" : "있습니다";
                Map<String, Object> ann2 = new LinkedHashMap<>();
                ann2.put("id", "ann_n1");
                ann2.put("kind", "need");
                ann2.put("criterionId", "W_SPELLING_SPACING_ERRORS");
                ann2.put("category", "Lỗi chính tả & cách chữ");
                ann2.put("evidence", ev2);
                ann2.put("start", needIdx);
                ann2.put("end", needIdx + ev2.length());
                ann2.put("explanationVi", "Lỗi 받침.");
                ann2.put("correction", studentText.contains("배운") ? "배우는" : "있습니다");
                ann2.put("severity", "MEDIUM");
                ann2.put("displayType", "WORD");
                ann2.put("index", 1);
                annotations.add(ann2);
            }
        }
        feedback.put("annotations", annotations);
        feedback.put("upgraded_annotations", List.of());

        String upgradedBase = (answer == null || answer.isBlank()) ? "한국어를 열심히 공부하겠습니다." : answer;
        feedback.put("upgraded_answer", upgradedBase + " 앞으로도 꾸준히 연습하여 TOPIK 쓰기 영역에서 높은 점수를 획득하고자 한다.");
        feedback.put("upgraded_answer_annotated", "");
        feedback.put("sentence_rewrites", List.of(
            buildMap(
                "original", answer != null && answer.contains("배운는") ? "배운는" : "열심히 공부해요",
                "upgraded", answer != null && answer.contains("배운는") ? "배우는" : "열심히 공부한다",
                "reason", "Sửa đuôi câu sang văn phong viết nhất quán 해라체 phù hợp TOPIK."
            )
        ));
        feedback.put("sample_answer", "한국어를 배우는 이유는 한국 문화에 대한 깊은 관심 때문이다. 특히 한국 드라마, 영화, K-POP 등 한류 콘텐츠를 자막 없이 즐기고 싶으며, 한국인들과 직접 소통하고 그들의 생활 방식을 이해하고 싶다. 앞으로는 문법과 어휘를 꾸준히 학습하여 TOPIK II 5급 이상을 목표로 실력을 쌓아 나갈 계획이다.");

        return writeJson(feedback);
    }

    /** Helper: create a Map from alternating key-value pairs (max 10 pairs = 20 args fine with varargs) */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildMap(Object... kvPairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            m.put((String) kvPairs[i], kvPairs[i + 1]);
        }
        return m;
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

    @Transactional
    public Long startAttempt(Long setId, Long userId) {
        PracticeSet set = loadPublished(setId);
        List<PracticeSubmission> existing = submissionRepository.findBySetIdAndUserIdOrderByCreatedAtDesc(setId, userId);
        for (PracticeSubmission s : existing) {
            if (PracticeSubmission.STATUS_IN_PROGRESS.equals(s.getStatus())) {
                log.info("[PracticeService] Reusing existing IN_PROGRESS attempt id={} for user={}", s.getId(), userId);
                return s.getId();
            }
        }
        
        PracticeSubmission submission = new PracticeSubmission(
                setId,
                userId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "{}",
                null
        );
        submission.setStatus(PracticeSubmission.STATUS_IN_PROGRESS);
        PracticeSubmission saved = submissionRepository.save(submission);
        return saved.getId();
    }

    @Transactional
    public Long submitAttempt(Long attemptId, Long userId, Map<String, String> form) {
        PracticeSubmission submission = submissionRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy lượt làm bài"));

        Long setId = submission.getSetId();
        PracticeSet set = loadPublished(setId);
        List<PracticeQuestion> questions = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId);
        if (questions.isEmpty()) {
            throw new jakarta.persistence.EntityNotFoundException("Bộ luyện tập chưa có câu hỏi");
        }

        Map<String, Object> answers = new LinkedHashMap<>();
        if (submission.getAnswersJson() != null && !submission.getAnswersJson().isBlank()) {
            try {
                Map<String, String> prev = objectMapper.readValue(submission.getAnswersJson(), new TypeReference<Map<String, String>>() {});
                answers.putAll(prev);
            } catch (Exception e) {
                log.warn("[submitAttempt] Failed to parse previous in-progress answers", e);
            }
        }

        BigDecimal score = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        String aiFeedback = null;

        for (PracticeQuestion q : questions) {
            total = total.add(q.getPoints());
            String key = "answer_" + q.getId();
            String answer;
            if (form.containsKey(key)) {
                answer = form.get(key).trim();
                answers.put(String.valueOf(q.getId()), answer);
            } else {
                answer = String.valueOf(answers.getOrDefault(String.valueOf(q.getId()), "")).trim();
            }

            if (PracticeQuestion.TYPE_MCQ.equals(q.getQuestionType())) {
                if (answersMatch(answer, q.getAnswerKey())) {
                    score = score.add(q.getPoints());
                }
            } else if (PracticeQuestion.TYPE_ESSAY.equals(q.getQuestionType())) {
                try {
                    aiFeedback = evaluationClient.evaluate(q.getPrompt(), answer);
                } catch (Exception ex) {
                    log.warn("[PracticeService] AI essay submit evaluation failed, falling back to mock: {}", ex.getMessage());
                    aiFeedback = mockWritingFeedback(q.getPrompt(), answer);
                }
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

        if (usesAnswerExplanations(set) && !hasWritingOrSpeaking(questions)) {
            aiFeedback = answerExplanationClient.explain(set, questions, answers);
        }

        submission.updateEvaluation(score, total, writeJson(answers), aiFeedback);
        submission.setStatus(aiFeedback == null ? PracticeSubmission.STATUS_SUBMITTED : PracticeSubmission.STATUS_GRADED);
        submission.setSubmittedAt(LocalDateTime.now());

        PracticeSubmission saved = submissionRepository.save(submission);
        return saved.getId();
    }

    @Transactional
    public void saveInProgressAnswers(Long attemptId, Long userId, Map<String, String> form) {
        PracticeSubmission submission = submissionRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy lượt làm bài"));

        Map<String, String> currentAnswers = new LinkedHashMap<>();
        if (submission.getAnswersJson() != null && !submission.getAnswersJson().isBlank()) {
            try {
                currentAnswers.putAll(objectMapper.readValue(submission.getAnswersJson(), new TypeReference<Map<String, String>>() {}));
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

        submission.updateEvaluation(BigDecimal.ZERO, BigDecimal.ZERO, writeJson(currentAnswers), null);
        submission.setStatus(PracticeSubmission.STATUS_IN_PROGRESS);
    }

    @Transactional
    public void discardAttempt(Long attemptId, Long userId) {
        PracticeSubmission submission = submissionRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lượt làm bài"));
        if (!PracticeSubmission.STATUS_IN_PROGRESS.equals(submission.getStatus())) {
            throw new IllegalStateException("Chỉ có thể hủy lượt làm bài chưa hoàn thành.");
        }
        submissionRepository.delete(submission);
        log.info("[PracticeService] Discarded in-progress attempt id={} for user={}", attemptId, userId);
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
    public PracticeAttemptResultView getAttemptResult(Long submissionId, Long userId) {
        PracticeSubmission submission = submissionRepository.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        PracticeSet set = setRepository.findById(submission.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));

        List<PracticeSection> sections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(set.getId());
        List<PracticeQuestion> allQuestions = questionRepository.findBySetIdOrderByDisplayOrderAsc(set.getId());
        List<PracticeQuestionGroup> allGroups = groupRepository.findBySetIdOrderByDisplayOrderAsc(set.getId());

        Map<String, String> submittedAnswers = readAnswers(submission.getAnswersJson());
        String optionLabelMode = getOptionLabelMode(set);

        // Build a map: groupId -> group entity for quick lookup
        Map<Long, PracticeQuestionGroup> groupById = new LinkedHashMap<>();
        for (PracticeQuestionGroup g : allGroups) groupById.put(g.getId(), g);

        // Build a map: sectionId -> list of questions (via group.sectionId)
        Map<Long, List<PracticeQuestion>> questionsBySection = new LinkedHashMap<>();
        for (PracticeQuestion q : allQuestions) {
            PracticeQuestionGroup grp = groupById.get(q.getGroupId());
            if (grp != null && grp.getSectionId() != null) {
                questionsBySection.computeIfAbsent(grp.getSectionId(), k -> new ArrayList<>()).add(q);
            }
        }

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalPoints = BigDecimal.ZERO;

        List<SectionResultRow> sectionRows = new ArrayList<>();

        for (PracticeSection section : sections) {
            List<PracticeQuestion> secQs = questionsBySection.getOrDefault(section.getId(), List.of());
            List<PracticeQuestionGroup> secGrps = allGroups.stream()
                    .filter(g -> section.getId().equals(g.getSectionId()))
                    .toList();

            String skill = section.getSkill();
            boolean isObjective = "READING".equals(skill) || "LISTENING".equals(skill);

            BigDecimal sectionScore = BigDecimal.ZERO;
            BigDecimal sectionTotal = BigDecimal.ZERO;
            int correctCount = 0;
            int incorrectCount = 0;
            String sectionAiFeedback = null;

            // Performance breakdown by question type
            Map<String, List<PracticeQuestion>> byType = new LinkedHashMap<>();
            for (PracticeQuestion q : secQs) {
                byType.computeIfAbsent(q.getQuestionType(), k -> new ArrayList<>()).add(q);
            }

            List<PerformanceByTypeRow> perfByType = new ArrayList<>();
            for (Map.Entry<String, List<PracticeQuestion>> entry : byType.entrySet()) {
                String type = entry.getKey();
                List<PracticeQuestion> qs = entry.getValue();
                int total = qs.size();
                int correct = 0;
                for (PracticeQuestion q : qs) {
                    sectionTotal = sectionTotal.add(q.getPoints());
                    String ans = submittedAnswers.getOrDefault(String.valueOf(q.getId()), "").trim();
                    if (isObjective || isAutoScoredByKey(type)) {
                        boolean ok = answersMatch(ans, q.getAnswerKey());
                        if (ok) {
                            correct++;
                            sectionScore = sectionScore.add(q.getPoints());
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

            // For subjective sections: pull aiFeedbackJson from submission (stored during submit)
            if (!isObjective) {
                sectionAiFeedback = submission.getAiFeedbackJson();
                // sectionScore already computed in submitAttempt path
                sectionScore = submission.getScore() != null ? submission.getScore() : BigDecimal.ZERO;
            }

            // Build review groups
            List<ReviewGroupRow> reviewGroups = new ArrayList<>();
            if (isObjective) {
                for (PracticeQuestionGroup g : secGrps) {
                    List<ReviewQuestionRow> qRows = new ArrayList<>();
                    List<PracticeQuestion> grpQs = secQs.stream()
                            .filter(q -> g.getId().equals(q.getGroupId()))
                            .toList();
                    for (PracticeQuestion q : grpQs) {
                        String ans = submittedAnswers.getOrDefault(String.valueOf(q.getId()), "").trim();
                        boolean isCorrect = answersMatch(ans, q.getAnswerKey());
                        String explanationJson = readingListeningExplanationService.getOrCreateExplanation(
                                q, g.getInstruction(), skill, set.getId(), optionLabelMode);
                        qRows.add(new ReviewQuestionRow(q.getId(), q.getQuestionNo(), q.getQuestionType(),
                                q.getPrompt(), readOptions(q.getOptionsJson()),
                                q.getAnswerKey() != null ? q.getAnswerKey() : "",
                                ans, isCorrect, explanationJson));
                    }
                    reviewGroups.add(new ReviewGroupRow(g.getGroupLabel(), g.getInstruction(),
                            g.getInstruction() != null ? g.getInstruction() : "",
                            audioStorageService.resolveUrlSafe(g.getAudioUrl()), qRows));
                }
            }

            totalScore = totalScore.add(sectionScore);
            totalPoints = totalPoints.add(sectionTotal);

            sectionRows.add(new SectionResultRow(
                    section.getId(),
                    section.getTitle(),
                    skill,
                    correctCount,
                    incorrectCount,
                    secQs.size(),
                    sectionScore,
                    sectionTotal,
                    perfByType,
                    reviewGroups,
                    sectionAiFeedback,
                    optionLabelMode
            ));
        }

        // Fallback: if no sections exist (legacy set without sections), treat the whole set as one section
        if (sectionRows.isEmpty()) {
            log.warn("[getAttemptResult] No sections for setId={}, falling back to legacy result", set.getId());
            ReadingListeningResultView legacy = getReadingListeningResult(submissionId, userId);
            String skill = set.getSkill() != null ? set.getSkill() : "READING";
            sectionRows.add(new SectionResultRow(null, set.getTitle(), skill,
                    legacy.correctCount(), legacy.incorrectCount(), legacy.totalCount(),
                    legacy.score(), legacy.totalPoints(),
                    legacy.performanceByType(), legacy.groups(),
                    submission.getAiFeedbackJson(), optionLabelMode));
            totalScore = legacy.score();
            totalPoints = legacy.totalPoints();
        }

        String scoreLabel = totalPoints.compareTo(BigDecimal.ZERO) == 0 ? "0%"
                : totalScore.divide(totalPoints, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP) + "%";

        return new PracticeAttemptResultView(
                submission.getId(),
                toSetRow(set),
                totalScore,
                totalPoints,
                scoreLabel,
                submission.getSubmittedAt(),
                sectionRows
        );
    }

    @Transactional
    public ReadingListeningResultView getReadingListeningResult(Long submissionId, Long userId) {
        PracticeSubmission submission = submissionRepository.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        PracticeSet set = setRepository.findById(submission.getSetId())
                .orElseThrow(() -> new EntityNotFoundException("Bộ luyện tập không tồn tại"));
        List<PracticeQuestion> dbQuestions = questionRepository.findBySetIdOrderByDisplayOrderAsc(set.getId());
        List<PracticeQuestionGroup> dbGroups = groupRepository.findBySetIdOrderByDisplayOrderAsc(set.getId());

        Map<String, String> submittedAnswers = readAnswers(submission.getAnswersJson());

        int correctCount = 0;
        int incorrectCount = 0;
        int totalCount = dbQuestions.size();

        // Calculate performance by question type
        Map<String, List<PracticeQuestion>> questionsByType = new LinkedHashMap<>();
        for (PracticeQuestion q : dbQuestions) {
            questionsByType.computeIfAbsent(q.getQuestionType(), k -> new ArrayList<>()).add(q);
        }

        List<PerformanceByTypeRow> performanceByType = new ArrayList<>();
        for (Map.Entry<String, List<PracticeQuestion>> entry : questionsByType.entrySet()) {
            String type = entry.getKey();
            List<PracticeQuestion> qs = entry.getValue();
            int total = qs.size();
            int correct = 0;
            int incorrect = 0;
            for (PracticeQuestion q : qs) {
                String ans = submittedAnswers.getOrDefault(String.valueOf(q.getId()), "").trim();
                boolean isCorrect = answersMatch(ans, q.getAnswerKey());
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
        if (dbGroups.isEmpty()) {
            // fallback grouping
            List<PracticeQuestionGroupRow> fallbackRows = fallbackGrouping(dbQuestions);
            for (PracticeQuestionGroupRow fg : fallbackRows) {
                List<ReviewQuestionRow> questions = new ArrayList<>();
                for (PracticeQuestionRow qr : fg.questions()) {
                    PracticeQuestion q = dbQuestions.stream().filter(item -> item.getId().equals(qr.id())).findFirst().orElse(null);
                    if (q != null) {
                        String ans = submittedAnswers.getOrDefault(String.valueOf(q.getId()), "").trim();
                        boolean isCorrect = answersMatch(ans, q.getAnswerKey());
                        String explanationJson = readingListeningExplanationService.getOrCreateExplanation(q, "", set.getSkill(), set.getId(), optionLabelMode);
                        questions.add(new ReviewQuestionRow(
                                q.getId(),
                                q.getQuestionNo(),
                                q.getQuestionType(),
                                q.getPrompt(),
                                readOptions(q.getOptionsJson()),
                                q.getAnswerKey() != null ? q.getAnswerKey() : "",
                                ans,
                                isCorrect,
                                explanationJson
                        ));
                    }
                }
                groups.add(new ReviewGroupRow(
                        fg.groupLabel(),
                        fg.instruction(),
                        fg.instruction() != null ? fg.instruction() : "",
                        null,
                        questions
                ));
            }
        } else {
            for (PracticeQuestionGroup g : dbGroups) {
                List<ReviewQuestionRow> questions = new ArrayList<>();
                List<PracticeQuestion> groupQs = dbQuestions.stream()
                        .filter(q -> g.getId().equals(q.getGroupId()))
                        .toList();

                for (PracticeQuestion q : groupQs) {
                    String ans = submittedAnswers.getOrDefault(String.valueOf(q.getId()), "").trim();
                    boolean isCorrect = answersMatch(ans, q.getAnswerKey());
                    String explanationJson = readingListeningExplanationService.getOrCreateExplanation(
                            q, g.getInstruction(), set.getSkill(), set.getId(), optionLabelMode
                    );
                    questions.add(new ReviewQuestionRow(
                            q.getId(),
                            q.getQuestionNo(),
                            q.getQuestionType(),
                            q.getPrompt(),
                            readOptions(q.getOptionsJson()),
                            q.getAnswerKey() != null ? q.getAnswerKey() : "",
                            ans,
                            isCorrect,
                            explanationJson
                    ));
                }
                groups.add(new ReviewGroupRow(
                        g.getGroupLabel(),
                        g.getInstruction(),
                        g.getInstruction() != null ? g.getInstruction() : "",
                        audioStorageService.resolveUrlSafe(g.getAudioUrl()),
                        questions
                ));
            }
        }

        return new ReadingListeningResultView(
                submission.getId(),
                toSetRow(set),
                submission.getScore(),
                submission.getTotalPoints(),
                correctCount,
                incorrectCount,
                totalCount,
                performanceByType,
                groups,
                submission.getAnswersJson(),
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

