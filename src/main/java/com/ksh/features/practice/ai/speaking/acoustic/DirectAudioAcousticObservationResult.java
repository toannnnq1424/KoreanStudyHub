package com.ksh.features.practice.ai.speaking.acoustic;

import com.ksh.features.practice.ai.contract.PracticeAiResultCompleteness;

import java.math.BigDecimal;
import java.util.List;

/** Dark-only provider observations. This type is not a learner scoring result. */
public record DirectAudioAcousticObservationResult(
        State state,
        String contractVersion,
        String language,
        String evaluatorId,
        String model,
        String calibrationProfileId,
        String calibrationVersion,
        List<DimensionObservation> observations,
        BigDecimal providerObservationTotal,
        BigDecimal providerConfidence,
        String providerRequestId,
        String providerCacheIdentity,
        String rejectionCode,
        PracticeAiResultCompleteness completeness,
        boolean scoreReleaseEligible,
        boolean presenterEligible,
        BigDecimal holisticScore,
        BigDecimal attemptPoints) {

    public DirectAudioAcousticObservationResult {
        observations = observations == null ? List.of() : List.copyOf(observations);
        if (completeness == null) {
            throw new IllegalArgumentException(
                    "Direct-audio completeness is required");
        }
        scoreReleaseEligible = false;
        presenterEligible = false;
        holisticScore = null;
        attemptPoints = null;
    }

    public static DirectAudioAcousticObservationResult rejected(String code) {
        return new DirectAudioAcousticObservationResult(
                State.REJECTED_NON_SCORE_BEARING,
                DirectAudioAcousticResponseNormalizer.CONTRACT_VERSION,
                null, null, null, null, null, List.of(), null, null,
                null, null, code,
                PracticeAiResultCompleteness.unavailable(code, 0),
                false, false, null, null);
    }

    public enum State {
        VALID_DARK_OBSERVATION,
        REJECTED_NON_SCORE_BEARING
    }

    public enum Dimension {
        PRONUNCIATION,
        FLUENCY
    }

    public record DimensionObservation(
            Dimension dimension,
            BigDecimal providerSignalValue,
            BigDecimal confidence,
            List<EvidenceSpan> evidence) {
        public DimensionObservation {
            evidence = List.copyOf(evidence);
        }
    }

    public record EvidenceSpan(
            String evidenceId,
            long startMs,
            long endMs,
            BigDecimal confidence,
            String observation) {
    }
}
