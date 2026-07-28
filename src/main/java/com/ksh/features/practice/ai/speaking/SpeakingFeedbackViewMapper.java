package com.ksh.features.practice.ai.speaking;

import com.ksh.features.practice.dto.PracticeDtos.SpeakingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingActionPlanView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingCriterionFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingFindingView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingRubricScoreView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingSubcriterionFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingTranscriptAnnotationView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class SpeakingFeedbackViewMapper {

    public SpeakingFeedbackView map(SpeakingEvaluationResult result) {
        if (result == null) {
            return null;
        }
        boolean trusted = result.currentEvidenceContract();
        return new SpeakingFeedbackView(
                result.holisticScoreAvailable() ? result.overallScore() : null,
                trusted ? result.overallSummary() : null,
                trusted ? result.overallSummary() : null,
                result.rubricScores().stream()
                        .map(row -> rubricScore(row, trusted))
                        .toList(),
                trusted ? feedbackItems(result.strengths()) : List.of(),
                trusted ? feedbackItems(result.needsImprovement()) : List.of(),
                result.sampleAnswer(),
                result.upgradedAnswer(),
                result.model(),
                result.source() == null ? null : result.source().name(),
                result.evaluationStatus() == null ? "EVALUATION_UNAVAILABLE" : result.evaluationStatus().name(),
                result.holisticScoreAvailable(),
                result.levelLabel(),
                trusted ? result.overallSummary() : null,
                trusted ? result.taskAchievementSummary() : null,
                trusted ? result.majorStrengths() : List.of(),
                trusted ? result.majorNeedsImprovement() : List.of(),
                trusted ? actionPlan(result.actionPlan()) : List.of(),
                trusted ? criterionFeedback(result.criterionFeedback()) : List.of(),
                trusted ? transcriptAnnotations(result.transcriptAnnotations()) : List.of(),
                trusted ? transcriptAnnotations(result.transcriptAnnotations()) : List.of(),
                result.transcript(),
                result.normalizedTranscript(),
                result.actuallyHeardTranscript(),
                result.interpretedIntent(),
                result.transcriptConfidence(),
                result.confidenceNotes(),
                result.listenerBurden(),
                result.pronunciationAdvisory(),
                result.fluencyObservations(),
                result.errorCategory(),
                result.retryable(),
                result.audioMediaId(),
                result.mediaVersion(),
                result.promptVersion(),
                result.rubricVersion(),
                result.schemaVersion(),
                result.model(),
                result.transcriptionModel(),
                result.evaluatorCapability().name(),
                result.evidenceMode().name(),
                result.evidenceContractVersion(),
                result.contractTrust().name(),
                result.profileAvailable(),
                result.holisticScoreAvailable());
    }

    private static SpeakingRubricScoreView rubricScore(
            SpeakingEvaluationResult.RubricScore row,
            boolean trusted
    ) {
        SpeakingRubricCriterion criterion = row == null ? null : row.criterion();
        boolean numeric = trusted && criterion != null && row.scored();
        String availability = !trusted
                ? SpeakingCriterionAvailability.LEGACY_UNVERIFIED.name()
                : criterion == null
                ? SpeakingCriterionAvailability.UNAVAILABLE.name()
                : row.availability().name();
        BigDecimal score = numeric ? row.score() : null;
        BigDecimal maxScore = numeric ? row.maxScore() : null;
        return new SpeakingRubricScoreView(
                speakingCriterionLabel(criterion),
                numeric ? percentage(score, maxScore) : null,
                trusted && criterion != null ? row.feedback() : null,
                criterion == null ? null : criterion.id(),
                score,
                maxScore,
                availability);
    }

    private static BigDecimal percentage(BigDecimal score, BigDecimal maxScore) {
        return maxScore == null || maxScore.signum() == 0 || score == null ? null
                : score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP);
    }

    private static List<SpeakingFindingView> feedbackItems(List<SpeakingEvaluationResult.FeedbackItem> items) {
        return items.stream()
                .map(item -> new SpeakingFindingView(
                        item.criterion() == null ? null : item.criterion().id(),
                        item.subCriterionId(),
                        item.evidenceScope(),
                        item.evidence(),
                        item.evidenceSource() == null ? null : item.evidenceSource().name(),
                        item.explanationVi(),
                        item.correction()))
                .toList();
    }

    private static List<SpeakingActionPlanView> actionPlan(List<SpeakingEvaluationResult.ActionPlanItem> items) {
        return items.stream()
                .map(item -> new SpeakingActionPlanView(
                        item.criterion() == null ? null : item.criterion().id(),
                        item.subCriterionId(),
                        item.title(),
                        item.instruction(),
                        item.reason(),
                        item.priority()))
                .toList();
    }

    private static List<SpeakingCriterionFeedbackView> criterionFeedback(
            List<SpeakingEvaluationResult.CriterionFeedback> items) {
        return items.stream()
                .map(item -> new SpeakingCriterionFeedbackView(
                        item.criterion() == null ? null : item.criterion().id(),
                        speakingCriterionLabel(item.criterion()),
                        item.score(),
                        item.maxScore(),
                        item.levelLabel(),
                        item.summary(),
                        item.strengths(),
                        item.needsImprovement(),
                        item.subcriteria().stream()
                                .map(sub -> new SpeakingSubcriterionFeedbackView(
                                        sub.subCriterionId(),
                                        speakingSubcriterionLabel(sub.subCriterionId()),
                                        speakingLevelLabel(sub.levelLabel()),
                                        sub.summary(),
                                        sub.strengths(),
                                        sub.needsImprovement()))
                                .toList()))
                .toList();
    }

    private static String speakingCriterionLabel(SpeakingRubricCriterion criterion) {
        if (criterion == null) {
            return "Tiêu chí không xác định";
        }
        return switch (criterion) {
            case CONTENT_TASK_FULFILLMENT -> "Nội dung và hoàn thành yêu cầu";
            case GRAMMAR_SENTENCE_CONTROL -> "Ngữ pháp và kiểm soát câu";
            case VOCABULARY_EXPRESSIONS -> "Từ vựng và cách diễn đạt";
            case COHERENCE_ORGANIZATION -> "Mạch lạc và tổ chức ý";
            case FLUENCY -> "Độ lưu loát";
            case PRONUNCIATION_DELIVERY -> "Phát âm và cách thể hiện";
        };
    }

    private static String speakingSubcriterionLabel(String subcriterionId) {
        if (subcriterionId == null || subcriterionId.isBlank()) {
            return "Tiêu chí thành phần";
        }
        return switch (subcriterionId.trim()) {
            case "S_CONTENT_RELEVANCE" -> "Mức độ liên quan đến đề";
            case "S_CONTENT_PROMPT_COVERAGE" -> "Mức độ bao phủ yêu cầu";
            case "S_CONTENT_SPECIFICITY_EXAMPLES" -> "Độ cụ thể và ví dụ";
            case "S_VOCAB_TOPIC_WORDS" -> "Từ vựng theo chủ đề";
            case "S_VOCAB_NATURAL_EXPRESSIONS" -> "Cách diễn đạt tự nhiên";
            case "S_VOCAB_REPETITION_CONTROL" -> "Kiểm soát lặp từ";
            case "S_VOCAB_WORD_CHOICE" -> "Lựa chọn từ ngữ";
            case "S_GRAMMAR_PARTICLES" -> "Tiểu từ";
            case "S_GRAMMAR_TENSE_ASPECT" -> "Thời và thể";
            case "S_GRAMMAR_ENDINGS" -> "Đuôi câu";
            case "S_GRAMMAR_SENTENCE_STRUCTURE" -> "Cấu trúc câu";
            case "S_GRAMMAR_HONORIFIC_REGISTER" -> "Kính ngữ và mức độ trang trọng";
            case "S_GRAMMAR_CONNECTORS" -> "Từ nối và liên kết";
            case "S_COHERENCE_ORGANIZATION" -> "Tổ chức ý";
            case "S_COHERENCE_LOGICAL_FLOW" -> "Mạch phát triển logic";
            case "S_COHERENCE_DISCOURSE_MARKERS" -> "Dấu hiệu liên kết diễn ngôn";
            default -> "Tiêu chí thành phần";
        };
    }

    private static String speakingLevelLabel(String providerLevelLabel) {
        if (providerLevelLabel == null || providerLevelLabel.isBlank()) {
            return "Chưa phân loại";
        }
        String normalized = providerLevelLabel.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "EXCELLENT", "ADVANCED", "STRONG" -> "Tốt";
            case "GOOD", "PROFICIENT" -> "Khá";
            case "DEVELOPING", "NEEDS_IMPROVEMENT" -> "Đang phát triển";
            case "BASIC", "LIMITED" -> "Cần củng cố";
            case "NOT_SCORABLE", "UNAVAILABLE" -> "Không khả dụng";
            default -> "Chưa phân loại";
        };
    }

    private static List<SpeakingTranscriptAnnotationView> transcriptAnnotations(
            List<SpeakingEvaluationResult.TranscriptAnnotation> items) {
        return items.stream()
                .map(item -> new SpeakingTranscriptAnnotationView(
                        item.criterion() == null ? null : item.criterion().id(),
                        item.subCriterionId(),
                        item.evidenceScope(),
                        item.evidence() == null ? item.originalSpan() : item.evidence(),
                        item.evidenceSource() == null ? null : item.evidenceSource().name(),
                        item.startOffset(),
                        item.endOffset(),
                        item.startOffset(),
                        item.endOffset(),
                        item.annotationType(),
                        annotationKind(item.annotationType()),
                        item.category(),
                        item.explanationVi() == null ? item.explanation() : item.explanationVi(),
                        item.suggestionKo(),
                        item.replacement(),
                        item.severity()))
                .toList();
    }

    private static String annotationKind(String type) {
        if (type == null) {
            return null;
        }
        String normalized = type.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("strength")) {
            return "strength";
        }
        if (normalized.contains("need")) {
            return "need";
        }
        return "advisory";
    }
}
