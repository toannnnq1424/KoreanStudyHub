package com.ksh.features.practice.ai.writing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WritingRuleEngine {

    public enum RuleSeverity {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum RuleAction {
        NEEDS_IMPROVEMENT,
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
        SPOKEN_BLACKLIST.add(new BlacklistEntry("랑", "~와 / ~과 (문어체 조사)", RuleSeverity.MEDIUM));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("한테", "~에게 (문어체 조사)", RuleSeverity.MEDIUM));

        SPOKEN_BLACKLIST.add(new BlacklistEntry("그리고", "아울러 / 또한 (격식 있는 연결)", RuleSeverity.LOW));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("하고", "~와 / ~과 또는 ~(으)며 (문어체 연결)", RuleSeverity.LOW));
    }

    public RuleAnalysis analyze(String prompt, String learnerAnswer) {
        return analyze(prompt, learnerAnswer, detectTaskType(prompt));
    }

    public RuleAnalysis analyze(String prompt, String learnerAnswer, String resolvedTaskType) {
        String taskType = resolvedTaskType == null ? "GENERAL" : resolvedTaskType;
        String answer = learnerAnswer == null ? "" : learnerAnswer;
        int charCount = countChars(answer);
        List<RuleViolation> violations = detectSpokenLanguage(answer, taskType);
        return new RuleAnalysis(taskType, charCount, buildCharCountWarning(charCount, taskType), violations);
    }

    public static String detectTaskType(String prompt) {
        String value = prompt == null ? "" : prompt.toLowerCase();
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

    public static long hardViolationCount(List<RuleViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return 0;
        }
        return violations.stream().filter(RuleViolation::hardFinding).count();
    }

    private static int countChars(String answer) {
        return answer.replace("\r", "").replace("\n", "").length();
    }

    private static List<RuleViolation> detectSpokenLanguage(String answer, String taskType) {
        List<RuleViolation> violations = new ArrayList<>();
        List<MatchedRange> matchedRanges = new ArrayList<>();
        for (BlacklistEntry entry : SPOKEN_BLACKLIST) {
            int start = firstNonOverlappingIndex(answer, entry.spoken(), matchedRanges);
            if (start < 0) {
                continue;
            }
            RuleAction action = actionFor(entry.severity(), taskType);
            if (action == null) {
                continue;
            }
            violations.add(new RuleViolation(
                    entry.spoken(),
                    entry.formalAlternative(),
                    messageFor(entry, action, taskType),
                    entry.severity(),
                    action
            ));
            matchedRanges.add(new MatchedRange(start, start + entry.spoken().length()));
        }
        return violations;
    }

    private static int firstNonOverlappingIndex(String answer, String evidence, List<MatchedRange> matchedRanges) {
        int searchFrom = 0;
        while (searchFrom <= answer.length()) {
            int start = answer.indexOf(evidence, searchFrom);
            if (start < 0) {
                return -1;
            }
            int end = start + evidence.length();
            if (matchedRanges.stream().noneMatch(range -> range.overlaps(start, end))) {
                return start;
            }
            searchFrom = Math.max(start + 1, end);
        }
        return -1;
    }

    private static RuleAction actionFor(RuleSeverity severity, String taskType) {
        if (isClozeTask(taskType)) {
            return severity == RuleSeverity.HIGH ? RuleAction.NEEDS_IMPROVEMENT : null;
        }
        if ("GENERAL".equals(taskType)) {
            return severity == RuleSeverity.HIGH ? RuleAction.NEEDS_IMPROVEMENT : RuleAction.SUGGESTION;
        }
        if (severity == RuleSeverity.LOW) {
            return RuleAction.SUGGESTION;
        }
        return RuleAction.NEEDS_IMPROVEMENT;
    }

    private static boolean isClozeTask(String taskType) {
        return "Q51".equals(taskType) || "Q52".equals(taskType) || "Q51_52".equals(taskType);
    }

    private static String messageFor(BlacklistEntry entry, RuleAction action, String taskType) {
        String prefix = action == RuleAction.NEEDS_IMPROVEMENT ? "문어체 대체 권장" : "문어체 제안";
        return prefix + " (" + taskType + ", " + entry.severity() + "): \""
                + entry.spoken() + "\" -> " + entry.formalAlternative();
    }

    private static String buildCharCountWarning(int charCount, String taskType) {
        if ("Q53".equals(taskType)) {
            if (charCount < 150) return "CRITICAL: 글자 수 " + charCount + "자 -> 최소 요건(200자) 미달. 내용 구성 점수 최대 -10점 감점.";
            if (charCount < 200) return "WARNING: 글자 수 " + charCount + "자 -> 200자 미만. 감점 위험.";
            if (charCount > 350) return "WARNING: 글자 수 " + charCount + "자 -> 300자 초과. 감점 위험.";
            return "OK: 글자 수 " + charCount + "자 -> 200~300자 범위 충족.";
        }
        if ("Q54".equals(taskType)) {
            if (charCount < 400) return "CRITICAL: 글자 수 " + charCount + "자 -> 최소 요건(600자) 크게 미달. 내용 구성 점수 최대 -15점 감점.";
            if (charCount < 600) return "WARNING: 글자 수 " + charCount + "자 -> 600자 미만. 감점 위험.";
            if (charCount > 750) return "WARNING: 글자 수 " + charCount + "자 -> 700자 초과. 감점 위험.";
            return "OK: 글자 수 " + charCount + "자 -> 600~700자 범위 충족.";
        }
        return "글자 수: " + charCount + "자.";
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
            this(evidence, suggestion, message, RuleSeverity.HIGH, RuleAction.NEEDS_IMPROVEMENT);
        }

        public boolean hardFinding() {
            return action == RuleAction.NEEDS_IMPROVEMENT;
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
