package com.ksh.features.practice.ai.writing;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WritingRuleEngine {

    public enum RuleSeverity {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum RuleAction {
        SUGGESTION
    }

    private static final List<BlacklistEntry> SPOKEN_BLACKLIST = new ArrayList<>();

    static {
        SPOKEN_BLACKLIST.add(new BlacklistEntry("근데", "그러나 / 그런데 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("진짜", "매우 / 정말로 / 실로 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("되게", "매우 / 상당히 / 무척 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("엄청", "매우 / 대단히 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("했어요", "했다 / 하였다 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("해요", "-ㄴ다 / -는다 / 한다 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("있어요", "있다 / 있습니다 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("없어요", "없다 / 없습니다 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("이에요", "이다 / 입니다 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("예요", "이다 / 입니다 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("어떤 것 같아요", "어떠하다 / ~다고 볼 수 있다 (문어체)", RuleSeverity.HIGH));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("같아요", "~ㄴ 것 같다 / ~다고 생각된다 (문어체)", RuleSeverity.HIGH));

        SPOKEN_BLACKLIST.add(new BlacklistEntry("좀", "약간 / 다소 (문어체)", RuleSeverity.MEDIUM));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("이랑", "~와 / ~과 (문어체 조사)", RuleSeverity.MEDIUM));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("한테", "~에게 (문어체 조사)", RuleSeverity.MEDIUM));
    }

    public RuleAnalysis analyze(String prompt, String learnerAnswer) {
        return analyze(prompt, learnerAnswer, detectTaskType(prompt));
    }

    public RuleAnalysis analyze(String prompt, String learnerAnswer, String resolvedTaskType) {
        String taskType = resolvedTaskType == null ? "GENERAL" : resolvedTaskType;
        String answer = Normalizer.normalize(
                learnerAnswer == null ? "" : learnerAnswer,
                Normalizer.Form.NFC);
        int charCount = countChars(answer);
        List<RuleViolation> violations = detectSpokenLanguage(answer, taskType);
        return new RuleAnalysis(taskType, charCount, buildCharCountWarning(charCount, taskType), violations);
    }

    public static String detectTaskType(String prompt) {
        String value = Normalizer.normalize(
                prompt == null ? "" : prompt,
                Normalizer.Form.NFC).toLowerCase();
        if (value.contains("51") || value.contains("52") || value.contains("괄호")
                || value.contains("(ㄱ)") || value.contains("(ㄴ)")) {
            return "Q51_52";
        }
        if (value.contains("53") || value.contains("200-300자") || value.contains("200~300자")
                || value.contains("200-300") || value.contains("200~300")) {
            return "Q53";
        }
        if (value.contains("54") || value.contains("600-700자") || value.contains("600~700자")
                || value.contains("600-700") || value.contains("600~700")) {
            return "Q54";
        }
        return "GENERAL";
    }

    private static int countChars(String answer) {
        String compact = answer.replace("\r", "").replace("\n", "");
        return compact.codePointCount(0, compact.length());
    }

    private static List<RuleViolation> detectSpokenLanguage(String answer, String taskType) {
        List<RuleViolation> violations = new ArrayList<>();
        List<MatchedRange> matchedRanges = new ArrayList<>();
        for (BlacklistEntry entry : SPOKEN_BLACKLIST) {
            MatchedRange matched = firstNonOverlappingRange(
                    answer, entry.spoken(), matchedRanges);
            if (matched == null) {
                continue;
            }
            violations.add(new RuleViolation(
                    answer.substring(matched.start(), matched.end()),
                    entry.formalAlternative(),
                    messageFor(entry, taskType),
                    entry.severity(),
                    RuleAction.SUGGESTION
            ));
            matchedRanges.add(matched);
        }
        return violations;
    }

    private static MatchedRange firstNonOverlappingRange(
            String answer,
            String evidence,
            List<MatchedRange> matchedRanges
    ) {
        boolean suffixForm = Set.of(
                "했어요", "해요", "있어요", "없어요", "이에요", "예요",
                "어떤 것 같아요", "같아요", "이랑", "한테").contains(evidence);
        String expression = (suffixForm ? "" : "(?<![\\p{L}\\p{N}])")
                + Pattern.quote(evidence)
                + "(?![\\p{L}\\p{N}])";
        Matcher matcher = Pattern.compile(
                expression,
                Pattern.UNICODE_CHARACTER_CLASS).matcher(answer);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (matchedRanges.stream().noneMatch(range -> range.overlaps(start, end))) {
                return new MatchedRange(start, end);
            }
        }
        return null;
    }

    private static String messageFor(BlacklistEntry entry, String taskType) {
        return "Tín hiệu tư vấn văn phong (" + taskType + ", "
                + entry.severity() + "): kiểm tra \"" + entry.spoken()
                + "\"; gợi ý tiếng Hàn trang trọng: "
                + entry.formalAlternative();
    }

    private static String buildCharCountWarning(int charCount, String taskType) {
        if ("Q53".equals(taskType)) {
            if (charCount < 150) return "Tư vấn: bài có " + charCount + " ký tự, thấp hơn đáng kể phạm vi 200~300 ký tự.";
            if (charCount < 200) return "Tư vấn: bài có " + charCount + " ký tự, thấp hơn phạm vi 200~300 ký tự.";
            if (charCount > 350) return "Tư vấn: bài có " + charCount + " ký tự, vượt đáng kể phạm vi 200~300 ký tự.";
            if (charCount > 300) return "Tư vấn: bài có " + charCount + " ký tự, vượt phạm vi 200~300 ký tự.";
            return "Bài có " + charCount + " ký tự, nằm trong phạm vi 200~300 ký tự.";
        }
        if ("Q54".equals(taskType)) {
            if (charCount < 400) return "Tư vấn: bài có " + charCount + " ký tự, thấp hơn đáng kể phạm vi 600~700 ký tự.";
            if (charCount < 600) return "Tư vấn: bài có " + charCount + " ký tự, thấp hơn phạm vi 600~700 ký tự.";
            if (charCount > 750) return "Tư vấn: bài có " + charCount + " ký tự, vượt đáng kể phạm vi 600~700 ký tự.";
            if (charCount > 700) return "Tư vấn: bài có " + charCount + " ký tự, vượt phạm vi 600~700 ký tự.";
            return "Bài có " + charCount + " ký tự, nằm trong phạm vi 600~700 ký tự.";
        }
        return "Bài có " + charCount + " ký tự.";
    }

    public record RuleAnalysis(String taskType,
                               int characterCount,
                               String charCountWarning,
                               List<RuleViolation> ruleViolations) {
    }

    public record RuleViolation(String evidence,
                                String suggestion,
                                String message,
                                RuleSeverity severity,
                                RuleAction action) {

        public RuleViolation(String evidence, String suggestion, String message) {
            this(evidence, suggestion, message, RuleSeverity.HIGH, RuleAction.SUGGESTION);
        }
    }

    private record BlacklistEntry(String spoken, String formalAlternative, RuleSeverity severity) {
    }

    private record MatchedRange(int start, int end) {
        boolean overlaps(int otherStart, int otherEnd) {
            return start < otherEnd && otherStart < end;
        }
    }
}
