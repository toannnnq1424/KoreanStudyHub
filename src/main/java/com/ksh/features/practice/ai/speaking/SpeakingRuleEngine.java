package com.ksh.features.practice.ai.speaking;

import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class SpeakingRuleEngine {
    private static final List<String> DISCOURSE_MARKERS = List.of(
            "먼저", "그리고", "또한", "하지만", "그래서", "예를 들면", "마지막으로", "제 생각에는");
    private static final Pattern HANGUL =
            Pattern.compile(".*\\p{IsHangul}.*");
    private static final Pattern POLITE_SENTENCE_ENDING = Pattern.compile(
            "(?:요|니다|습니다|세요|십시오)(?=\\s*(?:[.!?。！？]|$))");
    private static final Pattern CASUAL_SENTENCE_ENDING = Pattern.compile(
            "(?:했어|거야|싶어|좋아|해|이야)(?=\\s*(?:[.!?。！？]|$))");
    private static final Pattern CASUAL_SS_EO_SENTENCE_ENDING_NFD = Pattern.compile(
            "\u11BB\u110B\u1165(?=\\s*(?:[.!?。！？]|$))");

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
                    SpeakingRuleCategory.CONTENT, "TEXT_FALLBACK_TRANSCRIPT_ONLY",
                    "Bản nhập chữ chỉ cung cấp bằng chứng ngôn ngữ; không tạo nhận định âm học."));
        }
        if (transcriptConfidence != null
                && transcriptConfidence.compareTo(new BigDecimal("0.50")) < 0) {
            signals.add(signal(SpeakingRuleSeverity.MEDIUM, SpeakingRuleAction.INFO,
                    SpeakingRuleCategory.CONTENT, "LOW_TRANSCRIPT_CONFIDENCE",
                    "Bản chép lời có độ tin cậy thấp; chỉ số này là dữ liệu nguồn, không phải bằng chứng chấm điểm hay âm học."));
        }
        if (transcript == null || !HANGUL.matcher(transcript).matches()) {
            signals.add(signal(SpeakingRuleSeverity.HIGH, SpeakingRuleAction.SUGGESTION,
                    SpeakingRuleCategory.CONTENT, "NO_KOREAN_TRANSCRIPT",
                    "Bản chép lời trống hoặc chưa có bằng chứng tiếng Hàn; cần kiểm tra trước khi đánh giá."));
            return new SpeakingRuleAnalysis(signals);
        }
        mixedRegister(transcript, signals);
        missingDiscourseMarkers(transcript, signals);
        return new SpeakingRuleAnalysis(signals);
    }

    private void mixedRegister(String transcript, List<SpeakingRuleSignal> signals) {
        boolean polite = POLITE_SENTENCE_ENDING.matcher(transcript).find();
        boolean casual = CASUAL_SENTENCE_ENDING.matcher(transcript).find()
                || CASUAL_SS_EO_SENTENCE_ENDING_NFD.matcher(
                Normalizer.normalize(
                        transcript,
                        Normalizer.Form.NFD)).find();
        if (polite && casual) {
            signals.add(signal(SpeakingRuleSeverity.MEDIUM, SpeakingRuleAction.SUGGESTION,
                    SpeakingRuleCategory.REGISTER, "MIXED_REGISTER_ENDINGS",
                    "Có tín hiệu đuôi câu kính ngữ và thân mật cùng xuất hiện; hãy kiểm tra tính nhất quán của lối nói."));
        }
    }

    private void missingDiscourseMarkers(String transcript, List<SpeakingRuleSignal> signals) {
        if (transcript.length() < 80) {
            return;
        }
        boolean hasMarker = DISCOURSE_MARKERS.stream()
                .anyMatch(marker -> containsBoundedPhrase(
                        transcript, marker));
        if (!hasMarker) {
            signals.add(signal(SpeakingRuleSeverity.LOW, SpeakingRuleAction.SUGGESTION,
                    SpeakingRuleCategory.COHERENCE, "NO_DISCOURSE_MARKERS",
                    "Câu trả lời dài chưa có dấu hiệu từ nối diễn ngôn phổ biến; hãy xem xét tổ chức ý một cách thận trọng."));
        }
    }

    private static boolean containsBoundedPhrase(
            String transcript,
            String marker
    ) {
        int fromIndex = 0;
        while (fromIndex <= transcript.length() - marker.length()) {
            int index = transcript.indexOf(marker, fromIndex);
            if (index < 0) {
                return false;
            }
            int afterIndex = index + marker.length();
            boolean leftBoundary = index == 0
                    || !lexicalContinuation(
                    transcript.codePointBefore(index));
            boolean rightBoundary = afterIndex == transcript.length()
                    || !lexicalContinuation(
                    transcript.codePointAt(afterIndex));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            fromIndex = index + Character.charCount(
                    transcript.codePointAt(index));
        }
        return false;
    }

    private static boolean lexicalContinuation(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint)
                || codePoint == '_'
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.CONNECTOR_PUNCTUATION;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(
                value, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ");
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
        COHERENCE
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
