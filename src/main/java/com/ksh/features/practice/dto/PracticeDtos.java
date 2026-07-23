package com.ksh.features.practice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class PracticeDtos {
    private PracticeDtos() {
    }

    public static String getSkillLabel(String skill) {
        if (skill == null) return "Chưa xác định";
        return switch (skill.toUpperCase()) {
            case "READING" -> "Đọc";
            case "LISTENING" -> "Nghe";
            case "WRITING" -> "Viết";
            case "SPEAKING" -> "Nói";
            case "MIXED" -> "Tổng hợp";
            default -> skill;
        };
    }

    public record PracticeSetRow(Long id, String title, String description,
                                 String skill, String skillLabel,
                                 String metadataJson,
                                 String creationMethod) {
    }

    public record PracticeCatalogQuery(String search, String skill,
                                       Long classId, int batch) {
    }

    public record PracticeCatalogSkill(String code, String label) {
    }

    public record PracticeCatalogClassOption(Long id, String name) {
    }

    public record PracticeCatalogCard(
            Long id,
            String title,
            String description,
            String primarySkill,
            List<PracticeCatalogSkill> skills,
            int testCount,
            int completedTests,
            String visibilityLabel,
            String state,
            String stateLabel,
            Long resumeAttemptId
    ) {
        public boolean hasSkill(String code) {
            if (code == null) return false;
            if (skills != null && skills.stream()
                    .anyMatch(skill -> code.equalsIgnoreCase(skill.code()))) {
                return true;
            }
            return (skills == null || skills.isEmpty())
                    && code.equalsIgnoreCase(primarySkill);
        }

        public boolean multiSkill() {
            return skills != null && skills.size() > 1;
        }

        public String coverSkill() {
            return multiSkill() ? "MIXED" : primarySkill;
        }

        public String coverLabel() {
            if (skills == null || skills.isEmpty()) return "LUYỆN TẬP";
            if (skills.size() == 2) return "2 KỸ NĂNG";
            if (skills.size() > 2) return "TỔNG HỢP";
            return switch (skills.get(0).code()) {
                case "LISTENING" -> "NGHE";
                case "READING" -> "ĐỌC";
                case "WRITING" -> "VIẾT";
                case "SPEAKING" -> "NÓI";
                default -> "LUYỆN TẬP";
            };
        }

        public String skillSummary() {
            if (skills == null || skills.isEmpty()) return "Chưa xác định";
            return String.join(", ", skills.stream()
                    .map(PracticeCatalogSkill::label)
                    .toList());
        }

        public String skillCodes() {
            if (skills == null || skills.isEmpty()) {
                return primarySkill == null ? "" : primarySkill;
            }
            return String.join(",", skills.stream()
                    .map(PracticeCatalogSkill::code)
                    .toList());
        }

        public int progressPercent() {
            if (testCount <= 0) return 0;
            return Math.min(100, Math.max(0,
                    (int) Math.round(completedTests * 100.0 / testCount)));
        }
    }

    public record PracticeCatalogBatch(
            List<PracticeCatalogCard> items,
            List<PracticeCatalogClassOption> classes,
            String search,
            String skill,
            Long classId,
            int batch,
            int batchSize,
            long totalElements,
            boolean hasMore
    ) {
        public int nextBatch() {
            return batch + 1;
        }

        public PracticeCatalogCard resumeCard() {
            if (items == null) return null;
            return items.stream()
                    .filter(item -> item.resumeAttemptId() != null)
                    .findFirst()
                    .orElse(null);
        }
    }


    public record ExampleBox(
        String label,
        String content,
        List<String> choices,
        Integer answer
    ) {
    }

    public record PracticeQuestionOptionRow(String id, String text, String imageReference) {
    }

    public record PracticeQuestionBlankRow(String id, String prompt) {
    }

    public record PracticeQuestionRow(Long id, Integer questionNo,
                                      String questionType, String prompt,
                                      List<String> options,
                                      String answerKey,
                                      String explanation,
                                      String groupLabel,
                                      String imageReference,
                                      String audioReference,
                                      List<PracticeQuestionOptionRow> optionRows,
                                      List<PracticeQuestionBlankRow> blankRows) {
        public PracticeQuestionRow(Long id, Integer questionNo,
                                   String questionType, String prompt,
                                   List<String> options,
                                   String answerKey,
                                   String explanation,
                                   String groupLabel,
                                   String imageReference,
                                   String audioReference,
                                   List<PracticeQuestionOptionRow> optionRows) {
            this(id, questionNo, questionType, prompt, options, answerKey, explanation,
                    groupLabel, imageReference, audioReference, optionRows, null);
        }

        public PracticeQuestionRow(Long id, Integer questionNo,
                                   String questionType, String prompt,
                                   List<String> options,
                                   String answerKey,
                                   String explanation,
                                   String groupLabel) {
            this(id, questionNo, questionType, prompt, options, answerKey, explanation,
                    groupLabel, null, null, null, null);
        }

        public PracticeQuestionRow {
            options = options == null ? List.of() : List.copyOf(options);
            optionRows = optionRows == null || optionRows.isEmpty()
                    ? fallbackOptionRows(options)
                    : List.copyOf(optionRows);
            blankRows = blankRows == null ? List.of() : List.copyOf(blankRows);
        }

        private static List<PracticeQuestionOptionRow> fallbackOptionRows(List<String> options) {
            if (options == null || options.isEmpty()) {
                return List.of();
            }
            return java.util.stream.IntStream.range(0, options.size())
                    .mapToObj(index -> new PracticeQuestionOptionRow(
                            "opt_" + (index + 1), options.get(index), null))
                    .toList();
        }
    }

    public record PracticeQuestionGroupRow(
        Long id,
        Long sectionId,
        String groupLabel,
        Integer questionFrom,
        Integer questionTo,
        String instruction,
        String stimulusType,
        String passageText,
        String transcriptText,
        String imageUrl,
        String stimulusProvenanceJson,
        String audioUrl,
        ExampleBox exampleBox,
        List<PracticeQuestionRow> questions
    ) {
        public PracticeQuestionGroupRow(Long id,
                                        Long sectionId,
                                        String groupLabel,
                                        Integer questionFrom,
                                        Integer questionTo,
                                        String instruction,
                                        String audioUrl,
                                        ExampleBox exampleBox,
                                        List<PracticeQuestionRow> questions) {
            this(id, sectionId, groupLabel, questionFrom, questionTo, instruction,
                    null, null, null, null, null, audioUrl, exampleBox, questions);
        }
    }

    public record PracticeTestRow(Long id,
                                  Long setId,
                                  String title,
                                  String description,
                                  Integer displayOrder,
                                  Integer estimatedMinutes) {
    }

    public record PracticeSetTestCard(
            Long id,
            String title,
            String description,
            Integer displayOrder,
            Integer estimatedMinutes,
            List<PracticeCatalogSkill> skills,
            int completedSkillCount,
            int totalSkillCount,
            String state,
            String stateLabel,
            Long resumeAttemptId
    ) {
        public boolean hasSkill(String code) {
            return code != null && skills != null && skills.stream()
                    .anyMatch(skill -> code.equalsIgnoreCase(skill.code()));
        }

        public int progressPercent() {
            if (totalSkillCount <= 0) return 0;
            return Math.min(100, Math.max(0,
                    (int) Math.round(completedSkillCount * 100.0 / totalSkillCount)));
        }

        public String actionLabel() {
            return switch (state) {
                case "IN_PROGRESS" -> "Tiếp tục";
                case "COMPLETED" -> "Xem bài";
                case "PARTIAL" -> "Tiếp tục luyện";
                default -> "Bắt đầu";
            };
        }
    }

    public record PracticeAttemptCard(
            Long id,
            int attemptNumber,
            String scoreLabel,
            String status,
            String statusLabel,
            LocalDateTime activityAt,
            boolean initiallyVisible
    ) {
    }

    public record PracticeSkillAttemptCard(
            Long sectionId,
            String title,
            String skill,
            String skillLabel,
            Integer durationMinutes,
            BigDecimal totalPoints,
            Long inProgressAttemptId,
            List<PracticeAttemptCard> completedAttempts,
            String state,
            String stateLabel,
            String latestScoreLabel,
            String bestScoreLabel
    ) {
        public boolean hasInProgressAttempt() {
            return inProgressAttemptId != null;
        }

        public boolean hasCompletedAttempts() {
            return completedAttempts != null && !completedAttempts.isEmpty();
        }

        public int completedAttemptCount() {
            return completedAttempts == null ? 0 : completedAttempts.size();
        }

        public int hiddenAttemptCount() {
            if (completedAttempts == null) return 0;
            return (int) completedAttempts.stream()
                    .filter(attempt -> !attempt.initiallyVisible())
                    .count();
        }

        public Long latestCompletedAttemptId() {
            if (!hasCompletedAttempts()) return null;
            return completedAttempts.get(0).id();
        }

        public String actionLabel() {
            if (hasInProgressAttempt()) return "Tiếp tục";
            return hasCompletedAttempts() ? "Làm lại" : "Bắt đầu";
        }
    }

    public record PracticeAnswerExplanationRow(Integer questionNo,
                                               String questionType,
                                               String prompt,
                                               String learnerAnswer,
                                               String answerKey,
                                               String meaningVi,
                                               String evidenceQuote,
                                               String correctReasonVi,
                                               String relatedTranslationVi,
                                               List<EliminatedOptionExplanation> eliminatedOptions) {
    }

    public record PracticeAnswerReviewRow(Long questionId,
                                          Integer questionNo,
                                          String questionType,
                                          String prompt,
                                          String learnerAnswer) {
    }

    public record PracticeDraftQuestion(Integer questionNo, String questionType,
                                        String prompt, List<String> options,
                                        String optionsText,
                                        String answerKey, String explanation,
                                        BigDecimal points) {
    }

    public record PracticeDraftGroup(
        String groupLabel,
        Integer questionFrom,
        Integer questionTo,
        String instruction,
        String audioUrl,
        String exampleBoxJson,
        List<PracticeDraftQuestion> questions
    ) {
    }

    public record PracticePdfDraftView(String title, String description,
                                       String skill,
                                       String scope, Long classId,
                                       String sourcePdfPath,
                                       String metadataJson,
                                       List<PracticeDraftGroup> groups,
                                       String originalFilename) {
        @Override
        public List<PracticeDraftGroup> groups() {
            return groups == null ? List.of() : groups;
        }

        public List<PracticeDraftQuestion> questions() {
            return groups().stream()
                    .flatMap(g -> g.questions().stream())
                    .toList();
        }
    }

    public record PracticeSetView(PracticeSetRow set,
                                  List<PracticeQuestionGroupRow> groups,
                                  List<SectionView> sections,
                                  List<PracticeTestRow> tests) {
        // Convenience constructor for code that only supplies groups (backward-compat)
        public PracticeSetView(PracticeSetRow set, List<PracticeQuestionGroupRow> groups) {
            this(set, groups, List.of(), List.of());
        }

        public PracticeSetView(PracticeSetRow set, List<PracticeQuestionGroupRow> groups, List<SectionView> sections) {
            this(set, groups, sections, List.of());
        }

        public boolean writing() {
            return sections.stream().anyMatch(s -> "WRITING".equals(s.skill()));
        }

        public boolean listening() {
            return sections.stream().anyMatch(s -> "LISTENING".equals(s.skill()));
        }

        public boolean reading() {
            return sections.stream().anyMatch(s -> "READING".equals(s.skill()));
        }

        public boolean speaking() {
            return sections.stream().anyMatch(s -> "SPEAKING".equals(s.skill()));
        }

        public int totalQuestions() {
            return groups.stream()
                    .mapToInt(g -> g.questions().size())
                    .sum();
        }
    }

    /**
     * A single section visible during the exam player — its skill, duration,
     * title, and ordered groups.
     */
    public record SectionView(
            Long id,
            String title,
            String skill,
            int durationMinutes,
            List<PracticeQuestionGroupRow> groups
    ) {
        public int totalQuestions() {
            return groups.stream().mapToInt(g -> g.questions().size()).sum();
        }
    }

    public record PracticeResultView(Long submissionId, Long testId, PracticeSetRow set,
                                     BigDecimal score, BigDecimal totalPoints,
                                     String scoreLabel,
                                     String answersJson,
                                     String aiFeedbackJson,
                                     List<PracticeQuestionRow> questions,
                                     List<PracticeAnswerReviewRow> answerReviews,
                                     List<PracticeAnswerExplanationRow> answerExplanations,
                                     List<PracticeQuestionFeedbackRow> questionFeedbacks,
                                     List<SpeakingQuestionFeedbackRow> speakingQuestionFeedbacks) {
        public PracticeResultView(Long submissionId, PracticeSetRow set,
                                  BigDecimal score, BigDecimal totalPoints,
                                  String scoreLabel,
                                  String answersJson,
                                  String aiFeedbackJson,
                                  List<PracticeQuestionRow> questions,
                                  List<PracticeAnswerReviewRow> answerReviews,
                                  List<PracticeAnswerExplanationRow> answerExplanations,
                                  List<PracticeQuestionFeedbackRow> questionFeedbacks,
                                  List<SpeakingQuestionFeedbackRow> speakingQuestionFeedbacks) {
            this(submissionId, null, set, score, totalPoints, scoreLabel, answersJson, aiFeedbackJson,
                    questions, answerReviews, answerExplanations, questionFeedbacks, speakingQuestionFeedbacks);
        }

        public boolean hasAiFeedback() {
            return aiFeedbackJson != null && !aiFeedbackJson.isBlank();
        }

        public boolean hasRubricFeedback() {
            return hasAiFeedback() && ("WRITING".equals(set.skill()) || "SPEAKING".equals(set.skill()));
        }

        public boolean hasAnswerExplanations() {
            return answerExplanations != null && !answerExplanations.isEmpty();
        }
    }

    public record PracticeQuestionFeedbackRow(
            Long questionId,
            Integer questionNo,
            String questionType,
            String prompt,
            String learnerAnswer,
            WritingFeedbackView writingFeedback,
            boolean reEvaluatable
    ) {}

    public record SpeakingQuestionFeedbackRow(
            Long questionId,
            Integer questionNo,
            String questionType,
            String prompt,
            String learnerAnswer,
            SpeakingFeedbackView speakingFeedback,
            WritingFeedbackView legacyEssayFeedback,
            boolean feedbackAvailable,
            boolean legacyFeedbackApplied
    ) {}

    public record SpeakingFeedbackView(
            BigDecimal percentage,
            String summary,
            String summaryVi,
            List<SpeakingRubricScoreView> rubricScores,
            List<SpeakingFindingView> strengths,
            List<SpeakingFindingView> needsImprovement,
            String sampleAnswer,
            String correctedVersion,
            String engine,
            String source,
            String evaluationStatus,
            boolean scoreAvailable,
            String levelLabel,
            String overallSummary,
            String taskAchievementSummary,
            List<String> majorStrengths,
            List<String> majorNeedsImprovement,
            List<SpeakingActionPlanView> actionPlan,
            List<SpeakingCriterionFeedbackView> criterionFeedback,
            List<SpeakingTranscriptAnnotationView> transcriptAnnotations,
            List<SpeakingTranscriptAnnotationView> annotations,
            String transcript,
            String normalizedTranscript,
            String actuallyHeardTranscript,
            String interpretedIntent,
            BigDecimal transcriptConfidence,
            String confidenceNotes,
            String listenerBurden,
            List<String> pronunciationAdvisory,
            List<String> fluencyObservations,
            String errorCategory,
            boolean retryable,
            Long audioMediaId,
            Long mediaVersion,
            String promptVersion,
            String rubricVersion,
            String schemaVersion,
            String model,
            String transcriptionModel,
            String evaluatorCapability,
            String evidenceMode,
            String evidenceContractVersion,
            String contractTrust,
            boolean profileAvailable,
            boolean holisticScoreAvailable
    ) {
        public SpeakingFeedbackView(
                BigDecimal percentage,
                String summary,
                String summaryVi,
                List<SpeakingRubricScoreView> rubricScores,
                List<SpeakingFindingView> strengths,
                List<SpeakingFindingView> needsImprovement,
                String sampleAnswer,
                String correctedVersion,
                String engine,
                String source
        ) {
            this(
                    percentage, summary, summaryVi, rubricScores, strengths, needsImprovement,
                    sampleAnswer, correctedVersion, engine, source,
                    null, percentage != null, null, summaryVi == null ? summary : summaryVi, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    null, null, null, null, null, null, null, List.of(), List.of(),
                    null, false, null, null, null, null, null, engine, null,
                    "LEGACY_UNKNOWN", "UNKNOWN", null, "LEGACY_UNVERIFIED", false, false);
        }

        public SpeakingFeedbackView {
            rubricScores = rubricScores == null ? List.of() : List.copyOf(rubricScores);
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            needsImprovement = needsImprovement == null ? List.of() : List.copyOf(needsImprovement);
            majorStrengths = majorStrengths == null ? List.of() : List.copyOf(majorStrengths);
            majorNeedsImprovement = majorNeedsImprovement == null ? List.of() : List.copyOf(majorNeedsImprovement);
            actionPlan = actionPlan == null ? List.of() : List.copyOf(actionPlan);
            criterionFeedback = criterionFeedback == null ? List.of() : List.copyOf(criterionFeedback);
            transcriptAnnotations = transcriptAnnotations == null ? List.of() : List.copyOf(transcriptAnnotations);
            annotations = annotations == null ? List.of() : List.copyOf(annotations);
            pronunciationAdvisory = pronunciationAdvisory == null ? List.of() : List.copyOf(pronunciationAdvisory);
            fluencyObservations = fluencyObservations == null ? List.of() : List.copyOf(fluencyObservations);
            if (!holisticScoreAvailable) {
                percentage = null;
                scoreAvailable = false;
                levelLabel = null;
                listenerBurden = null;
                pronunciationAdvisory = List.of();
                fluencyObservations = List.of();
            }
        }
    }

    public record SpeakingRubricScoreView(
            String name,
            BigDecimal percentage,
            String feedback,
            String criterionId,
            BigDecimal score,
            BigDecimal maxScore,
            String availability
    ) {
        public SpeakingRubricScoreView {
            availability = speakingAvailability(availability, score);
            if ("SCORED".equals(availability) && (score == null || maxScore == null)) {
                availability = "UNAVAILABLE";
            }
            if (!"SCORED".equals(availability)) {
                percentage = null;
                score = null;
                maxScore = null;
            }
        }

        public SpeakingRubricScoreView(
                String name,
                BigDecimal percentage,
                String feedback,
                String criterionId,
                BigDecimal score,
                BigDecimal maxScore
        ) {
            this(name, percentage, feedback, criterionId, score, maxScore,
                    score == null ? "UNAVAILABLE" : "SCORED");
        }

        public SpeakingRubricScoreView(String name, BigDecimal percentage, String feedback) {
            this(name, percentage, feedback, null, null, null, "UNAVAILABLE");
        }
    }

    public record SpeakingCriterionFeedbackView(
            String criterionId,
            String name,
            BigDecimal score,
            BigDecimal maxScore,
            String levelLabel,
            String summary,
            List<String> strengths,
            List<String> needsImprovement,
            List<SpeakingSubcriterionFeedbackView> subcriteria
    ) {
        public SpeakingCriterionFeedbackView {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            needsImprovement = needsImprovement == null ? List.of() : List.copyOf(needsImprovement);
            subcriteria = subcriteria == null ? List.of() : List.copyOf(subcriteria);
        }
    }

    public record SpeakingSubcriterionFeedbackView(
            String subcriterionId,
            String name,
            String levelLabel,
            String summary,
            List<String> strengths,
            List<String> needsImprovement
    ) {
        public SpeakingSubcriterionFeedbackView {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            needsImprovement = needsImprovement == null ? List.of() : List.copyOf(needsImprovement);
        }
    }

    public record SpeakingActionPlanView(
            String criterionId,
            String subcriterionId,
            String titleVi,
            String instructionVi,
            String reasonVi,
            String priority
    ) {
        public String criterionLabel() {
            return switch (criterionId == null ? "" : criterionId) {
                case "S_CONTENT_TASK_FULFILLMENT" -> "Nội dung và hoàn thành yêu cầu";
                case "S_GRAMMAR_SENTENCE_CONTROL" -> "Ngữ pháp và kiểm soát câu";
                case "S_VOCABULARY_EXPRESSIONS" -> "Từ vựng và biểu đạt";
                case "S_COHERENCE_ORGANIZATION" -> "Mạch lạc và tổ chức ý";
                default -> null;
            };
        }
    }

    public record SpeakingTranscriptAnnotationView(
            String criterionId,
            String subcriterionId,
            String evidenceScope,
            String evidence,
            String evidenceSource,
            Integer startOffset,
            Integer endOffset,
            Integer start,
            Integer end,
            String annotationType,
            String kind,
            String category,
            String explanationVi,
            String suggestionKo,
            String correction,
            String severity
    ) {}

    public record SpeakingFindingView(
            String criterionId,
            String subcriterionId,
            String evidenceScope,
            String evidence,
            String evidenceSource,
            String explanationVi,
            String correction
    ) {
        public SpeakingFindingView(String criterionId, String explanationVi, String correction) {
            this(criterionId, null, null, null, null, explanationVi, correction);
        }
    }

    public record WritingFeedbackView(
            @JsonProperty("raw_score") BigDecimal rawScore,
            @JsonProperty("raw_score_max") BigDecimal rawScoreMax,
            BigDecimal score,
            String summary,
            @JsonProperty("summary_vi") String summaryVi,
            @JsonProperty("rubric_scores") List<WritingRubricScoreView> rubricScores,
            List<WritingFindingView> strengths,
            @JsonProperty("needs_improvement") List<WritingFindingView> needsImprovement,
            List<WritingAnnotationView> annotations,
            @JsonProperty("upgraded_answer") String upgradedAnswer,
            @JsonProperty("sentence_rewrites") List<WritingSentenceRewriteView> sentenceRewrites,
            @JsonProperty("sample_answer") String sampleAnswer,
            @JsonProperty("evaluation_status") String evaluationStatus,
            @JsonProperty("evaluation_source") String evaluationSource,
            @JsonProperty("evaluation_reason") String evaluationReason,
            @JsonProperty("evaluation_retryable") Boolean evaluationRetryable,
            @JsonProperty("score_available") Boolean scoreAvailable
    ) {
        public WritingFeedbackView {
            rubricScores = rubricScores == null ? List.of() : List.copyOf(rubricScores);
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            needsImprovement = needsImprovement == null ? List.of() : List.copyOf(needsImprovement);
            annotations = annotations == null ? List.of() : List.copyOf(annotations);
            sentenceRewrites = sentenceRewrites == null ? List.of() : List.copyOf(sentenceRewrites);
        }

        public boolean scoreAvailableFlag() {
            return scoreAvailable == null || scoreAvailable;
        }
    }

    public record WritingRubricScoreView(
            String name,
            BigDecimal score,
            String feedback
    ) {}

    public record WritingFindingView(
            String category,
            String vietnameseLabel,
            String uiLabel,
            String criterionId,
            String evidenceScope,
            String evidence,
            String explanationVi,
            String correction,
            String severity,
            String errorType,
            String whyItIsGood,
            String topikTip
    ) {}

    public record WritingAnnotationView(
            String id,
            String kind,
            String criterionId,
            String category,
            Integer start,
            Integer end,
            String severity,
            String displayType,
            Integer index,
            String explanationVi,
            String correction,
            String evidence
    ) {}

    public record WritingSentenceRewriteView(
            String original,
            String upgraded,
            String reason
    ) {}


    public record ReadingListeningResultView(
            Long submissionId,
            Long testId,
            PracticeSetRow set,
            BigDecimal score,
            BigDecimal totalPoints,
            int correctCount,
            int incorrectCount,
            int totalCount,
            List<PerformanceByTypeRow> performanceByType,
            List<ReviewGroupRow> groups,
            String answersJson,
            String optionLabelMode
    ) {
        public ReadingListeningResultView(
                Long submissionId,
                PracticeSetRow set,
                BigDecimal score,
                BigDecimal totalPoints,
                int correctCount,
                int incorrectCount,
                int totalCount,
                List<PerformanceByTypeRow> performanceByType,
                List<ReviewGroupRow> groups,
                String answersJson,
                String optionLabelMode
        ) {
            this(submissionId, null, set, score, totalPoints, correctCount, incorrectCount, totalCount,
                    performanceByType, groups, answersJson, optionLabelMode);
        }
    }

    public record PerformanceByTypeRow(
            String questionType,
            String label,
            int total,
            int correct,
            int incorrect,
            String accuracyPct
    ) {
    }

    public record ReviewGroupRow(
            String groupLabel,
            String instruction,
            String passageText,
            String audioUrl,
            List<ReviewQuestionRow> questions
    ) {
    }

    public record ReviewQuestionRow(
            Long questionId,
            Integer questionNo,
            String questionType,
            String prompt,
            List<String> options,
            String correctAnswer,
            String userAnswer,
            boolean isCorrect,
            String explanationJson
    ) {
    }

    public record QuestionExplanationRow(
            Long questionId,
            String explanationJson
    ) {
    }

    public record LearningProfileView(List<PracticeResultSummary> recent,
                                      long readingCount,
                                      long listeningCount,
                                      long writingCount,
                                      long speakingCount,
                                      BigDecimal averageScore) {
    }

    public record PracticeResultSummary(Long id, String title, String skill,
                                        BigDecimal score, BigDecimal totalPoints,
                                        LocalDateTime submittedAt,
                                        LocalDateTime activityAt,
                                        String status,
                                        Long setId,
                                        Long testId,
                                        Long sectionId) {
        public PracticeResultSummary(Long id, String title, String skill,
                                     BigDecimal score, BigDecimal totalPoints,
                                     LocalDateTime submittedAt) {
            this(id, title, skill, score, totalPoints, submittedAt, submittedAt, null, null, null, null);
        }
    }

    public record PracticePdfImportResult(Long setId, String title, int questionCount) {
    }

    public record SpeakingMediaUploadResponse(
            Long mediaId,
            Long attemptId,
            Long questionId,
            String status,
            Boolean active,
            Long byteSize,
            Long durationMs,
            String mimeType,
            String playbackPath,
            Long lockVersion
    ) {
    }

    public record SpeakingMediaDeleteResponse(
            Long mediaId,
            Long attemptId,
            Long questionId,
            String status,
            Boolean active,
            Boolean pendingCleanup
    ) {
    }

    public record SpeakingMediaView(
            Long mediaId,
            Long questionId,
            String status,
            Long byteSize,
            Long durationMs,
            String mimeType,
            String playbackPath,
            Long lockVersion
    ) {
    }

    public record SpeakingMediaErrorResponse(
            String code,
            String message
    ) {
    }

    public record SkillMetric(
            String skill,
            String skillLabel,
            Double normalizedScore,
            int attemptCount,
            Double deltaFromLastPeriod
    ) {}

    public record HeatmapCell(
            String date,
            int attemptCount,
            int totalMinutes
    ) {}

    public record ScoreTrendPoint(
            String date,
            String skill,
            double normalizedScore,
            String title
    ) {}

    public record PerformanceHighlight(
            String type,
            String label,
            String skillOrType,
            int attempts,
            double score,
            boolean hasData
    ) {}

    public record QuestionTypePerf(
            String skill,
            String questionType,
            String questionTypeLabel,
            int totalAttempts,
            double averageScore,
            double bestScore,
            String lastPracticedAt
    ) {}

    public record LearningProgressOverview(
            String studentName,
            String avatarUrl,
            String currentLevel,
            int totalAttempts,
            int totalCompletedTests,
            int totalPracticeMinutes,
            double recentAverageScore,
            List<SkillMetric> skillMetrics,
            List<HeatmapCell> heatmap,
            List<PracticeResultSummary> recentHistory
    ) {}

    public record PracticeAnalytics(
            List<SkillMetric> weeklySkillMetrics,
            List<ScoreTrendPoint> scoreTrend,
            List<QuestionTypePerf> questionTypePerf,
            List<PerformanceHighlight> highlights,
            List<PracticeResultSummary> history
    ) {}

    public record PracticeProgressPageData(
            LearningProgressOverview overview,
            PracticeAnalytics analytics
    ) {}

    public record EliminatedOptionExplanation(
            String optionKey,
            String reasonVi
    ) {
    }

    public static String getOptionLabelMode(String title, String metadataJson) {
        if (metadataJson != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(metadataJson);
                if (root.has("optionLabelMode") && !root.path("optionLabelMode").asText().isEmpty()) {
                    return root.path("optionLabelMode").asText();
                }
                if (root.has("document")) {
                    com.fasterxml.jackson.databind.JsonNode doc = root.path("document");
                    if (doc.has("detectedCategory")) {
                        String cat = doc.path("detectedCategory").asText();
                        if (cat.startsWith("TOPIK_")) {
                            return "NUMERIC";
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (title != null && title.toUpperCase().contains("TOPIK")) {
            return "NUMERIC";
        }
        return "ALPHA";
    }

    // =========================================================================
    //  Canonical immutable-attempt Result Detail contract
    // =========================================================================

    public enum ResultDetailScreenKind {
        OBJECTIVE_DETAIL,
        WRITING_DETAIL,
        SPEAKING_DETAIL
    }

    public enum ResultDetailPolarity {
        STRENGTH,
        NEEDS_IMPROVEMENT
    }

    public record PracticeResultDetailView(
            String schemaVersion,
            String descriptorPolicyId,
            ResultAttemptIdentity identity,
            ResultState state,
            ResultDetailPayload payload
    ) {
        public static final String SCHEMA_VERSION = "practice-result-detail-v1";
        public static final String DESCRIPTOR_POLICY_ID = "ksh-korean-detail-descriptors-v1";

        public PracticeResultDetailView(
                ResultAttemptIdentity identity,
                ResultState state,
                ResultDetailPayload payload
        ) {
            this(SCHEMA_VERSION, DESCRIPTOR_POLICY_ID, identity, state, payload);
        }

        public PracticeResultDetailView {
            if (identity == null || state == null || payload == null) {
                throw new IllegalArgumentException("Practice Result Detail envelope is incomplete");
            }
            schemaVersion = SCHEMA_VERSION;
            descriptorPolicyId = DESCRIPTOR_POLICY_ID;
            ResultDetailScreenKind expected = switch (identity.skill()) {
                case "READING", "LISTENING" -> ResultDetailScreenKind.OBJECTIVE_DETAIL;
                case "WRITING" -> ResultDetailScreenKind.WRITING_DETAIL;
                case "SPEAKING" -> ResultDetailScreenKind.SPEAKING_DETAIL;
                default -> throw new IllegalArgumentException(
                        "Unsupported skill for Practice Result Detail: " + identity.skill());
            };
            if (payload.screenKind() != expected) {
                throw new IllegalArgumentException(
                        "Practice Result Detail payload does not match immutable attempt skill");
            }
        }

        public ResultDetailScreenKind screenKind() {
            return payload.screenKind();
        }
    }

    public sealed interface ResultDetailPayload
            permits ObjectiveDetailPayload, WritingDetailPayload, SpeakingDetailPayload {
        ResultDetailScreenKind screenKind();
    }

    public record ObjectiveDetailPayload(
            ResultScoreSummary score,
            ResultAnswerDistribution answers,
            ResultFeedbackAvailability feedback,
            ObjectiveResultPayload summary,
            List<ObjectiveSourceGroup> sourceGroups,
            List<ObjectiveQuestionDetail> questions,
            String constructRegistryState,
            String constructRegistryNote
    ) implements ResultDetailPayload {
        public ObjectiveDetailPayload {
            if (score == null || answers == null || feedback == null || summary == null
                    || constructRegistryState == null || constructRegistryState.isBlank()
                    || constructRegistryNote == null || constructRegistryNote.isBlank()) {
                throw new IllegalArgumentException("Objective Result Detail payload is incomplete");
            }
            sourceGroups = immutableResultList(sourceGroups);
            questions = immutableResultList(questions);
            Set<String> sourceIds = new LinkedHashSet<>();
            for (ObjectiveSourceGroup source : sourceGroups) {
                if (!sourceIds.add(source.sourceId())) {
                    throw new IllegalArgumentException(
                            "Objective Result Detail source navigation must be unique");
                }
            }
            Set<Long> questionVersionIds = new LinkedHashSet<>();
            for (ObjectiveQuestionDetail question : questions) {
                if (!questionVersionIds.add(question.core().questionVersionId())) {
                    throw new IllegalArgumentException(
                            "Objective Result Detail must contain one item per immutable question");
                }
                if (!sourceIds.contains(question.core().sourceId())) {
                    throw new IllegalArgumentException(
                            "Objective Result Detail question references a foreign source group");
                }
            }
            Set<Long> navigatedQuestionIds = new LinkedHashSet<>();
            for (ObjectiveSourceGroup source : sourceGroups) {
                for (Long questionVersionId : source.questionVersionIds()) {
                    if (!navigatedQuestionIds.add(questionVersionId)) {
                        throw new IllegalArgumentException(
                                "Objective Result Detail question navigation must be unique");
                    }
                }
            }
            if (!navigatedQuestionIds.equals(questionVersionIds)) {
                throw new IllegalArgumentException(
                        "Objective Result Detail source navigation must match immutable questions");
            }
        }

        @Override
        public ResultDetailScreenKind screenKind() {
            return ResultDetailScreenKind.OBJECTIVE_DETAIL;
        }
    }

    public enum ObjectiveQuestionType {
        SINGLE_CHOICE,
        FILL_BLANK,
        TRUE_FALSE_NOT_GIVEN
    }

    public enum ObjectiveEvidenceKind {
        TEXT_SPAN,
        TRANSCRIPT_SPAN,
        IMAGE_REGION
    }

    public record ObjectiveSourceGroup(
            String sourceId,
            Long groupVersionId,
            String label,
            String sourceKind,
            String instruction,
            String passageText,
            String transcriptText,
            String imageUrl,
            String audioUrl,
            String provenance,
            String transcriptEvidenceScope,
            List<Long> questionVersionIds
    ) {
        public ObjectiveSourceGroup {
            if (sourceId == null || sourceId.isBlank()
                    || label == null || label.isBlank()
                    || sourceKind == null || sourceKind.isBlank()
                    || provenance == null || provenance.isBlank()
                    || transcriptEvidenceScope == null || transcriptEvidenceScope.isBlank()) {
                throw new IllegalArgumentException("Objective source group is incomplete");
            }
            instruction = blankResultText(instruction);
            passageText = blankResultText(passageText);
            transcriptText = blankResultText(transcriptText);
            imageUrl = blankResultText(imageUrl);
            audioUrl = blankResultText(audioUrl);
            questionVersionIds = immutableResultList(questionVersionIds);
            if (questionVersionIds.isEmpty()
                    || new LinkedHashSet<>(questionVersionIds).size() != questionVersionIds.size()
                    || questionVersionIds.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Objective source group question navigation is invalid");
            }
        }

        public boolean hasPassage() {
            return !passageText.isBlank();
        }

        public String provenanceLabelVi() {
            return "PUBLISHED_IMMUTABLE_SNAPSHOT".equals(provenance)
                    ? "Nguồn đề đã xuất bản và khóa"
                    : "Nguồn đề đã khóa";
        }

        public String provenanceLabelKo() {
            return "PUBLISHED_IMMUTABLE_SNAPSHOT".equals(provenance)
                    ? "게시 후 잠긴 출제 자료"
                    : "잠긴 출제 자료";
        }

        public boolean hasApprovedTranscript() {
            return !transcriptText.isBlank();
        }

        public boolean hasImage() {
            return !imageUrl.isBlank();
        }

        public boolean hasAudio() {
            return !audioUrl.isBlank();
        }
    }

    public record ObjectiveQuestionCore(
            Long questionVersionId,
            Long questionId,
            Integer questionNo,
            Integer stableOrder,
            String sourceId,
            String anchorId,
            String prompt,
            String scoreState,
            BigDecimal earnedPoints,
            BigDecimal possiblePoints,
            String learnerAnswerProvenance,
            String officialKeyProvenance,
            String teacherExplanation,
            String teacherExplanationProvenance
    ) {
        public ObjectiveQuestionCore {
            if (questionVersionId == null || questionId == null || questionNo == null
                    || stableOrder == null || stableOrder <= 0
                    || sourceId == null || sourceId.isBlank()
                    || anchorId == null || anchorId.isBlank()
                    || prompt == null || scoreState == null || scoreState.isBlank()
                    || possiblePoints == null
                    || learnerAnswerProvenance == null || learnerAnswerProvenance.isBlank()
                    || officialKeyProvenance == null || officialKeyProvenance.isBlank()
                    || teacherExplanationProvenance == null
                    || teacherExplanationProvenance.isBlank()) {
                throw new IllegalArgumentException("Objective question identity is incomplete");
            }
            teacherExplanation = blankResultText(teacherExplanation);
        }

        public String pointsDisplay() {
            return earnedPoints == null
                    ? null
                    : compactResultNumber(earnedPoints) + "/" + compactResultNumber(possiblePoints);
        }
    }

    public sealed interface ObjectiveQuestionDetail
            permits ObjectiveSingleChoiceDetail, ObjectiveFillBlankDetail, ObjectiveTfngDetail {
        ObjectiveQuestionType questionType();
        ObjectiveQuestionCore core();
        ObjectiveExplanation explanation();
    }

    public record ObjectiveSingleChoiceDetail(
            ObjectiveQuestionCore core,
            List<ObjectiveOptionResult> options,
            ObjectiveExplanation explanation
    ) implements ObjectiveQuestionDetail {
        public ObjectiveSingleChoiceDetail {
            if (core == null || explanation == null) {
                throw new IllegalArgumentException("Single-choice detail is incomplete");
            }
            options = immutableResultList(options);
            if (options.isEmpty()
                    || options.stream().filter(ObjectiveOptionResult::correct).count() != 1
                    || options.stream().filter(ObjectiveOptionResult::learnerSelected).count() > 1
                    || new LinkedHashSet<>(options.stream()
                            .map(ObjectiveOptionResult::optionId).toList()).size() != options.size()) {
                throw new IllegalArgumentException(
                        "Single-choice detail does not match immutable option authority");
            }
            Set<String> evidenceIds = explanation.evidenceRefs().stream()
                    .map(ObjectiveEvidenceRef::evidenceId)
                    .collect(java.util.stream.Collectors.toSet());
            if (options.stream().anyMatch(option ->
                    !evidenceIds.containsAll(option.evidenceIds()))) {
                throw new IllegalArgumentException(
                        "Single-choice option rationale references foreign evidence");
            }
        }

        @Override
        public ObjectiveQuestionType questionType() {
            return ObjectiveQuestionType.SINGLE_CHOICE;
        }
    }

    public record ObjectiveOptionResult(
            String optionId,
            String visibleLabel,
            String text,
            String imageReference,
            boolean learnerSelected,
            boolean correct,
            String learnerState,
            String rationaleVi,
            String rationaleProvenance,
            List<String> evidenceIds
    ) {
        public ObjectiveOptionResult {
            if (optionId == null || optionId.isBlank()
                    || visibleLabel == null || visibleLabel.isBlank()
                    || learnerState == null || learnerState.isBlank()
                    || rationaleVi == null || rationaleVi.isBlank()
                    || rationaleProvenance == null || rationaleProvenance.isBlank()) {
                throw new IllegalArgumentException("Objective option row is incomplete");
            }
            text = blankResultText(text);
            imageReference = blankResultText(imageReference);
            evidenceIds = immutableResultList(evidenceIds);
        }
    }

    public record ObjectiveFillBlankDetail(
            ObjectiveQuestionCore core,
            List<ObjectiveBlankResult> blanks,
            ObjectiveExplanation explanation
    ) implements ObjectiveQuestionDetail {
        public ObjectiveFillBlankDetail {
            if (core == null || explanation == null) {
                throw new IllegalArgumentException("Fill-blank detail is incomplete");
            }
            blanks = immutableResultList(blanks);
            if (blanks.isEmpty()
                    || new LinkedHashSet<>(blanks.stream()
                            .map(ObjectiveBlankResult::blankId).toList()).size() != blanks.size()) {
                throw new IllegalArgumentException(
                        "Fill-blank detail does not match immutable blank authority");
            }
            Set<String> evidenceIds = explanation.evidenceRefs().stream()
                    .map(ObjectiveEvidenceRef::evidenceId)
                    .collect(java.util.stream.Collectors.toSet());
            if (blanks.stream().anyMatch(blank ->
                    !evidenceIds.containsAll(blank.evidenceIds()))) {
                throw new IllegalArgumentException(
                        "Fill-blank explanation references foreign evidence");
            }
        }

        @Override
        public ObjectiveQuestionType questionType() {
            return ObjectiveQuestionType.FILL_BLANK;
        }
    }

    public record ObjectiveBlankResult(
            String blankId,
            String contextKo,
            String learnerValue,
            List<String> acceptedValues,
            String normalizationPolicy,
            boolean correct,
            String contextExplanationVi,
            String semanticConstraintVi,
            String grammarConstraintVi,
            String registerConstraintVi,
            String explanationProvenance,
            List<String> evidenceIds
    ) {
        public ObjectiveBlankResult {
            if (blankId == null || blankId.isBlank()
                    || acceptedValues == null || acceptedValues.isEmpty()
                    || normalizationPolicy == null || normalizationPolicy.isBlank()
                    || explanationProvenance == null || explanationProvenance.isBlank()) {
                throw new IllegalArgumentException("Objective blank row is incomplete");
            }
            contextKo = blankResultText(contextKo);
            learnerValue = blankResultText(learnerValue);
            acceptedValues = immutableResultList(acceptedValues);
            if (acceptedValues.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException(
                        "Objective blank accepted values must be authoritative text");
            }
            contextExplanationVi = blankResultText(contextExplanationVi);
            semanticConstraintVi = blankResultText(semanticConstraintVi);
            grammarConstraintVi = blankResultText(grammarConstraintVi);
            registerConstraintVi = blankResultText(registerConstraintVi);
            evidenceIds = immutableResultList(evidenceIds);
        }
    }

    public record ObjectiveTfngDetail(
            ObjectiveQuestionCore core,
            String claimKo,
            String learnerValue,
            String officialValue,
            String relation,
            String relationExplanationVi,
            String missingInformationVi,
            List<ObjectiveTfngAlternative> alternatives,
            ObjectiveExplanation explanation
    ) implements ObjectiveQuestionDetail {
        public ObjectiveTfngDetail {
            if (core == null || officialValue == null || officialValue.isBlank()
                    || relation == null || relation.isBlank() || explanation == null) {
                throw new IllegalArgumentException("TFNG detail is incomplete");
            }
            claimKo = blankResultText(claimKo);
            learnerValue = blankResultText(learnerValue);
            relationExplanationVi = blankResultText(relationExplanationVi);
            missingInformationVi = blankResultText(missingInformationVi);
            alternatives = immutableResultList(alternatives);
            Set<String> tfngLabels = Set.of("TRUE", "FALSE", "NOT_GIVEN");
            String expectedRelation = switch (officialValue) {
                case "TRUE" -> "ENTAILED";
                case "FALSE" -> "CONTRADICTED";
                case "NOT_GIVEN" -> "NOT_STATED";
                default -> throw new IllegalArgumentException(
                        "TFNG official value is not canonical");
            };
            Set<String> expectedAlternatives = new LinkedHashSet<>(tfngLabels);
            expectedAlternatives.remove(officialValue);
            if (alternatives.size() != 2
                    || alternatives.stream().anyMatch(alternative ->
                            officialValue.equals(alternative.label()))
                    || new LinkedHashSet<>(alternatives.stream()
                            .map(ObjectiveTfngAlternative::label).toList()).size() != 2
                    || !expectedAlternatives.equals(alternatives.stream()
                            .map(ObjectiveTfngAlternative::label)
                            .collect(java.util.stream.Collectors.toSet()))
                    || !expectedRelation.equals(relation)
                    || alternatives.stream().anyMatch(alternative ->
                            !tfngRelationForResult(alternative.label())
                                    .equals(alternative.relation()))
                    || ("NOT_GIVEN".equals(officialValue)
                            && explanation.ready()
                            && missingInformationVi.isBlank())) {
                throw new IllegalArgumentException(
                        "TFNG detail must explain exactly the two non-authoritative labels");
            }
        }

        @Override
        public ObjectiveQuestionType questionType() {
            return ObjectiveQuestionType.TRUE_FALSE_NOT_GIVEN;
        }

        public String learnerValueLabelVi() {
            return learnerValue.isBlank() ? "Chưa trả lời" : tfngLabelVi(learnerValue);
        }

        public String learnerValueLabelKo() {
            return learnerValue.isBlank() ? "미응답" : tfngLabelKo(learnerValue);
        }

        public String officialValueLabelVi() {
            return tfngLabelVi(officialValue);
        }

        public String officialValueLabelKo() {
            return tfngLabelKo(officialValue);
        }

        public String relationLabelVi() {
            return tfngRelationLabelVi(relation);
        }

        public String relationLabelKo() {
            return tfngRelationLabelKo(relation);
        }
    }

    public record ObjectiveTfngAlternative(
            String label,
            String relation,
            String reasonVi,
            String provenance
    ) {
        public ObjectiveTfngAlternative {
            if (label == null || label.isBlank()
                    || relation == null || relation.isBlank()
                    || reasonVi == null || reasonVi.isBlank()
                    || provenance == null || provenance.isBlank()) {
                throw new IllegalArgumentException("TFNG alternative explanation is incomplete");
            }
        }

        public String labelVi() {
            return tfngLabelVi(label);
        }

        public String labelKo() {
            return tfngLabelKo(label);
        }

        public String relationLabelVi() {
            return tfngRelationLabelVi(relation);
        }

        public String relationLabelKo() {
            return tfngRelationLabelKo(relation);
        }
    }

    public record ObjectiveExplanation(
            String state,
            String stateLabel,
            String artifactSchemaVersion,
            String aiMeaningVi,
            String correctReasonVi,
            String aiArtifactProvenance,
            List<ObjectiveEvidenceRef> evidenceRefs,
            List<ObjectiveEvidenceTranslation> evidenceTranslations,
            List<ObjectiveConstructDescriptor> constructDescriptors,
            String constructRegistryState
    ) {
        public ObjectiveExplanation {
            if (state == null || state.isBlank()
                    || stateLabel == null || stateLabel.isBlank()
                    || aiArtifactProvenance == null || aiArtifactProvenance.isBlank()
                    || constructRegistryState == null || constructRegistryState.isBlank()) {
                throw new IllegalArgumentException("Objective explanation state is incomplete");
            }
            artifactSchemaVersion = blankResultText(artifactSchemaVersion);
            aiMeaningVi = blankResultText(aiMeaningVi);
            correctReasonVi = blankResultText(correctReasonVi);
            evidenceRefs = immutableResultList(evidenceRefs);
            evidenceTranslations = immutableResultList(evidenceTranslations);
            constructDescriptors = immutableResultList(constructDescriptors);
            Set<String> evidenceIds = new LinkedHashSet<>();
            for (ObjectiveEvidenceRef evidence : evidenceRefs) {
                if (!evidenceIds.add(evidence.evidenceId())) {
                    throw new IllegalArgumentException(
                            "Objective explanation evidence IDs must be unique");
                }
            }
            Set<String> translatedEvidenceIds = new LinkedHashSet<>();
            for (ObjectiveEvidenceTranslation translation : evidenceTranslations) {
                if (!evidenceIds.contains(translation.evidenceId())
                        || !translatedEvidenceIds.add(translation.evidenceId())) {
                    throw new IllegalArgumentException(
                            "Evidence translation references foreign or duplicate evidence");
                }
            }
            if (!"READY".equals(state)
                    && (!evidenceRefs.isEmpty()
                            || !evidenceTranslations.isEmpty()
                            || !constructDescriptors.isEmpty())) {
                throw new IllegalArgumentException(
                        "Unavailable objective explanation cannot expose artifact evidence");
            }
        }

        public boolean ready() {
            return "READY".equals(state);
        }
    }

    public sealed interface ObjectiveEvidenceRef
            permits ObjectiveTextEvidenceRef, ObjectiveImageEvidenceRef {
        String evidenceId();
        ObjectiveEvidenceKind kind();
        String purpose();
        String sourceRole();
    }

    public record ObjectiveTextEvidenceRef(
            String evidenceId,
            ObjectiveEvidenceKind kind,
            String purpose,
            String sourceRole,
            String exactQuoteKo,
            int startOffset,
            int endOffset
    ) implements ObjectiveEvidenceRef {
        public ObjectiveTextEvidenceRef {
            if (evidenceId == null || evidenceId.isBlank()
                    || (kind != ObjectiveEvidenceKind.TEXT_SPAN
                            && kind != ObjectiveEvidenceKind.TRANSCRIPT_SPAN)
                    || purpose == null || purpose.isBlank()
                    || sourceRole == null || sourceRole.isBlank()
                    || exactQuoteKo == null || exactQuoteKo.isBlank()
                    || startOffset < 0 || endOffset <= startOffset) {
                throw new IllegalArgumentException("Objective text evidence is invalid");
            }
        }
    }

    public record ObjectiveImageEvidenceRef(
            String evidenceId,
            ObjectiveEvidenceKind kind,
            String purpose,
            String sourceRole,
            String assetDigest,
            int imageIndex,
            String regionMode,
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height
    ) implements ObjectiveEvidenceRef {
        public ObjectiveImageEvidenceRef {
            if (evidenceId == null || evidenceId.isBlank()
                    || kind != ObjectiveEvidenceKind.IMAGE_REGION
                    || purpose == null || purpose.isBlank()
                    || sourceRole == null || sourceRole.isBlank()
                    || assetDigest == null || !assetDigest.matches("(?i)[0-9a-f]{64}")
                    || imageIndex < 0
                    || (!"WHOLE_IMAGE".equals(regionMode)
                            && !"RECTANGLE".equals(regionMode))) {
                throw new IllegalArgumentException("Objective image evidence is invalid");
            }
            if ("RECTANGLE".equals(regionMode)
                    && (x == null || y == null || width == null || height == null
                            || x.signum() < 0 || y.signum() < 0
                            || width.signum() <= 0 || height.signum() <= 0)) {
                throw new IllegalArgumentException(
                        "Objective image evidence rectangle is incomplete");
            }
            if ("WHOLE_IMAGE".equals(regionMode)) {
                if (x != null || y != null || width != null || height != null) {
                    throw new IllegalArgumentException(
                            "WHOLE_IMAGE evidence must not expose rectangle coordinates");
                }
            }
        }
    }

    public record ObjectiveEvidenceTranslation(
            String evidenceId,
            String label,
            String translationVi,
            String provenance
    ) {
        public ObjectiveEvidenceTranslation {
            if (evidenceId == null || evidenceId.isBlank()
                    || !"Dịch đoạn liên quan".equals(label)
                    || translationVi == null || translationVi.isBlank()
                    || provenance == null || provenance.isBlank()) {
                throw new IllegalArgumentException(
                        "Objective evidence translation contract is incomplete");
            }
        }
    }

    public record ObjectiveConstructDescriptor(
            String code,
            String labelVi,
            String labelKo,
            String registryVersion
    ) {
        public ObjectiveConstructDescriptor {
            if (code == null || code.isBlank()
                    || labelVi == null || labelVi.isBlank()
                    || labelKo == null || labelKo.isBlank()
                    || registryVersion == null || registryVersion.isBlank()) {
                throw new IllegalArgumentException(
                        "Objective construct descriptor is incomplete");
            }
        }
    }

    public record WritingDetailPayload(
            ResultFeedbackAvailability feedback,
            List<WritingTaskResult> tasks,
            Long activeQuestionId,
            List<ResultDetailScoreCriterion> scoreCriteria,
            String scoreProfileId,
            String diagnosticSeamId,
            String diagnosticSeamState,
            String diagnosticScopeNoteVi,
            String diagnosticScopeNoteKo,
            String diagnosticAvailability,
            String diagnosticAvailabilityNoteVi,
            String diagnosticAvailabilityNoteKo,
            List<WritingDiagnosticGroup> diagnosticGroups,
            WritingUpgradeView upgrade
    ) implements ResultDetailPayload {
        public WritingDetailPayload {
            if (feedback == null
                    || !"KSH_INTERNAL_TASK_NATIVE_V1".equals(scoreProfileId)
                    || !"ksh-writing-detail-diagnostics-seam-v1".equals(diagnosticSeamId)
                    || !"BOUNDED_CURRENT_EVIDENCE".equals(diagnosticSeamState)
                    || diagnosticScopeNoteVi == null || diagnosticScopeNoteVi.isBlank()
                    || diagnosticScopeNoteKo == null || diagnosticScopeNoteKo.isBlank()
                    || diagnosticAvailability == null
                    || !Set.of(
                            "AVAILABLE",
                            "NO_VALIDATED_EVIDENCE",
                            "BLANK_IDENTITY_UNAVAILABLE",
                            "FEEDBACK_UNAVAILABLE",
                            "CURRENT_EVIDENCE_CONTRACT_UNAVAILABLE",
                            "TASK_IDENTITY_UNAVAILABLE",
                            "NO_DETAIL_TASK").contains(diagnosticAvailability)
                    || diagnosticAvailabilityNoteVi == null || diagnosticAvailabilityNoteVi.isBlank()
                    || diagnosticAvailabilityNoteKo == null || diagnosticAvailabilityNoteKo.isBlank()) {
                throw new IllegalArgumentException("Writing Result Detail contract is incomplete");
            }
            tasks = immutableResultList(tasks);
            scoreCriteria = immutableResultList(scoreCriteria);
            diagnosticGroups = immutableResultList(diagnosticGroups);
            List<WritingTaskResult> immutableTasks = tasks;
            if (activeQuestionId != null && immutableTasks.stream().noneMatch(task ->
                    activeQuestionId.equals(task.questionId()) && task.detailAvailable())) {
                throw new IllegalArgumentException(
                        "Writing Result Detail question selection is outside the immutable attempt");
            }
            if (scoreCriteria.stream().anyMatch(criterion ->
                    criterion.questionId() == null || immutableTasks.stream().noneMatch(task ->
                            criterion.questionId().equals(task.questionId())
                                    && task.detailAvailable()))) {
                throw new IllegalArgumentException(
                        "Writing score criteria must belong to a detail-capable immutable task");
            }
            if (diagnosticGroups.stream()
                    .flatMap(group -> java.util.stream.Stream.concat(
                            group.strengths().stream(),
                            group.needsImprovement().stream()))
                    .anyMatch(finding ->
                    activeQuestionId == null
                            || finding.questionId() == null
                            || !activeQuestionId.equals(finding.questionId()))) {
                throw new IllegalArgumentException(
                        "Writing diagnostics must belong only to the selected immutable task");
            }
            if (activeQuestionId == null && upgrade != null) {
                throw new IllegalArgumentException(
                        "Writing upgrade provenance cannot exist without a selected task");
            }
            if (activeQuestionId != null
                    && (upgrade == null || !activeQuestionId.equals(upgrade.questionId()))) {
                throw new IllegalArgumentException(
                        "Writing upgrade provenance must belong to the selected immutable task");
            }
            if (upgrade != null) {
                WritingTaskResult selected = immutableTasks.stream()
                        .filter(task -> activeQuestionId.equals(task.questionId()))
                        .findFirst()
                        .orElseThrow();
                String learnerAnswer = selected.learnerAnswer() == null
                        ? ""
                        : selected.learnerAnswer();
                if (upgrade.significantRewrites().stream().anyMatch(rewrite ->
                        rewrite.original() == null
                                || rewrite.original().isBlank()
                                || !learnerAnswer.contains(rewrite.original()))) {
                    throw new IllegalArgumentException(
                            "Writing rewrites must preserve an exact selected learner span");
                }
            }
        }

        public List<WritingDiagnosticFinding> diagnosticFindings() {
            return diagnosticGroups.stream()
                    .flatMap(group -> java.util.stream.Stream.concat(
                            group.strengths().stream(),
                            group.needsImprovement().stream()))
                    .toList();
        }

        public List<WritingDiagnosticChip> filterChips() {
            return diagnosticGroups.stream()
                    .flatMap(group -> java.util.stream.Stream.concat(
                            group.strengthChips().stream(),
                            group.needsImprovementChips().stream()))
                    .toList();
        }

        public boolean hasStrengthFindings() {
            return diagnosticGroups.stream()
                    .anyMatch(WritingDiagnosticGroup::hasStrengths);
        }

        public boolean hasNeedsImprovementFindings() {
            return diagnosticGroups.stream()
                    .anyMatch(WritingDiagnosticGroup::hasNeedsImprovement);
        }

        @Override
        public ResultDetailScreenKind screenKind() {
            return ResultDetailScreenKind.WRITING_DETAIL;
        }
    }

    public enum WritingDiagnosticTargetKind {
        WHOLE_ANSWER,
        BLANK
    }

    public record WritingDiagnosticTarget(
            WritingDiagnosticTargetKind kind,
            String blankId,
            Integer blankIndex
    ) {
        public WritingDiagnosticTarget {
            if (kind == null) {
                throw new IllegalArgumentException("Writing diagnostic target kind is required");
            }
            if (kind == WritingDiagnosticTargetKind.WHOLE_ANSWER
                    && (blankId != null || blankIndex != null)) {
                throw new IllegalArgumentException(
                        "Whole-answer diagnostics cannot fabricate a blank target");
            }
            if (kind == WritingDiagnosticTargetKind.BLANK
                    && (blankId == null || blankId.isBlank()
                    || blankIndex == null || blankIndex < 1)) {
                throw new IllegalArgumentException(
                        "Blank diagnostics require an authoritative blank identity");
            }
        }
    }

    public record WritingDiagnosticFinding(
            Long questionId,
            String findingId,
            String categoryCode,
            String categoryLabelVi,
            String categoryLabelKo,
            int categoryOrder,
            String featureCode,
            String featureLabelVi,
            String featureLabelKo,
            int featureOrder,
            ResultDetailPolarity polarity,
            String parentCriterionId,
            String scoreEffect,
            String applicability,
            WritingDiagnosticTarget target,
            String evidenceAvailability,
            String evidenceScope,
            String evidence,
            String explanationVi,
            String correctionKo,
            String impact,
            String frequency,
            String confidence,
            String observability
    ) {
        public WritingDiagnosticFinding {
            if (questionId == null
                    || findingId == null || findingId.isBlank()
                    || categoryCode == null
                    || !Set.of(
                            "TASK_CONTENT",
                            "DISCOURSE",
                            "MORPHOSYNTAX",
                            "LEXICO_SEMANTIC",
                            "SOCIOLINGUISTIC_PRAGMATIC",
                            "ORTHOGRAPHY",
                            "LENGTH_FORMAT").contains(categoryCode)
                    || categoryLabelVi == null || categoryLabelVi.isBlank()
                    || categoryLabelKo == null || categoryLabelKo.isBlank()
                    || categoryOrder <= 0
                    || featureCode == null || featureCode.isBlank()
                    || featureLabelVi == null || featureLabelVi.isBlank()
                    || featureLabelKo == null || featureLabelKo.isBlank()
                    || featureOrder <= 0 || polarity == null
                    || scoreEffect == null || scoreEffect.isBlank()
                    || applicability == null || applicability.isBlank()
                    || target == null
                    || evidenceAvailability == null
                    || !Set.of(
                            "EXACT_TEXT_AVAILABLE",
                            "WHOLE_ANSWER_AVAILABLE").contains(evidenceAvailability)
                    || evidenceScope == null
                    || !Set.of("TEXT_SPAN", "WHOLE_ANSWER").contains(evidenceScope)
                    || explanationVi == null || explanationVi.isBlank()) {
                throw new IllegalArgumentException(
                        "Writing diagnostic finding is incomplete");
            }
            if ("PARENT_LINKED".equals(scoreEffect)
                    && (parentCriterionId == null || parentCriterionId.isBlank())) {
                throw new IllegalArgumentException(
                        "Parent-linked Writing diagnostics require score authority");
            }
            if ("DIAGNOSTIC_ONLY".equals(scoreEffect)
                    && parentCriterionId != null) {
                throw new IllegalArgumentException(
                        "Diagnostic-only Writing findings cannot own a score parent");
            }
            if (!"PARENT_LINKED".equals(scoreEffect)
                    && !"DIAGNOSTIC_ONLY".equals(scoreEffect)) {
                throw new IllegalArgumentException(
                        "Unknown Writing diagnostic score effect");
            }
            if ("TEXT_SPAN".equals(evidenceScope)
                    && (evidence == null || evidence.isBlank())) {
                throw new IllegalArgumentException(
                        "Text-span Writing diagnostics require exact evidence");
            }
            if ("WHOLE_ANSWER".equals(evidenceScope)
                    && evidence != null && !evidence.isEmpty()) {
                throw new IllegalArgumentException(
                        "Whole-answer Writing diagnostics cannot fake a highlight");
            }
            if (("TEXT_SPAN".equals(evidenceScope)
                    && !"EXACT_TEXT_AVAILABLE".equals(evidenceAvailability))
                    || ("WHOLE_ANSWER".equals(evidenceScope)
                    && !"WHOLE_ANSWER_AVAILABLE".equals(evidenceAvailability))) {
                throw new IllegalArgumentException(
                        "Writing evidence scope and availability are inconsistent");
            }
        }
    }

    public record WritingDiagnosticChip(
            String id,
            String labelVi,
            String labelKo,
            ResultDetailPolarity polarity,
            String parentCriterionId,
            String scoreEffect,
            String applicability,
            int stableOrder,
            int count,
            boolean countedSeparately,
            String evidenceAvailability
    ) {
        public WritingDiagnosticChip {
            if (id == null || id.isBlank()
                    || labelVi == null || labelVi.isBlank()
                    || labelKo == null || labelKo.isBlank()
                    || polarity == null
                    || scoreEffect == null || scoreEffect.isBlank()
                    || applicability == null || applicability.isBlank()
                    || stableOrder <= 0 || count <= 0
                    || evidenceAvailability == null || evidenceAvailability.isBlank()) {
                throw new IllegalArgumentException(
                        "Writing diagnostic chip is incomplete");
            }
            if (countedSeparately) {
                throw new IllegalArgumentException(
                        "Writing diagnostic chip counts are non-additive navigation metadata");
            }
            if (scoreEffect == null
                    || !Set.of("PARENT_LINKED", "DIAGNOSTIC_ONLY").contains(scoreEffect)) {
                throw new IllegalArgumentException(
                        "Unknown Writing diagnostic chip score effect");
            }
            if (evidenceAvailability == null
                    || !Set.of(
                    "EXACT_TEXT_AVAILABLE",
                    "WHOLE_ANSWER_AVAILABLE",
                    "MIXED_EVIDENCE_AVAILABLE").contains(evidenceAvailability)) {
                throw new IllegalArgumentException(
                        "Unknown Writing diagnostic chip evidence availability");
            }
            if ("PARENT_LINKED".equals(scoreEffect)
                    && (parentCriterionId == null || parentCriterionId.isBlank())) {
                throw new IllegalArgumentException(
                        "Parent-linked Writing chips require score authority");
            }
            if ("DIAGNOSTIC_ONLY".equals(scoreEffect) && parentCriterionId != null) {
                throw new IllegalArgumentException(
                        "Diagnostic-only Writing chips cannot own a score parent");
            }
        }
    }

    public record WritingDiagnosticGroup(
            String categoryCode,
            String labelVi,
            String labelKo,
            int stableOrder,
            List<WritingDiagnosticFinding> strengths,
            List<WritingDiagnosticFinding> needsImprovement,
            List<WritingDiagnosticChip> strengthChips,
            List<WritingDiagnosticChip> needsImprovementChips
    ) {
        public WritingDiagnosticGroup {
            if (categoryCode == null
                    || !Set.of(
                    "TASK_CONTENT",
                    "DISCOURSE",
                    "MORPHOSYNTAX",
                    "LEXICO_SEMANTIC",
                    "SOCIOLINGUISTIC_PRAGMATIC",
                    "ORTHOGRAPHY",
                    "LENGTH_FORMAT").contains(categoryCode)
                    || labelVi == null || labelVi.isBlank()
                    || labelKo == null || labelKo.isBlank()
                    || stableOrder <= 0) {
                throw new IllegalArgumentException(
                        "Writing diagnostic group is incomplete");
            }
            strengths = immutableResultList(strengths);
            needsImprovement = immutableResultList(needsImprovement);
            strengthChips = immutableResultList(strengthChips);
            needsImprovementChips = immutableResultList(needsImprovementChips);
            if (strengths.stream().anyMatch(finding ->
                    finding.polarity() != ResultDetailPolarity.STRENGTH
                            || !categoryCode.equals(finding.categoryCode()))
                    || needsImprovement.stream().anyMatch(finding ->
                    finding.polarity() != ResultDetailPolarity.NEEDS_IMPROVEMENT
                            || !categoryCode.equals(finding.categoryCode()))
                    || strengthChips.stream().anyMatch(chip ->
                    chip.polarity() != ResultDetailPolarity.STRENGTH)
                    || needsImprovementChips.stream().anyMatch(chip ->
                    chip.polarity() != ResultDetailPolarity.NEEDS_IMPROVEMENT)) {
                throw new IllegalArgumentException(
                        "Writing diagnostic group polarity/category is inconsistent");
            }
        }

        public boolean hasStrengths() {
            return !strengths.isEmpty();
        }

        public boolean hasNeedsImprovement() {
            return !needsImprovement.isEmpty();
        }
    }

    public record WritingAnswerArtifact(
            String content,
            String availability,
            String provenance,
            String labelVi,
            String labelKo
    ) {
        public WritingAnswerArtifact {
            content = content == null ? "" : content;
            if (availability == null
                    || !Set.of(
                    "AVAILABLE",
                    "UNAVAILABLE").contains(availability)
                    || provenance == null
                    || !Set.of(
                    "LEARNER_SUBMISSION_DERIVED_EVALUATOR_OUTPUT",
                    "EVALUATOR_GENERATED_NOT_TEACHER_REFERENCE").contains(provenance)
                    || labelVi == null || labelVi.isBlank()
                    || labelKo == null || labelKo.isBlank()) {
                throw new IllegalArgumentException(
                        "Writing answer provenance is incomplete");
            }
            if ("AVAILABLE".equals(availability) && content.isBlank()) {
                throw new IllegalArgumentException(
                        "Available Writing answer provenance requires content");
            }
            if (!"AVAILABLE".equals(availability) && !content.isEmpty()) {
                throw new IllegalArgumentException(
                        "Unavailable Writing answer provenance cannot expose content");
            }
        }

        public boolean available() {
            return "AVAILABLE".equals(availability);
        }
    }

    public record WritingUpgradeView(
            Long questionId,
            WritingAnswerArtifact learnerDerivedUpgrade,
            List<WritingSentenceRewriteView> significantRewrites,
            WritingAnswerArtifact evaluatorSample
    ) {
        public WritingUpgradeView {
            if (questionId == null || learnerDerivedUpgrade == null
                    || evaluatorSample == null) {
                throw new IllegalArgumentException(
                        "Writing upgrade provenance is incomplete");
            }
            significantRewrites = immutableResultList(significantRewrites);
            if (significantRewrites.stream().anyMatch(rewrite ->
                    rewrite.original() == null || rewrite.original().isBlank()
                            || rewrite.upgraded() == null || rewrite.upgraded().isBlank()
                            || rewrite.reason() == null || rewrite.reason().isBlank())) {
                throw new IllegalArgumentException(
                        "Writing significant rewrite is incomplete");
            }
        }
    }

    public record SpeakingDetailPayload(
            ResultFeedbackAvailability feedback,
            List<SpeakingTaskDetail> tasks,
            Long activeQuestionId,
            String scoreProfileId,
            String profileState,
            String evidenceMode,
            String evaluatorCapability,
            String evidenceNote,
            String taskScoreState,
            List<ResultDetailScoreCriterion> scoreCriteria,
            SpeakingEvidenceView evidence,
            String diagnosticAvailability,
            String diagnosticScopeNoteVi,
            String diagnosticScopeNoteKo,
            String diagnosticAvailabilityNoteVi,
            String diagnosticAvailabilityNoteKo,
            List<SpeakingDiagnosticGroup> diagnosticGroups,
            SpeakingUpgradeView upgrade
    ) implements ResultDetailPayload {
        public SpeakingDetailPayload {
            if (feedback == null
                    || scoreProfileId == null || scoreProfileId.isBlank()
                    || profileState == null || profileState.isBlank()
                    || evidenceMode == null || evidenceMode.isBlank()
                    || evaluatorCapability == null || evaluatorCapability.isBlank()
                    || evidenceNote == null || evidenceNote.isBlank()
                    || taskScoreState == null || taskScoreState.isBlank()
                    || diagnosticAvailability == null || diagnosticAvailability.isBlank()
                    || diagnosticScopeNoteVi == null || diagnosticScopeNoteVi.isBlank()
                    || diagnosticScopeNoteKo == null || diagnosticScopeNoteKo.isBlank()
                    || diagnosticAvailabilityNoteVi == null
                    || diagnosticAvailabilityNoteVi.isBlank()
                    || diagnosticAvailabilityNoteKo == null
                    || diagnosticAvailabilityNoteKo.isBlank()) {
                throw new IllegalArgumentException(
                        "Speaking Result Detail contract is incomplete");
            }
            tasks = immutableResultList(tasks);
            scoreCriteria = immutableResultList(scoreCriteria);
            diagnosticGroups = immutableResultList(diagnosticGroups);
            List<ResultDetailDiagnosticFinding> allDiagnosticFindings =
                    diagnosticGroups.stream()
                            .flatMap(group -> java.util.stream.Stream.concat(
                                    group.strengths().stream(),
                                    group.needsImprovement().stream()))
                            .toList();
            List<ResultDetailFilterChip> allFilterChips = diagnosticGroups.stream()
                    .flatMap(group -> java.util.stream.Stream.concat(
                            group.strengthChips().stream(),
                            group.needsImprovementChips().stream()))
                    .toList();
            if (new LinkedHashSet<>(tasks.stream()
                    .map(SpeakingTaskDetail::questionId).toList()).size()
                    != tasks.size()
                    || new LinkedHashSet<>(tasks.stream()
                    .map(SpeakingTaskDetail::questionVersionId).toList()).size()
                    != tasks.size()) {
                throw new IllegalArgumentException(
                        "Speaking immutable task navigation must be unique");
            }
            if (activeQuestionId == null) {
                if (!scoreCriteria.isEmpty() || evidence != null || upgrade != null
                        || !diagnosticGroups.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Speaking detail artifacts require a selected immutable task");
                }
            }
            if (activeQuestionId != null && tasks.stream().noneMatch(task ->
                    activeQuestionId.equals(task.questionId()) && task.detailAvailable())) {
                throw new IllegalArgumentException(
                        "Speaking Result Detail question selection is outside the immutable attempt");
            }
            if (activeQuestionId != null && tasks.stream().anyMatch(task ->
                    !activeQuestionId.equals(task.questionId())
                            && (!task.prompt().isBlank()
                            || task.hasLearnerSubmissionText()
                            || !task.summary().isBlank()
                            || !"NAVIGATION_ONLY".equals(task.submissionState())
                            || !"NAVIGATION_ONLY".equals(task.evaluationState())))) {
                throw new IllegalArgumentException(
                        "Non-selected Speaking tasks may expose navigation identity only");
            }
            if (activeQuestionId != null && scoreCriteria.size() != 6) {
                throw new IllegalArgumentException(
                        "Speaking Result Detail must preserve all six criterion states");
            }
            List<String> expected = List.of(
                    "S_CONTENT_TASK_FULFILLMENT",
                    "S_GRAMMAR_SENTENCE_CONTROL",
                    "S_VOCABULARY_EXPRESSIONS",
                    "S_COHERENCE_ORGANIZATION",
                    "S_FLUENCY",
                    "S_PRONUNCIATION_DELIVERY");
            if (activeQuestionId != null
                    && !scoreCriteria.stream()
                    .map(ResultDetailScoreCriterion::criterionId).toList()
                    .equals(expected)) {
                throw new IllegalArgumentException(
                        "Speaking Result Detail criterion order is not canonical");
            }
            if (activeQuestionId != null && scoreCriteria.stream().anyMatch(criterion ->
                    !activeQuestionId.equals(criterion.questionId()))) {
                throw new IllegalArgumentException(
                        "Speaking score criteria must belong to the selected immutable task");
            }
            if (activeQuestionId != null
                    && (evidence == null || !activeQuestionId.equals(evidence.questionId())
                    || upgrade == null || !activeQuestionId.equals(upgrade.questionId()))) {
                throw new IllegalArgumentException(
                        "Speaking evidence and upgrade must belong to the selected immutable task");
            }
            if (activeQuestionId != null
                    && !"DIRECT_AUDIO_AND_TRANSCRIPT".equals(evidenceMode)) {
                if (!"NOT_SCORABLE".equals(
                        evidence.acousticEvidenceAvailability())) {
                    throw new IllegalArgumentException(
                            "Speaking without direct-audio scoring must keep acoustic evidence NOT_SCORABLE");
                }
                for (int index = 4; index < 6; index++) {
                    ResultDetailScoreCriterion acoustic = scoreCriteria.get(index);
                    if (!Set.of("NOT_SCORABLE", "LEGACY_UNVERIFIED", "UNAVAILABLE")
                            .contains(acoustic.availability())
                            || acoustic.score() != null || acoustic.maxScore() != null) {
                        throw new IllegalArgumentException(
                                "Speaking acoustic criteria require governed direct-audio evidence");
                    }
                }
            }
            if (activeQuestionId != null && "TRANSCRIPT_ONLY".equals(evidenceMode)) {
                for (int index = 4; index < 6; index++) {
                    if (!"NOT_SCORABLE".equals(scoreCriteria.get(index).availability())) {
                        throw new IllegalArgumentException(
                                "Transcript-only Speaking acoustic criteria must be NOT_SCORABLE");
                    }
                }
            }
            if (activeQuestionId != null
                    && allDiagnosticFindings.stream().anyMatch(finding ->
                    !activeQuestionId.equals(finding.questionId()))) {
                throw new IllegalArgumentException(
                        "Speaking diagnostics must belong only to the selected immutable task");
            }
            if (activeQuestionId != null
                    && !"DIRECT_AUDIO_AND_TRANSCRIPT".equals(evidenceMode)
                    && allFilterChips.stream().anyMatch(chip ->
                    chip.parentCriterionId().equals("S_FLUENCY")
                            || chip.parentCriterionId()
                            .equals("S_PRONUNCIATION_DELIVERY"))) {
                throw new IllegalArgumentException(
                        "Speaking without direct-audio scoring cannot expose acoustic diagnostic chips");
            }
            if (activeQuestionId != null
                    && upgrade.significantRewrites().stream().anyMatch(rewrite ->
                    evidence.transcriptText().isBlank()
                            || !evidence.transcriptText().contains(rewrite.original()))) {
                throw new IllegalArgumentException(
                        "Speaking rewrites must preserve an exact authoritative transcript span");
            }
            if (activeQuestionId != null
                    && !evidence.transcriptAvailable()
                    && (upgrade.learnerDerivedUpgrade().available()
                    || upgrade.evaluatorSample().available())) {
                throw new IllegalArgumentException(
                        "Speaking upgrade artifacts require an authoritative transcript");
            }
        }

        public List<ResultDetailDiagnosticFinding> diagnosticFindings() {
            return diagnosticGroups.stream()
                    .flatMap(group -> java.util.stream.Stream.concat(
                            group.strengths().stream(),
                            group.needsImprovement().stream()))
                    .toList();
        }

        public List<ResultDetailFilterChip> filterChips() {
            return diagnosticGroups.stream()
                    .flatMap(group -> java.util.stream.Stream.concat(
                            group.strengthChips().stream(),
                            group.needsImprovementChips().stream()))
                    .toList();
        }

        public boolean hasStrengthFindings() {
            return diagnosticGroups.stream().anyMatch(SpeakingDiagnosticGroup::hasStrengths);
        }

        public boolean hasNeedsImprovementFindings() {
            return diagnosticGroups.stream()
                    .anyMatch(SpeakingDiagnosticGroup::hasNeedsImprovement);
        }

        public String profileStateLabelVi() {
            return switch (profileState) {
                case "READY" -> "Hồ sơ đã sẵn sàng";
                case "LOW_CONFIDENCE" -> "Bản chép lời có độ tin cậy thấp";
                case "PENDING" -> "Bằng chứng đang được xử lý";
                case "LEGACY_UNVERIFIED" -> "Dữ liệu tương thích cũ chưa được xác minh";
                case "FAILED" -> "Chưa thể tạo hồ sơ";
                default -> "Chưa có hồ sơ khả dụng";
            };
        }

        public String profileStateLabelKo() {
            return switch (profileState) {
                case "READY" -> "프로필 준비 완료";
                case "LOW_CONFIDENCE" -> "전사 신뢰도 낮음";
                case "PENDING" -> "근거 처리 중";
                case "LEGACY_UNVERIFIED" -> "이전 호환 데이터 미검증";
                case "FAILED" -> "프로필 생성 불가";
                default -> "사용 가능한 프로필 없음";
            };
        }

        public String evidenceModeLabelVi() {
            return switch (evidenceMode) {
                case "TRANSCRIPT_ONLY" -> "Chỉ dựa trên bản chép lời";
                case "RECORDING_SOURCE_ONLY" -> "Chỉ xác nhận nguồn bản ghi";
                case "LEGACY_ESSAY_TEXT_COMPATIBILITY" ->
                        "Văn bản tương thích từ dữ liệu Nói cũ";
                case "DIRECT_AUDIO_AND_TRANSCRIPT" ->
                        "Âm thanh trực tiếp và bản chép lời";
                default -> "Chưa xác định được nguồn bằng chứng";
            };
        }

        public String evidenceModeLabelKo() {
            return switch (evidenceMode) {
                case "TRANSCRIPT_ONLY" -> "전사문 기반 평가";
                case "RECORDING_SOURCE_ONLY" -> "녹음 출처만 확인";
                case "LEGACY_ESSAY_TEXT_COMPATIBILITY" ->
                        "이전 말하기 데이터의 텍스트 호환 모드";
                case "DIRECT_AUDIO_AND_TRANSCRIPT" -> "직접 음성과 전사문";
                default -> "근거 출처 미확인";
            };
        }

        public String taskScoreStateLabelVi() {
            return switch (taskScoreState) {
                case "LANGUAGE_CRITERIA_AVAILABLE_NO_TASK_TOTAL" ->
                        "Có điểm theo tiêu chí ngôn ngữ; không tạo điểm tổng nhiệm vụ";
                case "PENDING" -> "Chưa có điểm khi bằng chứng đang xử lý";
                case "LEGACY_UNVERIFIED" -> "Điểm cũ không đủ điều kiện hiển thị";
                default -> "Chưa có điểm nhiệm vụ khả dụng";
            };
        }

        public String taskScoreStateLabelKo() {
            return switch (taskScoreState) {
                case "LANGUAGE_CRITERIA_AVAILABLE_NO_TASK_TOTAL" ->
                        "언어 기준별 점수만 제공하며 과제 총점은 산출하지 않음";
                case "PENDING" -> "근거 처리 중에는 점수를 제공하지 않음";
                case "LEGACY_UNVERIFIED" -> "이전 점수는 표시 조건을 충족하지 않음";
                default -> "사용 가능한 과제 점수 없음";
            };
        }

        public String evaluatorCapabilityLabelVi() {
            return "TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION".equals(evaluatorCapability)
                    ? "Bộ đánh giá chỉ nhận văn bản chép lời, không nghe bản ghi"
                    : "Chưa có bộ đánh giá âm thanh trực tiếp đã được phê duyệt";
        }

        public String evaluatorCapabilityLabelKo() {
            return "TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION".equals(evaluatorCapability)
                    ? "평가기는 전사문만 사용하며 녹음을 직접 듣지 않음"
                    : "승인된 직접 음성 평가기 없음";
        }

        @Override
        public ResultDetailScreenKind screenKind() {
            return ResultDetailScreenKind.SPEAKING_DETAIL;
        }
    }

    public record SpeakingTaskDetail(
            Long questionId,
            Long questionVersionId,
            Integer questionNo,
            String questionType,
            String compatibilityMode,
            String prompt,
            String learnerSubmissionText,
            String submissionState,
            String evaluationState,
            String summary
    ) {
        public SpeakingTaskDetail {
            learnerSubmissionText = blankResultText(learnerSubmissionText);
            summary = blankResultText(summary);
            if (questionId == null || questionVersionId == null || questionNo == null
                    || questionType == null
                    || !Set.of("SPEAKING", "ESSAY").contains(questionType)
                    || compatibilityMode == null
                    || !Set.of(
                    "CANONICAL_SPEAKING",
                    "LEGACY_ESSAY_COMPATIBILITY").contains(compatibilityMode)
                    || prompt == null
                    || submissionState == null || submissionState.isBlank()
                    || evaluationState == null || evaluationState.isBlank()
                    || "AUDIO_SUBMITTED".equalsIgnoreCase(learnerSubmissionText.trim())) {
                throw new IllegalArgumentException(
                        "Speaking immutable task identity/provenance is incomplete");
            }
            if ("SPEAKING".equals(questionType)
                    != "CANONICAL_SPEAKING".equals(compatibilityMode)) {
                throw new IllegalArgumentException(
                        "Speaking task compatibility mode does not match immutable type");
            }
        }

        public boolean detailAvailable() {
            return true;
        }

        public boolean canonicalSpeaking() {
            return "CANONICAL_SPEAKING".equals(compatibilityMode);
        }

        public boolean hasLearnerSubmissionText() {
            return !learnerSubmissionText.isBlank();
        }

        public String taskLabelVi() {
            return (canonicalSpeaking() ? "Câu Nói " : "Câu Nói cũ ")
                    + questionNo;
        }

        public String taskLabelKo() {
            return (canonicalSpeaking() ? "말하기 " : "이전 말하기 ")
                    + questionNo + "번";
        }

        public String compatibilityLabelVi() {
            return canonicalSpeaking()
                    ? "Nhiệm vụ Nói chuẩn hiện hành"
                    : "Dữ liệu Nói cũ lưu theo dạng bài tự luận";
        }

        public String compatibilityLabelKo() {
            return canonicalSpeaking()
                    ? "현재 표준 말하기 과제"
                    : "서술형으로 저장된 이전 말하기 데이터";
        }

        public String submissionStateLabelVi() {
            return switch (submissionState) {
                case "AUDIO_SOURCE_WITH_AUTHORITATIVE_TRANSCRIPT" ->
                        "Đã nộp bản ghi và có bản chép lời đủ thẩm quyền";
                case "AUDIO_SOURCE_TRANSCRIPT_UNAVAILABLE" ->
                        "Đã nộp bản ghi; chưa có bản chép lời đủ thẩm quyền";
                case "TEXT_COMPATIBILITY" ->
                        "Nội dung văn bản tương thích, không phải bằng chứng âm thanh";
                case "LEGACY_ESSAY_TEXT_COMPATIBILITY" ->
                        "Văn bản Nói cũ lưu theo dạng bài tự luận";
                default -> "Chưa có câu trả lời đã nộp";
            };
        }

        public String submissionStateLabelKo() {
            return switch (submissionState) {
                case "AUDIO_SOURCE_WITH_AUTHORITATIVE_TRANSCRIPT" ->
                        "녹음 제출 완료 · 권한 있는 전사문 있음";
                case "AUDIO_SOURCE_TRANSCRIPT_UNAVAILABLE" ->
                        "녹음 제출 완료 · 권한 있는 전사문 없음";
                case "TEXT_COMPATIBILITY" ->
                        "텍스트 호환 내용 · 음성 근거 아님";
                case "LEGACY_ESSAY_TEXT_COMPATIBILITY" ->
                        "서술형으로 저장된 이전 말하기 텍스트";
                default -> "제출된 답변 없음";
            };
        }

        public String evaluationStateLabelVi() {
            return switch (evaluationState) {
                case "READY" -> "Phản hồi đã sẵn sàng";
                case "LOW_CONFIDENCE" -> "Bản chép lời có độ tin cậy thấp";
                case "PENDING" -> "Đang xử lý bằng chứng";
                case "LEGACY_UNVERIFIED" -> "Dữ liệu cũ chưa được xác minh";
                case "FAILED" -> "Xử lý phản hồi không thành công";
                default -> "Chưa có phản hồi";
            };
        }

        public String evaluationStateLabelKo() {
            return switch (evaluationState) {
                case "READY" -> "피드백 준비 완료";
                case "LOW_CONFIDENCE" -> "전사 신뢰도 낮음";
                case "PENDING" -> "근거 처리 중";
                case "LEGACY_UNVERIFIED" -> "이전 데이터 미검증";
                case "FAILED" -> "피드백 처리 실패";
                default -> "피드백 없음";
            };
        }
    }

    public record SpeakingEvidenceView(
            Long questionId,
            String transcriptText,
            String transcriptAvailability,
            String transcriptSource,
            String transcriptMediaBinding,
            String recordingState,
            Long mediaId,
            Long durationMs,
            Long byteSize,
            String mimeType,
            String playbackPath,
            boolean playbackAvailable,
            String acousticEvidenceAvailability
    ) {
        public SpeakingEvidenceView {
            transcriptText = blankResultText(transcriptText);
            playbackPath = blankResultText(playbackPath);
            mimeType = blankResultText(mimeType);
            if (questionId == null
                    || transcriptAvailability == null
                    || !Set.of("AVAILABLE", "UNAVAILABLE")
                    .contains(transcriptAvailability)
                    || transcriptSource == null
                    || !Set.of(
                    "CURRENT_AUTHORITATIVE_TRANSCRIPT",
                    "UNAVAILABLE").contains(transcriptSource)
                    || transcriptMediaBinding == null
                    || !Set.of(
                    "MATCHED_CURRENT_EVALUATION",
                    "UNVERIFIED",
                    "NOT_APPLICABLE").contains(transcriptMediaBinding)
                    || recordingState == null
                    || !Set.of(
                    "READY_OWNER_BOUND_RECORDING",
                    "SUBMISSION_MARKER_ONLY",
                    "UNAVAILABLE").contains(recordingState)
                    || acousticEvidenceAvailability == null
                    || !Set.of(
                    "NOT_SCORABLE",
                    "AVAILABLE_GOVERNED_DIRECT_AUDIO").contains(
                    acousticEvidenceAvailability)
                    || "AUDIO_SUBMITTED".equalsIgnoreCase(transcriptText.trim())) {
                throw new IllegalArgumentException(
                        "Speaking evidence provenance is incomplete");
            }
            if ("AVAILABLE".equals(transcriptAvailability)
                    != !transcriptText.isBlank()
                    || ("AVAILABLE".equals(transcriptAvailability)
                    && !"CURRENT_AUTHORITATIVE_TRANSCRIPT".equals(transcriptSource))
                    || ("UNAVAILABLE".equals(transcriptAvailability)
                    && !"UNAVAILABLE".equals(transcriptSource))) {
                throw new IllegalArgumentException(
                        "Speaking transcript availability/source is inconsistent");
            }
            if (("AVAILABLE".equals(transcriptAvailability)
                    && "NOT_APPLICABLE".equals(transcriptMediaBinding))
                    || ("UNAVAILABLE".equals(transcriptAvailability)
                    && !"NOT_APPLICABLE".equals(transcriptMediaBinding))) {
                throw new IllegalArgumentException(
                        "Speaking transcript/media binding state is inconsistent");
            }
            if ("READY_OWNER_BOUND_RECORDING".equals(recordingState)
                    != (mediaId != null)) {
                throw new IllegalArgumentException(
                        "Speaking recording state requires owner-bound media identity");
            }
            if (playbackAvailable && (mediaId == null || playbackPath.isBlank())) {
                throw new IllegalArgumentException(
                        "Speaking playback requires an owner-bound recording path");
            }
            if (!playbackAvailable && !playbackPath.isBlank()) {
                throw new IllegalArgumentException(
                        "Disabled Speaking playback cannot expose a media path");
            }
        }

        public boolean transcriptAvailable() {
            return "AVAILABLE".equals(transcriptAvailability);
        }

        public boolean recordingAvailable() {
            return "READY_OWNER_BOUND_RECORDING".equals(recordingState);
        }

        public String transcriptSourceLabelVi() {
            return transcriptAvailable()
                    ? "Bản chép lời thuộc hợp đồng bằng chứng hiện tại"
                    : "Chưa có bản chép lời đủ thẩm quyền";
        }

        public String transcriptSourceLabelKo() {
            return transcriptAvailable()
                    ? "현재 근거 계약에 속한 전사문"
                    : "권한 있는 전사문 없음";
        }

        public String recordingStateLabelVi() {
            return switch (recordingState) {
                case "READY_OWNER_BOUND_RECORDING" ->
                        "Có bản ghi thuộc bài làm của người học";
                case "SUBMISSION_MARKER_ONLY" ->
                        "Chỉ có trạng thái đã nộp âm thanh; chưa có bản ghi phát lại";
                default -> "Không có bản ghi khả dụng";
            };
        }

        public String recordingStateLabelKo() {
            return switch (recordingState) {
                case "READY_OWNER_BOUND_RECORDING" ->
                        "학습자 제출에 귀속된 녹음 있음";
                case "SUBMISSION_MARKER_ONLY" ->
                        "음성 제출 상태만 있으며 재생 가능한 녹음 없음";
                default -> "사용 가능한 녹음 없음";
            };
        }

        public String transcriptMediaBindingLabelVi() {
            return switch (transcriptMediaBinding) {
                case "MATCHED_CURRENT_EVALUATION" ->
                        "Bản ghi khớp định danh media của lần tạo bản chép lời hiện tại";
                case "UNVERIFIED" ->
                        "Chưa chứng minh bản ghi phát lại là đúng media đã tạo bản chép lời";
                default -> "Chưa có bản chép lời để đối chiếu media";
            };
        }

        public String transcriptMediaBindingLabelKo() {
            return switch (transcriptMediaBinding) {
                case "MATCHED_CURRENT_EVALUATION" ->
                        "녹음이 현재 전사 생성의 미디어 식별자와 일치함";
                case "UNVERIFIED" ->
                        "재생 녹음과 전사 생성 미디어의 동일성이 확인되지 않음";
                default -> "미디어와 대조할 전사문 없음";
            };
        }

        public String acousticAvailabilityLabelVi() {
            return "AVAILABLE_GOVERNED_DIRECT_AUDIO".equals(acousticEvidenceAvailability)
                    ? "Có bằng chứng âm thanh trực tiếp đã được phê duyệt"
                    : "Chưa thể chấm âm học từ năng lực hiện tại";
        }

        public String acousticAvailabilityLabelKo() {
            return "AVAILABLE_GOVERNED_DIRECT_AUDIO".equals(acousticEvidenceAvailability)
                    ? "승인된 직접 음성 근거 있음"
                    : "현재 평가 역량으로 음향 채점 불가";
        }

        public String durationDisplay() {
            if (durationMs == null || durationMs < 0) {
                return null;
            }
            long seconds = durationMs / 1_000;
            return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        }

        public String byteSizeDisplay() {
            if (byteSize == null || byteSize < 0) {
                return null;
            }
            if (byteSize < 1_024) {
                return byteSize + " B";
            }
            return compactResultNumber(
                    BigDecimal.valueOf(byteSize)
                            .divide(BigDecimal.valueOf(1_024), 1, java.math.RoundingMode.HALF_UP))
                    + " KB";
        }
    }

    public record SpeakingDiagnosticGroup(
            String categoryCode,
            String labelVi,
            String labelKo,
            int stableOrder,
            List<ResultDetailDiagnosticFinding> strengths,
            List<ResultDetailDiagnosticFinding> needsImprovement,
            List<ResultDetailFilterChip> strengthChips,
            List<ResultDetailFilterChip> needsImprovementChips
    ) {
        public SpeakingDiagnosticGroup {
            if (categoryCode == null
                    || !Set.of(
                    "TASK_RESPONSE_RELEVANCE",
                    "DISCOURSE_ORGANIZATION",
                    "MORPHOSYNTAX",
                    "LEXICON_COLLOCATION",
                    "SOCIOLINGUISTIC_REGISTER_PRAGMATICS",
                    "FLUENCY_RHYTHM",
                    "PRONUNCIATION_ACOUSTICS").contains(categoryCode)
                    || labelVi == null || labelVi.isBlank()
                    || labelKo == null || labelKo.isBlank()
                    || stableOrder <= 0) {
                throw new IllegalArgumentException(
                        "Speaking diagnostic group is incomplete");
            }
            strengths = immutableResultList(strengths);
            needsImprovement = immutableResultList(needsImprovement);
            strengthChips = immutableResultList(strengthChips);
            needsImprovementChips = immutableResultList(needsImprovementChips);
            if (strengths.stream().anyMatch(finding ->
                    finding.polarity() != ResultDetailPolarity.STRENGTH)
                    || needsImprovement.stream().anyMatch(finding ->
                    finding.polarity() != ResultDetailPolarity.NEEDS_IMPROVEMENT)
                    || strengthChips.stream().anyMatch(chip ->
                    chip.polarity() != ResultDetailPolarity.STRENGTH)
                    || needsImprovementChips.stream().anyMatch(chip ->
                    chip.polarity() != ResultDetailPolarity.NEEDS_IMPROVEMENT)) {
                throw new IllegalArgumentException(
                        "Speaking diagnostic group polarity is inconsistent");
            }
            requireSpeakingChipCoverage(strengths, strengthChips);
            requireSpeakingChipCoverage(needsImprovement, needsImprovementChips);
        }

        public boolean hasStrengths() {
            return !strengths.isEmpty();
        }

        public boolean hasNeedsImprovement() {
            return !needsImprovement.isEmpty();
        }

        private static void requireSpeakingChipCoverage(
                List<ResultDetailDiagnosticFinding> findings,
                List<ResultDetailFilterChip> chips
        ) {
            Map<String, Long> expectedCounts = findings.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            ResultDetailDiagnosticFinding::descriptorId,
                            LinkedHashMap::new,
                            java.util.stream.Collectors.counting()));
            if (new LinkedHashSet<>(chips.stream()
                    .map(ResultDetailFilterChip::id).toList()).size() != chips.size()
                    || !expectedCounts.keySet().equals(chips.stream()
                    .map(ResultDetailFilterChip::id)
                    .collect(java.util.stream.Collectors.toSet()))
                    || chips.stream().anyMatch(chip ->
                    expectedCounts.getOrDefault(chip.id(), 0L) != chip.count()
                            || findings.stream()
                            .filter(finding ->
                                    chip.id().equals(finding.descriptorId()))
                            .anyMatch(finding ->
                                    !chip.parentCriterionId().equals(
                                            finding.parentCriterionId())
                                            || !chip.applicability().equals(
                                            finding.applicability())))) {
                throw new IllegalArgumentException(
                        "Speaking diagnostic chips must exactly count rendered findings");
            }
        }
    }

    public record SpeakingAnswerArtifact(
            String content,
            String availability,
            String provenance,
            String labelVi,
            String labelKo
    ) {
        public SpeakingAnswerArtifact {
            content = blankResultText(content);
            if (availability == null
                    || !Set.of("AVAILABLE", "UNAVAILABLE").contains(availability)
                    || provenance == null
                    || !Set.of(
                    "LEARNER_TRANSCRIPT_DERIVED_EVALUATOR_OUTPUT",
                    "EVALUATOR_GENERATED_NOT_TEACHER_REFERENCE").contains(provenance)
                    || labelVi == null || labelVi.isBlank()
                    || labelKo == null || labelKo.isBlank()) {
                throw new IllegalArgumentException(
                        "Speaking answer artifact provenance is incomplete");
            }
            if ("AVAILABLE".equals(availability) != !content.isBlank()) {
                throw new IllegalArgumentException(
                        "Speaking answer artifact availability/content is inconsistent");
            }
        }

        public boolean available() {
            return "AVAILABLE".equals(availability);
        }
    }

    public record SpeakingPhraseRewriteView(
            String original,
            String upgraded,
            String reason
    ) {
        public SpeakingPhraseRewriteView {
            if (original == null || original.isBlank()
                    || upgraded == null || upgraded.isBlank()
                    || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Speaking phrase rewrite is incomplete");
            }
        }
    }

    public record SpeakingUpgradeView(
            Long questionId,
            SpeakingAnswerArtifact learnerDerivedUpgrade,
            List<SpeakingPhraseRewriteView> significantRewrites,
            SpeakingAnswerArtifact evaluatorSample
    ) {
        public SpeakingUpgradeView {
            if (questionId == null || learnerDerivedUpgrade == null
                    || evaluatorSample == null) {
                throw new IllegalArgumentException(
                        "Speaking upgrade provenance is incomplete");
            }
            significantRewrites = immutableResultList(significantRewrites);
        }
    }

    public record ResultDetailScoreCriterion(
            Long questionId,
            String criterionId,
            String labelVi,
            String labelKo,
            BigDecimal score,
            BigDecimal maxScore,
            String availability,
            int stableOrder
    ) {
        public ResultDetailScoreCriterion {
            if (criterionId == null || criterionId.isBlank()
                    || labelVi == null || labelVi.isBlank()
                    || availability == null || availability.isBlank()
                    || stableOrder <= 0) {
                throw new IllegalArgumentException("Result Detail score criterion is incomplete");
            }
            if (!"SCORED".equals(availability)) {
                score = null;
                maxScore = null;
            }
        }

        public String scoreDisplay() {
            return score == null || maxScore == null
                    ? null
                    : compactResultNumber(score) + "/" + compactResultNumber(maxScore);
        }
    }

    public record ResultDetailDiagnosticFinding(
            Long questionId,
            String findingId,
            String descriptorId,
            ResultDetailPolarity polarity,
            String parentCriterionId,
            String applicability,
            String evidenceAvailability,
            String evidenceScope,
            String evidence,
            String explanationVi,
            String correctionKo
    ) {
        public ResultDetailDiagnosticFinding {
            if (findingId == null || findingId.isBlank()
                    || descriptorId == null || descriptorId.isBlank() || polarity == null
                    || parentCriterionId == null || parentCriterionId.isBlank()
                    || applicability == null || applicability.isBlank()
                    || evidenceAvailability == null || evidenceAvailability.isBlank()) {
                throw new IllegalArgumentException("Result Detail diagnostic finding is incomplete");
            }
        }
    }

    public record ResultDetailFilterChip(
            String id,
            String labelVi,
            String labelKo,
            ResultDetailPolarity polarity,
            String parentCriterionId,
            String applicability,
            int stableOrder,
            int count,
            boolean countedSeparately,
            String evidenceAvailability
    ) {
        public ResultDetailFilterChip {
            if (id == null || id.isBlank() || labelVi == null || labelVi.isBlank()
                    || polarity == null || parentCriterionId == null || parentCriterionId.isBlank()
                    || applicability == null || applicability.isBlank()
                    || stableOrder <= 0 || count <= 0
                    || evidenceAvailability == null || evidenceAvailability.isBlank()) {
                throw new IllegalArgumentException("Result Detail filter chip is incomplete");
            }
            if (countedSeparately) {
                throw new IllegalArgumentException(
                        "Diagnostic chip counts are navigation metadata, not separate scores");
            }
        }
    }

    // =========================================================================
    //  Canonical immutable-attempt result contract
    // =========================================================================

    public record PracticeAttemptResultView(
            ResultAttemptIdentity identity,
            ResultState state,
            ResultScoreSummary score,
            ResultAnswerDistribution answers,
            ResultFeedbackAvailability feedback,
            LocalDateTime startedAt,
            LocalDateTime submittedAt,
            Long elapsedSeconds,
            ResultSkillPayload payload
    ) {
        public PracticeAttemptResultView {
            if (identity == null || state == null || score == null || answers == null
                    || feedback == null || payload == null) {
                throw new IllegalArgumentException("Practice result envelope is incomplete");
            }
        }

        public String elapsedDisplay() {
            if (elapsedSeconds == null) {
                return null;
            }
            long minutes = elapsedSeconds / 60;
            long seconds = elapsedSeconds % 60;
            return minutes + " phút " + seconds + " giây";
        }
    }

    public record ResultAttemptIdentity(
            Long attemptId,
            Long publishedVersionId,
            Long setVersionId,
            Long testVersionId,
            Long sectionVersionId,
            Long setId,
            String setTitle,
            Long testId,
            String testTitle,
            Long sectionId,
            String sectionTitle,
            String skill,
            String skillLabel
    ) {
    }

    public record ResultState(String code, String label) {
    }

    public record ResultScoreSummary(
            BigDecimal value,
            BigDecimal earnedPoints,
            BigDecimal possiblePoints,
            BigDecimal percentage,
            String unit,
            String scaleLabel,
            String levelLabel
    ) {
        public boolean available() {
            return value != null || percentage != null || earnedPoints != null;
        }

        public String primaryDisplay() {
            BigDecimal display;
            if ("PERCENTAGE".equals(unit)) {
                display = percentage != null ? percentage : value;
            } else if ("EARNED_POINTS".equals(unit)) {
                display = earnedPoints != null ? earnedPoints : value;
            } else {
                display = value != null ? value : percentage;
            }
            return display == null ? null : compactResultNumber(display);
        }

        public String pointsDisplay() {
            if (earnedPoints == null || possiblePoints == null) {
                return null;
            }
            return compactResultNumber(earnedPoints) + "/" + compactResultNumber(possiblePoints);
        }

        public ResultScoreSummary unavailableView() {
            return new ResultScoreSummary(
                    null,
                    null,
                    null,
                    null,
                    unit,
                    scaleLabel,
                    null);
        }
    }

    public record ResultAnswerDistribution(
            int correct,
            int partial,
            int incorrect,
            int notAnswered,
            int pending,
            int unscorable,
            int total,
            int scoredDenominator
    ) {
        public String scoredLabel() {
            return scoredDenominator + "/" + total + " câu đã chấm";
        }
    }

    public record ResultFeedbackAvailability(
            String state,
            String label,
            int readyCount,
            int totalCount
    ) {
        public boolean ready() {
            return "READY".equals(state);
        }

        public String progressLabel(String noun) {
            return readyCount + "/" + totalCount + " " + noun;
        }

        public String stateLabelKo() {
            return switch (state == null ? "" : state) {
                case "READY" -> "평가 완료";
                case "PENDING" -> "평가 대기 중";
                case "FAILED" -> "평가 실패";
                case "PARTIAL" -> "일부 평가 가능";
                case "LOW_CONFIDENCE" -> "신뢰도 낮음";
                case "NOT_SCORABLE" -> "채점 불가";
                case "LEGACY_UNVERIFIED" -> "이전 데이터 확인 불가";
                case "UNAVAILABLE" -> "평가 불가";
                default -> "상태 확인 불가";
            };
        }
    }

    public sealed interface ResultSkillPayload
            permits ObjectiveResultPayload, WritingResultPayload, SpeakingResultPayload {
        String kind();
    }

    public record ObjectiveResultPayload(
            String kind,
            List<ObjectiveResultTypeBreakdown> breakdown
    ) implements ResultSkillPayload {
        public ObjectiveResultPayload {
            kind = "OBJECTIVE";
            breakdown = immutableResultList(breakdown);
        }

        public ObjectiveResultPayload(List<ObjectiveResultTypeBreakdown> breakdown) {
            this("OBJECTIVE", breakdown);
        }
    }

    public record ObjectiveResultTypeBreakdown(
            String questionType,
            String label,
            ResultAnswerDistribution answers,
            BigDecimal earnedPoints,
            BigDecimal possiblePoints,
            BigDecimal scoreRatePercentage
    ) {
        public String pointsDisplay() {
            if (earnedPoints == null || possiblePoints == null) {
                return null;
            }
            return compactResultNumber(earnedPoints) + "/" + compactResultNumber(possiblePoints);
        }

        public String scoreRateDisplay() {
            return scoreRatePercentage == null
                    ? null
                    : compactResultNumber(scoreRatePercentage) + "%";
        }
    }

    public record WritingResultPayload(
            String kind,
            List<WritingTaskResult> tasks
    ) implements ResultSkillPayload {
        public WritingResultPayload {
            kind = "WRITING";
            tasks = immutableResultList(tasks);
        }

        public WritingResultPayload(List<WritingTaskResult> tasks) {
            this("WRITING", tasks);
        }
    }

    public record WritingTaskResult(
            Long questionId,
            Long questionVersionId,
            Integer questionNo,
            String taskType,
            String taskLabel,
            String prompt,
            String learnerAnswer,
            ResultScoreSummary score,
            ResultFeedbackAvailability feedback,
            String summary,
            List<ResultRubricCriterion> officialCriteria,
            List<WritingAnalysisLens> analysisLenses,
            boolean detailAvailable
    ) {
        public WritingTaskResult {
            officialCriteria = immutableResultList(officialCriteria);
            analysisLenses = immutableResultList(analysisLenses);
        }

        public boolean answered() {
            return learnerAnswer != null && !learnerAnswer.isBlank();
        }

        public boolean evaluated() {
            return feedback != null && feedback.ready() && score != null && score.available();
        }

        public boolean clozeTask() {
            return "Q51".equals(taskType) || "Q52".equals(taskType);
        }
    }

    public record WritingAnalysisLens(
            String code,
            String label,
            String sourceCriterionId,
            List<String> evidence
    ) {
        public WritingAnalysisLens {
            evidence = immutableResultList(evidence);
        }
    }

    public record SpeakingResultPayload(
            String kind,
            ResultScoreSummary holisticScore,
            int coveredSegments,
            int totalSegments,
            String profileState,
            String evidenceMode,
            String evidenceNote,
            List<String> overallSummaries,
            List<String> strengths,
            List<String> needsImprovement,
            List<SpeakingActionPlanView> actionPlan,
            List<SpeakingCriterionResult> criteria,
            String evaluatorCapability,
            String evidenceContractVersion,
            String contractTrust,
            boolean holisticScoreAvailable,
            int legacyUnverifiedSegments
    ) implements ResultSkillPayload {
        public SpeakingResultPayload {
            kind = "SPEAKING";
            overallSummaries = immutableResultList(overallSummaries);
            strengths = immutableResultList(strengths);
            needsImprovement = immutableResultList(needsImprovement);
            actionPlan = immutableResultList(actionPlan);
            criteria = immutableResultList(criteria);
            profileState = switch (profileState == null ? "" : profileState) {
                case "READY", "PARTIAL", "PENDING", "FAILED", "UNAVAILABLE",
                        "LOW_CONFIDENCE", "LEGACY_UNVERIFIED" -> profileState;
                default -> "UNAVAILABLE";
            };
            boolean transcriptProfile = ("CURRENT_VERIFIED".equals(contractTrust)
                    || "MIXED_WITH_LEGACY_UNVERIFIED".equals(contractTrust))
                    && "TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION".equals(evaluatorCapability)
                    && "TRANSCRIPT_ONLY".equals(evidenceMode)
                    && com.ksh.features.practice.ai.speaking.SpeakingPromptRules
                            .EVIDENCE_CONTRACT_VERSION.equals(evidenceContractVersion);
            boolean governedFutureHolisticProfile = "CURRENT_VERIFIED".equals(contractTrust)
                    && holisticScoreAvailable
                    && holisticScore != null
                    && holisticScore.available()
                    && "DIRECT_AUDIO_AND_TRANSCRIPT".equals(evidenceMode)
                    && evaluatorCapability != null
                    && !"AUDIO_DIRECT_FULL_RESERVED".equals(evaluatorCapability)
                    && !"LEGACY_UNKNOWN".equals(evaluatorCapability)
                    && evidenceContractVersion != null
                    && !evidenceContractVersion.isBlank();
            if (!transcriptProfile && !governedFutureHolisticProfile) {
                contractTrust = "LEGACY_UNVERIFIED";
                evaluatorCapability = "LEGACY_UNKNOWN";
                evidenceMode = "UNKNOWN";
                evidenceContractVersion = null;
                holisticScoreAvailable = false;
                actionPlan = List.of();
                String unavailableCriterionState = legacyUnverifiedSegments > 0
                        ? "LEGACY_UNVERIFIED" : "UNAVAILABLE";
                criteria = criteria.stream()
                        .map(criterion -> criterion.unavailableView(unavailableCriterionState))
                        .toList();
                if (legacyUnverifiedSegments > 0) {
                    profileState = "LEGACY_UNVERIFIED";
                } else if (!"PENDING".equals(profileState)
                        && !"FAILED".equals(profileState)
                        && !"UNAVAILABLE".equals(profileState)) {
                    profileState = "UNAVAILABLE";
                }
            } else if (transcriptProfile) {
                // The current transcript capability cannot produce an overall
                // Speaking score, even if a stale caller supplies one.
                holisticScoreAvailable = false;
            }
            if (!holisticScoreAvailable && holisticScore != null) {
                holisticScore = holisticScore.unavailableView();
            }
        }

        public SpeakingResultPayload(
                ResultScoreSummary holisticScore,
                int coveredSegments,
                int totalSegments,
                String evidenceMode,
                String evidenceNote,
                List<String> overallSummaries,
                List<String> strengths,
                List<String> needsImprovement,
                List<SpeakingActionPlanView> actionPlan,
                List<SpeakingCriterionResult> criteria) {
            this("SPEAKING", holisticScore, coveredSegments, totalSegments,
                    defaultSpeakingProfileState(coveredSegments, totalSegments), evidenceMode,
                    evidenceNote, overallSummaries, strengths, needsImprovement, actionPlan, criteria,
                    "TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                    com.ksh.features.practice.ai.speaking.SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                    "CURRENT_VERIFIED", false, 0);
        }

        public SpeakingResultPayload(
                ResultScoreSummary holisticScore,
                int coveredSegments,
                int totalSegments,
                String profileState,
                String evidenceMode,
                String evidenceNote,
                List<String> overallSummaries,
                List<String> strengths,
                List<String> needsImprovement,
                List<SpeakingActionPlanView> actionPlan,
                List<SpeakingCriterionResult> criteria,
                String evaluatorCapability,
                String evidenceContractVersion,
                String contractTrust,
                boolean holisticScoreAvailable,
                int legacyUnverifiedSegments) {
            this("SPEAKING", holisticScore, coveredSegments, totalSegments, profileState, evidenceMode,
                    evidenceNote, overallSummaries, strengths, needsImprovement, actionPlan, criteria,
                    evaluatorCapability, evidenceContractVersion, contractTrust,
                    holisticScoreAvailable, legacyUnverifiedSegments);
        }

        public SpeakingResultPayload(
                ResultScoreSummary holisticScore,
                int coveredSegments,
                int totalSegments,
                String evidenceMode,
                String evidenceNote,
                List<String> overallSummaries,
                List<String> strengths,
                List<String> needsImprovement,
                List<SpeakingActionPlanView> actionPlan,
                List<SpeakingCriterionResult> criteria,
                String evaluatorCapability,
                String evidenceContractVersion,
                String contractTrust,
                boolean holisticScoreAvailable,
                int legacyUnverifiedSegments) {
            this(holisticScore, coveredSegments, totalSegments,
                    legacyUnverifiedSegments > 0 && coveredSegments == 0
                            ? "LEGACY_UNVERIFIED"
                            : defaultSpeakingProfileState(coveredSegments, totalSegments),
                    evidenceMode, evidenceNote, overallSummaries, strengths, needsImprovement,
                    actionPlan, criteria, evaluatorCapability, evidenceContractVersion,
                    contractTrust, holisticScoreAvailable, legacyUnverifiedSegments);
        }

        public boolean transcriptGroundedProfile() {
            return ("CURRENT_VERIFIED".equals(contractTrust)
                    || "MIXED_WITH_LEGACY_UNVERIFIED".equals(contractTrust))
                    && "TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION".equals(evaluatorCapability)
                    && "TRANSCRIPT_ONLY".equals(evidenceMode);
        }

        public String profileTitle() {
            if (holisticScoreAvailable) {
                return "Kết quả Nói tổng hợp";
            }
            if (transcriptGroundedProfile()
                    && ("READY".equals(profileState) || "PARTIAL".equals(profileState))) {
                return "Hồ sơ ngôn ngữ dựa trên bản chép lời";
            }
            if (transcriptGroundedProfile() && "PENDING".equals(profileState)) {
                return "Hồ sơ ngôn ngữ đang được xử lý";
            }
            if (transcriptGroundedProfile() && "LOW_CONFIDENCE".equals(profileState)) {
                return "Bản chép lời có độ tin cậy thấp";
            }
            return "Hồ sơ đánh giá chưa khả dụng";
        }

        public String profileStateLabel() {
            return switch (profileState) {
                case "READY" -> "Hồ sơ đã sẵn sàng";
                case "PARTIAL" -> "Hồ sơ mới có một phần";
                case "PENDING" -> "Đang xử lý bằng chứng";
                case "LOW_CONFIDENCE" -> "Bản chép lời có độ tin cậy thấp";
                case "FAILED" -> "Chưa thể tạo hồ sơ";
                case "LEGACY_UNVERIFIED" -> "Kết quả cũ chưa được xác minh";
                default -> "Chưa có hồ sơ khả dụng";
            };
        }

        public String profileStateDescription() {
            return switch (profileState) {
                case "READY" -> coveredSegments + "/" + totalSegments
                        + " phần trả lời có bằng chứng đủ điều kiện.";
                case "PARTIAL" -> coveredSegments + "/" + totalSegments
                        + " phần trả lời có bằng chứng đủ điều kiện; phần còn lại không được tính là 0 điểm.";
                case "PENDING" -> "Bằng chứng chưa xử lý xong; chưa có điểm nào được suy đoán trong thời gian chờ.";
                case "LOW_CONFIDENCE" -> "Bản chép lời hiện tại không đủ tin cậy để chấm tiêu chí; trạng thái này không được quy đổi thành 0 điểm.";
                case "FAILED" -> "Không có điểm nào được tạo khi xử lý bằng chứng không thành công.";
                case "LEGACY_UNVERIFIED" -> "Dữ liệu lưu trước đây không đủ thông tin để xác minh; mọi số điểm cũ đều được ẩn.";
                default -> "Chưa có bằng chứng đủ điều kiện và trạng thái này không có nghĩa là 0 điểm.";
            };
        }

        public String evidenceSourceLabel() {
            if (transcriptGroundedProfile()
                    && ("READY".equals(profileState) || "PARTIAL".equals(profileState))) {
                return "Bản chép lời của bài làm";
            }
            if (transcriptGroundedProfile() && "PENDING".equals(profileState)) {
                return "Bản chép lời đang được xử lý";
            }
            if (transcriptGroundedProfile() && "LOW_CONFIDENCE".equals(profileState)) {
                return "Bản chép lời đã xác minh nguồn nhưng có độ tin cậy thấp";
            }
            if (transcriptGroundedProfile() && "FAILED".equals(profileState)) {
                return "Bản chép lời chưa đủ điều kiện đánh giá";
            }
            if (holisticScoreAvailable && "DIRECT_AUDIO_AND_TRANSCRIPT".equals(evidenceMode)) {
                return "Âm thanh trực tiếp và bản chép lời đã xác minh";
            }
            return "Chưa có nguồn bằng chứng đủ điều kiện";
        }

        public String scopeLabel() {
            if (transcriptGroundedProfile() && "LOW_CONFIDENCE".equals(profileState)) {
                return "Chỉ ghi nhận nguồn bằng chứng; chưa đánh giá tiêu chí";
            }
            if (transcriptGroundedProfile()) {
                return "4 tiêu chí ngôn ngữ; 2 tiêu chí cần âm thanh trực tiếp";
            }
            return holisticScoreAvailable
                    ? "Hồ sơ Nói tổng hợp đã qua kiểm soát"
                    : "Không suy đoán tiêu chí hoặc điểm tổng hợp";
        }

        public String trustLabel() {
            return switch (contractTrust) {
                case "CURRENT_VERIFIED" -> "Đã xác minh theo quy tắc đánh giá hiện tại";
                case "MIXED_WITH_LEGACY_UNVERIFIED" -> "Chỉ phần bằng chứng hiện tại được dùng để chấm";
                default -> "Chưa thể xác minh theo quy tắc đánh giá hiện tại";
            };
        }
    }

    public record SpeakingCriterionResult(
            String criterionId,
            String label,
            BigDecimal weight,
            BigDecimal score,
            BigDecimal percentage,
            int coveredSegments,
            int totalSegments,
            ResultEvaluationBand band,
            String summary,
            boolean advisoryOnly,
            String availability,
            boolean requiresDirectAudioEvidence
    ) {
        public SpeakingCriterionResult {
            availability = speakingAvailability(availability, score);
            if ("NOT_SCORABLE".equals(availability) && !requiresDirectAudioEvidence) {
                availability = "UNAVAILABLE";
            }
            if (!"SCORED".equals(availability)) {
                weight = null;
                score = null;
                percentage = null;
                summary = null;
                advisoryOnly = false;
                band = ResultEvaluationBand.UNAVAILABLE;
            }
            band = band == null ? ResultEvaluationBand.UNAVAILABLE : band;
        }

        public SpeakingCriterionResult(
                String criterionId,
                String label,
                BigDecimal weight,
                BigDecimal score,
                BigDecimal percentage,
                int coveredSegments,
                int totalSegments,
                ResultEvaluationBand band,
                String summary,
                boolean advisoryOnly
        ) {
            this(criterionId, label, weight, score, percentage, coveredSegments,
                    totalSegments, band, summary, advisoryOnly,
                    score == null ? "UNAVAILABLE" : "SCORED",
                    speakingCriterionRequiresDirectAudio(criterionId));
        }

        public SpeakingCriterionResult(
                String criterionId,
                String label,
                BigDecimal weight,
                BigDecimal score,
                BigDecimal percentage,
                int coveredSegments,
                int totalSegments,
                ResultEvaluationBand band,
                String summary,
                boolean advisoryOnly,
                String availability
        ) {
            this(criterionId, label, weight, score, percentage, coveredSegments,
                    totalSegments, band, summary, advisoryOnly, availability,
                    speakingCriterionRequiresDirectAudio(criterionId));
        }

        public String coverageLabel() {
            if ("NOT_SCORABLE".equals(availability)) {
                return "Bộ đánh giá chưa nhận âm thanh trực tiếp của người học";
            }
            if ("LEGACY_UNVERIFIED".equals(availability)) {
                return "Kết quả lưu trước đây chưa đủ thông tin xác minh";
            }
            if ("UNAVAILABLE".equals(availability)) {
                return "Chưa có bằng chứng đủ điều kiện để chấm";
            }
            return coveredSegments + "/" + totalSegments
                    + " phần trả lời có bằng chứng bản chép lời";
        }

        public String scoreDisplay() {
            if (!"SCORED".equals(availability) || score == null || weight == null) {
                return null;
            }
            return compactResultNumber(score) + "/" + compactResultNumber(weight);
        }

        public boolean scored() {
            return "SCORED".equals(availability) && scoreDisplay() != null;
        }

        public boolean notScorable() {
            return "NOT_SCORABLE".equals(availability);
        }

        public String availabilityLabel() {
            return switch (availability) {
                case "SCORED" -> "Đã chấm từ bản chép lời";
                case "NOT_SCORABLE" -> "Chưa thể chấm";
                case "LEGACY_UNVERIFIED" -> "Kết quả cũ không khả dụng";
                default -> "Chưa có dữ liệu chấm";
            };
        }

        public String stateCssClass() {
            return switch (availability) {
                case "SCORED" -> "scored";
                case "NOT_SCORABLE" -> "not-scorable";
                case "LEGACY_UNVERIFIED" -> "legacy-unverified";
                default -> "unavailable";
            };
        }

        public SpeakingCriterionResult unavailableView(String unavailableState) {
            return new SpeakingCriterionResult(
                    criterionId, label, null, null, null, coveredSegments, totalSegments,
                    ResultEvaluationBand.UNAVAILABLE, null, false, unavailableState,
                    requiresDirectAudioEvidence);
        }
    }

    public record ResultRubricCriterion(
            String criterionId,
            String label,
            BigDecimal score,
            BigDecimal maxScore,
            String feedback
    ) {
        public String scoreDisplay() {
            if (score == null || maxScore == null) {
                return null;
            }
            return compactResultNumber(score) + "/" + compactResultNumber(maxScore);
        }
    }

    public enum ResultEvaluationBand {
        LIMITED("limited", "Cần cải thiện"),
        DEVELOPING("developing", "Đang phát triển"),
        GOOD("good", "Tốt"),
        VERY_GOOD("very-good", "Rất tốt"),
        UNAVAILABLE("unavailable", "Chưa có dữ liệu");

        private final String cssClass;
        private final String label;

        ResultEvaluationBand(String cssClass, String label) {
            this.cssClass = cssClass;
            this.label = label;
        }

        public String cssClass() {
            return cssClass;
        }

        public String label() {
            return label;
        }

        public static ResultEvaluationBand fromPercentage(BigDecimal percentage) {
            if (percentage == null) {
                return UNAVAILABLE;
            }
            if (percentage.compareTo(BigDecimal.valueOf(40)) < 0) {
                return LIMITED;
            }
            if (percentage.compareTo(BigDecimal.valueOf(60)) < 0) {
                return DEVELOPING;
            }
            if (percentage.compareTo(BigDecimal.valueOf(80)) < 0) {
                return GOOD;
            }
            return VERY_GOOD;
        }
    }

    private static <T> List<T> immutableResultList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String blankResultText(String value) {
        return value == null ? "" : value;
    }

    private static String tfngRelationForResult(String value) {
        return switch (value) {
            case "TRUE" -> "ENTAILED";
            case "FALSE" -> "CONTRADICTED";
            case "NOT_GIVEN" -> "NOT_STATED";
            default -> "";
        };
    }

    private static String tfngLabelVi(String value) {
        return switch (value) {
            case "TRUE" -> "Đúng";
            case "FALSE" -> "Sai";
            case "NOT_GIVEN" -> "Không có thông tin";
            default -> "Chưa xác định";
        };
    }

    private static String tfngLabelKo(String value) {
        return switch (value) {
            case "TRUE" -> "맞음";
            case "FALSE" -> "틀림";
            case "NOT_GIVEN" -> "정보 없음";
            default -> "확인 불가";
        };
    }

    private static String tfngRelationLabelVi(String value) {
        return switch (value) {
            case "ENTAILED" -> "Được nguồn xác nhận";
            case "CONTRADICTED" -> "Trái với nguồn";
            case "NOT_STATED" -> "Nguồn không nêu";
            default -> "Chưa xác định";
        };
    }

    private static String tfngRelationLabelKo(String value) {
        return switch (value) {
            case "ENTAILED" -> "근거에서 확인됨";
            case "CONTRADICTED" -> "근거와 모순됨";
            case "NOT_STATED" -> "근거에 제시되지 않음";
            default -> "확인 불가";
        };
    }

    private static String speakingAvailability(String availability, BigDecimal score) {
        String resolved = availability == null || availability.isBlank()
                ? (score == null ? "UNAVAILABLE" : "SCORED")
                : availability;
        return switch (resolved) {
            case "SCORED", "NOT_SCORABLE", "UNAVAILABLE", "LEGACY_UNVERIFIED" -> resolved;
            default -> "UNAVAILABLE";
        };
    }

    private static String defaultSpeakingProfileState(int coveredSegments, int totalSegments) {
        if (totalSegments <= 0) {
            return "UNAVAILABLE";
        }
        if (coveredSegments >= totalSegments) {
            return "READY";
        }
        return coveredSegments > 0 ? "PARTIAL" : "UNAVAILABLE";
    }

    private static boolean speakingCriterionRequiresDirectAudio(String criterionId) {
        return "S_FLUENCY".equals(criterionId)
                || "S_PRONUNCIATION_DELIVERY".equals(criterionId);
    }

    private static String compactResultNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
