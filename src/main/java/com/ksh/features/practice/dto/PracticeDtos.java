package com.ksh.features.practice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
