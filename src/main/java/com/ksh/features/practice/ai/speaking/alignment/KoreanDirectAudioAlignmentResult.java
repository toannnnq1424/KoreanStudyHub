package com.ksh.features.practice.ai.speaking.alignment;

import java.math.BigDecimal;
import java.util.List;

/** Non-score-bearing alignment projection for authorized ranged playback. */
public record KoreanDirectAudioAlignmentResult(
        Status status,
        String reason,
        List<Span> spans,
        List<RejectedItem> rejectedItems,
        boolean scoreReleaseEligible,
        boolean learnerVisible,
        String playbackUrl,
        BigDecimal holisticScore,
        BigDecimal attemptPoints) {

    public KoreanDirectAudioAlignmentResult {
        spans = spans == null ? List.of() : List.copyOf(spans);
        rejectedItems = rejectedItems == null ? List.of() : List.copyOf(rejectedItems);
        scoreReleaseEligible = false;
        learnerVisible = false;
        playbackUrl = null;
        holisticScore = null;
        attemptPoints = null;
    }

    public static KoreanDirectAudioAlignmentResult unavailable(String reason) {
        return new KoreanDirectAudioAlignmentResult(
                Status.UNAVAILABLE, reason, List.of(), List.of(),
                false, false, null, null, null);
    }

    public enum Status {
        COMPLETE,
        PARTIAL_NON_SCORE,
        UNAVAILABLE
    }

    public enum Level {
        EOJJEOL,
        SYLLABLE,
        JAMO,
        PHONEME
    }

    public enum IssueCode {
        NONE,
        SUBSTITUTION,
        OMISSION,
        INSERTION,
        DURATION,
        PAUSE,
        UNALIGNED,
        LOW_CONFIDENCE
    }

    public enum EvidenceSource {
        FORCED_ALIGNMENT,
        ASR_WORD_TIMESTAMP,
        ACOUSTIC_CLASSIFIER
    }

    public record Span(
            String spanId,
            Level level,
            String parentSpanId,
            String tokenId,
            String surfaceKo,
            int utf16Start,
            int utf16End,
            long startMs,
            long endMs,
            String expectedPronunciation,
            String observedPronunciation,
            IssueCode issueCode,
            BigDecimal confidence,
            EvidenceSource evidenceSource,
            String evidenceId) {
    }

    public record RejectedItem(String spanId, String reason) {
    }
}
