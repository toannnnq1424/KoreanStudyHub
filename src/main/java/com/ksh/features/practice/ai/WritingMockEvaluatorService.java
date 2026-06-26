package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WritingMockEvaluatorService {

    private final ObjectMapper objectMapper;

    public WritingMockEvaluatorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String evaluate(String prompt,
                           String learnerAnswer,
                           WritingRuleEngine.RuleAnalysis ruleAnalysis,
                           String reason) {
        try {
            String answer = learnerAnswer == null ? "" : learnerAnswer.trim();
            boolean spam = isClearlyInvalid(answer);
            double score = spam ? 1.0 : heuristicScore(answer, ruleAnalysis);
            double rawMax = WritingScoreMatrix.rawScoreMax(ruleAnalysis.taskType());
            double rawScore = WritingScoreMatrix.rawScoreFromNormalized(score, ruleAnalysis.taskType());

            List<Map<String, Object>> strengths = spam ? List.of() : mockStrengths(answer, ruleAnalysis);
            List<Map<String, Object>> needs = mockNeeds(answer, ruleAnalysis);
            score = WritingScoreMatrix.backendScoreFromEvidence(score, strengths.size(), needs.size());
            rawScore = WritingScoreMatrix.rawScoreFromNormalized(score, ruleAnalysis.taskType());

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("score", score);
            root.put("raw_score", rawScore);
            root.put("raw_score_max", rawMax);
            root.put("task_type", ruleAnalysis.taskType());
            root.put("summary", spam
                    ? "[SPAM_DETECTED] Bài làm không hợp lệ hoặc chưa đủ dữ liệu tiếng Hàn để chấm."
                    : "KSH đang chạy Mock Evaluator vì " + reason + " Kết quả này dựa trên đếm ký tự, blacklist văn nói và heuristic cục bộ.");
            root.put("rubric_scores", mockRubrics(score, ruleAnalysis));
            root.put("strengths", strengths);
            root.put("needs_improvement", needs);
            root.put("student_text", answer);
            root.put("upgraded_answer", upgraded(answer, ruleAnalysis));
            root.put("upgraded_answer_annotated", "");
            root.put("sample_answer", sampleAnswer(ruleAnalysis.taskType()));
            root.put("sentence_rewrites", mockRewrites(answer, ruleAnalysis));
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build mock writing evaluation.", ex);
        }
    }

    private static boolean isClearlyInvalid(String answer) {
        if (answer.length() < 8) {
            return true;
        }
        String lower = answer.toLowerCase();
        return lower.matches(".*(asdf|qwer|fuck|shit).*") || !lower.matches(".*[가-힣].*");
    }

    private static double heuristicScore(String answer, WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        double score = 5.0;
        if (answer.length() >= 120) score += 0.5;
        if (answer.length() >= 220) score += 0.5;
        if (answer.contains("따라서") || answer.contains("그러므로") || answer.contains("반면에")) score += 0.5;
        if (answer.contains("해요") || answer.contains("했어요") || answer.contains("같아요")) score -= 0.5;
        if (ruleAnalysis.charCountWarning().startsWith("OK:")) score += 0.5;
        if (ruleAnalysis.charCountWarning().startsWith("CRITICAL:")) score -= 1.5;
        if (ruleAnalysis.charCountWarning().startsWith("WARNING:")) score -= 0.5;
        score -= Math.min(1.5, ruleAnalysis.ruleViolations().size() * 0.25);
        return WritingScoreMatrix.clampAndRound(score);
    }

    private static List<Map<String, Object>> mockRubrics(double score, WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        return List.of(
                rubric(WritingPromptRules.RUBRIC_CONTENT, score, "Mock: đánh giá dựa trên mức độ có nội dung tiếng Hàn và độ dài bài làm."),
                rubric(WritingPromptRules.RUBRIC_STRUCTURE, Math.max(1.0, score - 0.5), "Mock: cần AI thật để đọc sâu logic triển khai; hiện hệ thống chỉ kiểm tra dấu hiệu bố cục cơ bản."),
                rubric(WritingPromptRules.RUBRIC_LANGUAGE, Math.max(1.0, score - Math.min(1.0, ruleAnalysis.ruleViolations().size() * 0.25)), "Mock: đã phát hiện khẩu ngữ/cảnh báo ký tự bằng rule engine.")
        );
    }

    private static List<Map<String, Object>> mockStrengths(String answer, WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (answer.contains("한국어") || answer.contains("한국")) {
            rows.add(finding("W_TOPIC_SPECIFIC_EXPRESSIONS", "한국어", "Bài có từ vựng đúng chủ đề học tiếng Hàn.", ""));
        }
        if (ruleAnalysis.charCountWarning().startsWith("OK:")) {
            rows.add(finding("W_SENTENCE_VARIETY",
                    answer.substring(0, Math.min(answer.length(), 20)),
                    "Dung lượng bài nằm trong vùng yêu cầu của dạng câu hỏi.",
                    ""));
        }
        if (answer.contains("따라서") || answer.contains("그러므로") || answer.contains("반면에")) {
            rows.add(finding("W_ADVANCED_GRAMMAR_STRUCTURES", answer.contains("따라서") ? "따라서" : "그러므로", "Có dùng từ nối phù hợp văn viết.", ""));
        }
        return rows;
    }

    private static List<Map<String, Object>> mockNeeds(String answer, WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WritingRuleEngine.RuleViolation violation : ruleAnalysis.ruleViolations()) {
            rows.add(finding(
                    "W_REGISTER_CONSISTENCY_ISSUES",
                    violation.evidence(),
                    "Đây là biểu hiện khẩu ngữ; trong TOPIK Writing nên chuyển sang văn viết.",
                    violation.suggestion()
            ));
        }
        if (!ruleAnalysis.charCountWarning().startsWith("OK:") && !"GENERAL".equals(ruleAnalysis.taskType())) {
            rows.add(finding(
                    "W_SENTENCE_STRUCTURE_ISSUES",
                    answer.isBlank() ? "글자 수" : answer.substring(0, Math.min(answer.length(), 20)),
                    ruleAnalysis.charCountWarning(),
                    "Điều chỉnh dung lượng đúng yêu cầu đề bài."
            ));
        }
        return rows;
    }

    private static String upgraded(String answer, WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        if (answer == null || answer.isBlank()) {
            return "한국어 학습의 목적을 명확히 밝히고, 앞으로의 학습 계획을 구체적으로 제시할 필요가 있다.";
        }
        String upgraded = answer
                .replace("진짜", "매우")
                .replace("되게", "상당히")
                .replace("근데", "그러나")
                .replace("해요", "한다")
                .replace("했어요", "하였다")
                .replace("같아요", "다고 볼 수 있다");
        if (upgraded.equals(answer) && !ruleAnalysis.ruleViolations().isEmpty()) {
            upgraded += " 문어체 표현을 더 일관되게 사용할 필요가 있다.";
        }
        return upgraded;
    }

    private static String sampleAnswer(String taskType) {
        if ("Q53".equals(taskType)) {
            return "제시된 자료에 따르면 전체적인 수치는 일정한 변화 양상을 보인다. 가장 두드러진 특징은 첫 번째 항목이 다른 항목보다 높은 비율을 차지한다는 점이다. 반면 두 번째 항목은 비교적 낮은 수준에 머물렀다. 이러한 결과를 통해 해당 현상이 시간의 흐름에 따라 점차 뚜렷해지고 있음을 알 수 있다.";
        }
        if ("Q54".equals(taskType)) {
            return "현대 사회에서는 다양한 사회적 변화로 인해 새로운 문제가 나타나고 있다. 이러한 현상은 개인의 생활 방식뿐만 아니라 공동체의 가치관에도 영향을 미친다. 따라서 문제의 원인을 정확히 파악하고 현실적인 해결 방안을 마련하는 것이 중요하다. 특히 교육과 제도적 지원이 함께 이루어진다면 부정적인 영향을 줄이고 긍정적인 변화를 이끌어 낼 수 있을 것이다.";
        }
        return "한국어를 효과적으로 학습하기 위해서는 학습 목적을 분명히 세우고 꾸준히 실천하는 태도가 필요하다. 또한 읽기, 듣기, 쓰기 연습을 균형 있게 진행한다면 실제 의사소통 능력도 함께 향상될 수 있다.";
    }

    private static List<Map<String, Object>> mockRewrites(String answer, WritingRuleEngine.RuleAnalysis ruleAnalysis) {
        if (answer == null || answer.isBlank() || ruleAnalysis.ruleViolations().isEmpty()) {
            return List.of();
        }
        WritingRuleEngine.RuleViolation violation = ruleAnalysis.ruleViolations().get(0);
        return List.of(Map.of(
                "original", violation.evidence(),
                "upgraded", violation.suggestion(),
                "reason", "Chuyển khẩu ngữ sang văn viết phù hợp TOPIK Writing."
        ));
    }

    private static Map<String, Object> rubric(String name, double score, String feedback) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("score", WritingScoreMatrix.clampAndRound(score));
        row.put("feedback", feedback);
        return row;
    }

    private static Map<String, Object> finding(String criterionId, String evidence, String explanation, String correction) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("criterionId", criterionId);
        row.put("evidence", evidence);
        row.put("explanationVi", explanation);
        row.put("correction", correction);
        return row;
    }
}
