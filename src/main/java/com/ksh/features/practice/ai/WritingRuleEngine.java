package com.ksh.features.practice.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WritingRuleEngine {

    private static final List<BlacklistEntry> SPOKEN_BLACKLIST = new ArrayList<>();

    static {
        SPOKEN_BLACKLIST.add(new BlacklistEntry("근데", "그러나 / 그런데 (văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("그리고", "아울러 / 또한 (liên kết trang trọng hơn trong nghị luận)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("진짜", "매우 / 정말로 / 실로 (văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("되게", "매우 / 상당히 / 무척 (văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("엄청", "매우 / 대단히 (văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("좀", "약간 / 다소 (văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("~랑", "~와 / ~과 (tiểu từ văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("이랑", "~와 / ~과 (tiểu từ văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("~한테", "~에게 (tiểu từ văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("한테", "~에게 (tiểu từ văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("하고", "~와 / ~과 hoặc ~(으)며 (liên kết văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("했어요", "했다 / 하였다 (đuôi văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("해요", "-ㄴ다 / -는다 / 한다 (đuôi văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("있어요", "있다 / 있습니다 (đuôi văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("없어요", "없다 / 없습니다 (đuôi văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("이에요", "이다 / 입니다 (đuôi văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("예요", "이다 / 입니다 (đuôi văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("어떤 것 같아요", "어떠하다 / ~다고 볼 수 있다 (văn viết)"));
        SPOKEN_BLACKLIST.add(new BlacklistEntry("같아요", "~ㄴ 것 같다 / ~다고 생각된다 (văn viết)"));
    }

    public RuleAnalysis analyze(String prompt, String learnerAnswer) {
        return analyze(prompt, learnerAnswer, detectTaskType(prompt));
    }

    public RuleAnalysis analyze(String prompt, String learnerAnswer, String resolvedTaskType) {
        String taskType = resolvedTaskType == null ? "GENERAL" : resolvedTaskType;
        String answer = learnerAnswer == null ? "" : learnerAnswer;
        int charCount = countChars(answer);
        List<RuleViolation> violations = detectSpokenLanguage(answer);
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

    private static int countChars(String answer) {
        return answer.replace("\r", "").replace("\n", "").length();
    }

    private static List<RuleViolation> detectSpokenLanguage(String answer) {
        List<RuleViolation> violations = new ArrayList<>();
        for (BlacklistEntry entry : SPOKEN_BLACKLIST) {
            if (answer.contains(entry.spoken())) {
                violations.add(new RuleViolation(
                        entry.spoken(),
                        entry.formalAlternative(),
                        "\"" + entry.spoken() + "\" -> 문어체 대체 권장: " + entry.formalAlternative()
                ));
            }
        }
        return violations;
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

    public record RuleViolation(String evidence, String suggestion, String message) {
    }

    private record BlacklistEntry(String spoken, String formalAlternative) {
    }
}
