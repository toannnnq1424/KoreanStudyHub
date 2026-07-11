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

    public static String getCategoryLabel(String cat) {
        if (cat == null || cat.isBlank()) return "Chưa phân loại";
        return com.ksh.entities.PracticeCategory.fromString(cat).label();
    }


    public record PracticeSetRow(Long id, String title, String description,
                                 String skill, String skillLabel,
                                 String topikLevel, String categoryLabel,
                                 String badgeText, String metadataJson,
                                 String creationMethod) {
    }


    public record ExampleBox(
        String label,
        String content,
        List<String> choices,
        Integer answer
    ) {
    }

    public record PracticeQuestionRow(Long id, Integer questionNo,
                                      String questionType, String prompt,
                                      List<String> options,
                                      String answerKey,
                                      String explanation,
                                      String groupLabel) {
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
                                       String skill, String topikLevel,
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
            String transcriptionModel
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
                    null, false, null, null, null, null, null, engine, null);
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
        }
    }

    public record SpeakingRubricScoreView(
            String name,
            BigDecimal percentage,
            String feedback,
            String criterionId,
            BigDecimal score,
            BigDecimal maxScore
    ) {
        public SpeakingRubricScoreView(String name, BigDecimal percentage, String feedback) {
            this(name, percentage, feedback, null, null, null);
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
    ) {}

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

    public record PracticeAttemptHistoryRow(Long id,
                                            BigDecimal score,
                                            BigDecimal totalPoints,
                                            String status,
                                            LocalDateTime submittedAt,
                                            LocalDateTime createdAt,
                                            String skill,
                                            Long testId,
                                            Long sectionId) {
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
            double normalizedScore,
            int attemptCount,
            double deltaFromLastPeriod
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
    //  Unified Result Architecture — PracticeSection.skill is source of truth
    // =========================================================================

    /**
     * Result for a single section (one skill).
     * For READING/LISTENING sections: correctCount, incorrectCount, groups populated.
     * For WRITING/SPEAKING sections: aiFeedbackJson populated, groups may be empty.
     */
    public record SectionResultRow(
            Long sectionId,
            String sectionTitle,
            String skill,
            int correctCount,
            int incorrectCount,
            int totalCount,
            java.math.BigDecimal sectionScore,
            java.math.BigDecimal sectionTotalPoints,
            List<PerformanceByTypeRow> performanceByType,
            List<ReviewGroupRow> groups,
            String aiFeedbackJson,
            String optionLabelMode
    ) {
        public boolean hasAiFeedback() {
            return aiFeedbackJson != null && !aiFeedbackJson.isBlank();
        }

        public boolean isObjectiveSkill() {
            return "READING".equals(skill) || "LISTENING".equals(skill);
        }
    }

    /**
     * Top-level unified result view — replaces PracticeResultView + ReadingListeningResultView.
     * The controller puts this in the model, and result-shell.html delegates rendering
     * to skill-specific Thymeleaf fragments.
     */
    public record PracticeAttemptResultView(
            Long submissionId,
            PracticeSetRow set,
            java.math.BigDecimal totalScore,
            java.math.BigDecimal totalPoints,
            String scoreLabel,
            java.time.LocalDateTime submittedAt,
            List<SectionResultRow> sections
    ) {
        public boolean hasMultipleSections() {
            return sections != null && sections.size() > 1;
        }

        public int totalCorrect() {
            if (sections == null) return 0;
            return sections.stream().mapToInt(SectionResultRow::correctCount).sum();
        }

        public int totalIncorrect() {
            if (sections == null) return 0;
            return sections.stream().mapToInt(SectionResultRow::incorrectCount).sum();
        }
    }
}
