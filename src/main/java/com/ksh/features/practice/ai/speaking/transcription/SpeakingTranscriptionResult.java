package com.ksh.features.practice.ai.speaking.transcription;

import com.ksh.features.practice.ai.speaking.SpeakingEvaluationSource;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;

import java.math.BigDecimal;

public record SpeakingTranscriptionResult(
        SpeakingEvaluationStatus status,
        SpeakingEvaluationSource source,
        String provider,
        String model,
        String language,
        String transcript,
        String normalizedTranscript,
        BigDecimal transcriptConfidence,
        LogprobSummary logprobSummary,
        Long durationMs,
        Long latencyMs,
        SpeakingTranscriptionErrorCategory errorCategory,
        boolean retryable
) {
    public static SpeakingTranscriptionResult failure(
            SpeakingEvaluationStatus status,
            String provider,
            String model,
            String language,
            SpeakingTranscriptionErrorCategory errorCategory,
            boolean retryable
    ) {
        return new SpeakingTranscriptionResult(
                status,
                SpeakingEvaluationSource.PROVIDER,
                provider,
                model,
                language,
                null,
                null,
                null,
                null,
                null,
                null,
                errorCategory,
                retryable);
    }

    public record LogprobSummary(int tokenCount, BigDecimal averageLogprob, BigDecimal minimumLogprob) {
        @Override
        public String toString() {
            return "LogprobSummary{"
                    + "tokenCount=" + tokenCount
                    + ", averageLogprob=" + averageLogprob
                    + ", minimumLogprob=" + minimumLogprob
                    + '}';
        }
    }

    @Override
    public String toString() {
        return "SpeakingTranscriptionResult{"
                + "status=" + status
                + ", source=" + source
                + ", provider='" + provider + '\''
                + ", model='" + model + '\''
                + ", language='" + language + '\''
                + ", transcriptPresent=" + (transcript != null && !transcript.isBlank())
                + ", normalizedTranscriptPresent=" + (normalizedTranscript != null && !normalizedTranscript.isBlank())
                + ", transcriptConfidence=" + transcriptConfidence
                + ", logprobSummary=" + logprobSummary
                + ", durationMs=" + durationMs
                + ", latencyMs=" + latencyMs
                + ", errorCategory=" + errorCategory
                + ", retryable=" + retryable
                + '}';
    }
}
