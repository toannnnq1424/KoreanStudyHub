package com.ksh.features.practice.ai.speaking;

import org.springframework.stereotype.Component;

@Component
public class SpeakingEvaluationReusePolicy {

    public Decision decide(
            SpeakingEvaluationResult stored,
            SpeakingEvaluationIdentity currentIdentity,
            boolean realProviderEnabled
    ) {
        if (stored == null) {
            return Decision.evaluate("MISSING_STORED_RESULT");
        }
        if (currentIdentity == null) {
            return Decision.evaluate("MISSING_CURRENT_IDENTITY");
        }
        if (realProviderEnabled && (stored.evaluationStatus() == SpeakingEvaluationStatus.LEGACY_RESULT
                || stored.evaluationStatus() == SpeakingEvaluationStatus.MOCK_EVALUATED)) {
            return Decision.evaluate("LEGACY_OR_MOCK_NOT_REUSABLE_WITH_REAL_PROVIDER");
        }
        if (!currentIdentity.matches(stored)) {
            return Decision.evaluate("IDENTITY_CHANGED");
        }
        if (stored.evaluationStatus() != null
                && stored.evaluationStatus().scoreBearing()
                && stored.evaluationStatus() != SpeakingEvaluationStatus.MOCK_EVALUATED
                && stored.evaluationStatus() != SpeakingEvaluationStatus.LEGACY_RESULT) {
            return stored.profileAvailable()
                    ? Decision.reuse("MATCHING_TRANSCRIPT_LANGUAGE_PROFILE")
                    : Decision.evaluate("MATCHING_RESULT_MISSING_LANGUAGE_PROFILE");
        }
        if (stored.retryable()) {
            return Decision.evaluate("RETRYABLE_FAILURE");
        }
        return Decision.reuse("MATCHING_NON_RETRYABLE_FAILURE");
    }

    public SpeakingEvaluationResult preserveSuccessOnTransientFailure(
            SpeakingEvaluationResult stored,
            SpeakingEvaluationResult candidate,
            SpeakingEvaluationIdentity currentIdentity
    ) {
        if (stored != null
                && candidate != null
                && candidate.retryable()
                && !candidate.profileAvailable()
                && currentIdentity != null
                && currentIdentity.matches(stored)
                && stored.profileAvailable()
                && stored.evaluationStatus() != SpeakingEvaluationStatus.LEGACY_RESULT
                && stored.evaluationStatus() != SpeakingEvaluationStatus.MOCK_EVALUATED) {
            return stored;
        }
        return candidate;
    }

    public record Decision(boolean reuse, String reason) {
        static Decision reuse(String reason) {
            return new Decision(true, reason);
        }

        static Decision evaluate(String reason) {
            return new Decision(false, reason);
        }
    }
}
