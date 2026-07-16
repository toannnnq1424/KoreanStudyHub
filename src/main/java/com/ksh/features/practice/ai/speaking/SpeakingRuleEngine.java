package com.ksh.features.practice.ai.speaking;

import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class SpeakingRuleEngine {
    private static final List<String> FILLERS = List.of("음", "어", "그", "뭐", "뭐랄까", "그러니까", "약간", "이제");
    private static final List<String> DISCOURSE_MARKERS = List.of(
            "먼저", "그리고", "또한", "하지만", "그래서", "예를 들면", "마지막으로", "제 생각에는");
    private static final Pattern HANGUL = Pattern.compile(".*[가-힣].*");

    public SpeakingRuleAnalysis analyze(SpeakingTranscriptionResult transcription, boolean textFallback) {
        String transcript = normalize(transcription == null ? null : transcription.normalizedTranscript());
        if (transcript == null) {
            transcript = normalize(transcription == null ? null : transcription.transcript());
        }
        return analyze(transcript, transcription == null ? null : transcription.transcriptConfidence(), textFallback);
    }

    public SpeakingRuleAnalysis analyze(String transcriptText, BigDecimal transcriptConfidence, boolean textFallback) {
        String transcript = normalize(transcriptText);
        List<SpeakingRuleSignal> signals = new ArrayList<>();
        if (textFallback) {
            signals.add(signal(SpeakingRuleSeverity.MEDIUM, SpeakingRuleAction.INFO,
                    SpeakingRuleCategory.PRONUNCIATION, "TEXT_FALLBACK_NO_AUDIO",
                    "Text fallback: no learner audio is available, so pronunciation is not audio-grounded."));
        }
        if (transcriptConfidence != null
                && transcriptConfidence.compareTo(new BigDecimal("0.50")) < 0) {
            signals.add(signal(SpeakingRuleSeverity.MEDIUM, SpeakingRuleAction.INFO,
                    SpeakingRuleCategory.PRONUNCIATION, "LOW_TRANSCRIPT_CONFIDENCE",
                    "Low transcript confidence; language and delivery judgments should be conservative."));
        }
        signals.add(signal(SpeakingRuleSeverity.LOW, SpeakingRuleAction.INFO,
                SpeakingRuleCategory.PRONUNCIATION, "NO_PHONEME_CERTAINTY",
                "No specialized pronunciation alignment is available; do not output exact phoneme diagnosis."));
        if (transcript == null || !HANGUL.matcher(transcript).matches()) {
            signals.add(signal(SpeakingRuleSeverity.HIGH, SpeakingRuleAction.NEEDS_IMPROVEMENT,
                    SpeakingRuleCategory.CONTENT, "NO_KOREAN_TRANSCRIPT",
                    "Transcript has no Korean evidence or is empty."));
            return new SpeakingRuleAnalysis(signals);
        }
        repeatedFillers(transcript, signals);
        mixedRegister(transcript, signals);
        missingDiscourseMarkers(transcript, signals);
        return new SpeakingRuleAnalysis(signals);
    }

    private void repeatedFillers(String transcript, List<SpeakingRuleSignal> signals) {
        int total = 0;
        for (String filler : FILLERS) {
            total += countToken(transcript, filler);
        }
        if (total >= 4) {
            signals.add(signal(SpeakingRuleSeverity.MEDIUM, SpeakingRuleAction.SUGGESTION,
                    SpeakingRuleCategory.FLUENCY, "REPEATED_FILLERS",
                    "Repeated filler words detected; evaluate fluency and listener burden conservatively."));
        }
    }

    private void mixedRegister(String transcript, List<SpeakingRuleSignal> signals) {
        boolean polite = containsAny(transcript, "요", "습니다", "니다", "세요");
        boolean casual = containsAny(transcript, "해.", "했어", "야.", "거야", "싶어", "좋아");
        if (polite && casual) {
            signals.add(signal(SpeakingRuleSeverity.MEDIUM, SpeakingRuleAction.NEEDS_IMPROVEMENT,
                    SpeakingRuleCategory.REGISTER, "MIXED_REGISTER_ENDINGS",
                    "Mixed polite and casual ending style detected; evaluate register consistency."));
        }
    }

    private void missingDiscourseMarkers(String transcript, List<SpeakingRuleSignal> signals) {
        if (transcript.length() < 80) {
            return;
        }
        boolean hasMarker = DISCOURSE_MARKERS.stream().anyMatch(transcript::contains);
        if (!hasMarker) {
            signals.add(signal(SpeakingRuleSeverity.LOW, SpeakingRuleAction.SUGGESTION,
                    SpeakingRuleCategory.COHERENCE, "NO_DISCOURSE_MARKERS",
                    "Long answer has no common discourse markers; evaluate organization carefully."));
        }
    }

    private static int countToken(String transcript, String token) {
        int count = 0;
        int index = transcript.indexOf(token);
        while (index >= 0) {
            count++;
            index = transcript.indexOf(token, index + token.length());
        }
        return count;
    }

    private static boolean containsAny(String transcript, String... values) {
        for (String value : values) {
            if (transcript.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static SpeakingRuleSignal signal(
            SpeakingRuleSeverity severity,
            SpeakingRuleAction action,
            SpeakingRuleCategory category,
            String code,
            String message
    ) {
        return new SpeakingRuleSignal(severity, action, category, code, message);
    }

    public enum SpeakingRuleSeverity {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum SpeakingRuleAction {
        NEEDS_IMPROVEMENT,
        SUGGESTION,
        INFO
    }

    public enum SpeakingRuleCategory {
        CONTENT,
        VOCABULARY,
        GRAMMAR,
        REGISTER,
        COHERENCE,
        FLUENCY,
        PRONUNCIATION
    }

    public record SpeakingRuleSignal(
            SpeakingRuleSeverity severity,
            SpeakingRuleAction action,
            SpeakingRuleCategory category,
            String code,
            String message
    ) {}

    public record SpeakingRuleAnalysis(List<SpeakingRuleSignal> signals) {
        public SpeakingRuleAnalysis {
            signals = signals == null ? List.of() : List.copyOf(signals);
        }
    }
}
