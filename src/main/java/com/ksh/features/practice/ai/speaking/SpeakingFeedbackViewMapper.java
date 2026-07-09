package com.ksh.features.practice.ai.speaking;

import com.ksh.features.practice.dto.PracticeDtos.SpeakingFeedbackView;
import com.ksh.features.practice.dto.PracticeDtos.SpeakingRubricScoreView;
import org.springframework.stereotype.Component;

@Component
public class SpeakingFeedbackViewMapper {

    public SpeakingFeedbackView map(SpeakingEvaluationResult result) {
        if (result == null) {
            return null;
        }
        return new SpeakingFeedbackView(
                result.scoreAvailable() ? result.overallScore() : null,
                null,
                result.evaluationStatus().name(),
                result.rubricScores().stream()
                        .map(row -> new SpeakingRubricScoreView(
                                row.criterion().label(),
                                row.maxScore().signum() == 0 ? null
                                        : row.score().multiply(java.math.BigDecimal.valueOf(100))
                                        .divide(row.maxScore(), 2, java.math.RoundingMode.HALF_UP),
                                row.feedback()))
                        .toList(),
                java.util.List.of(),
                java.util.List.of(),
                result.sampleAnswer(),
                result.upgradedAnswer(),
                result.model(),
                result.source().name());
    }
}
