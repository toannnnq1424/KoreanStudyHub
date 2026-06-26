package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock explanation service for Reading/Listening questions.
 *
 * <p>Activated automatically when:
 * <ul>
 *   <li>OpenAI/Gemini API key is missing</li>
 *   <li>AI quota is exhausted (HTTP 429)</li>
 *   <li>AI is temporarily unavailable (5xx)</li>
 *   <li>Max retries exceeded</li>
 * </ul>
 *
 * <p>Generates a realistic Vietnamese explanation based on the question's
 * stored data (prompt, options, answerKey, explanation) without calling any
 * external API. The response schema matches {@code rl_answer_explanation}
 * so the frontend renders correctly.
 *
 * <p>Mock responses are NOT written to {@code question_explanation_cache}.
 * The next time the user views the result, the system will attempt the real
 * AI call again (cache miss → try AI → if succeeds, cache it).
 */
@Service
public class ReadingListeningMockExplanationService {

    private final ObjectMapper objectMapper;

    public ReadingListeningMockExplanationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a mock explanation for a Reading/Listening question.
     *
     * @param question   the practice question entity
     * @param passageText the passage/transcript associated with the question group
     * @param skillType  "READING" or "LISTENING"
     * @param reason     why we're using mock (shown in reasonVi for dev awareness)
     * @return JSON string matching {@code rl_answer_explanation} schema, never null
     */
    public String explain(PracticeQuestion question, String passageText, String skillType, String optionLabelMode, String reason) {
        try {
            String correctAnswer = question.getAnswerKey() != null ? question.getAnswerKey().trim() : "";
            List<String> options = readOptions(question.getOptionsJson());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meaningVi", "Giải thích câu hỏi và đoạn hội thoại/đọc hiểu liên quan.");
            result.put("evidenceQuote", passageText != null && !passageText.isEmpty() ? passageText : "Không có trích dẫn.");
            result.put("correctReasonVi", question.getExplanation() != null && !question.getExplanation().isBlank()
                    ? question.getExplanation()
                    : "Đáp án đúng được xác định là " + correctAnswer + ".");
            result.put("relatedTranslationVi", "Dịch nghĩa nội dung.");
            
            List<Map<String, String>> eliminatedOptions = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                String key = "ALPHA".equals(optionLabelMode) ? String.valueOf((char)('A' + i)) : String.valueOf(i + 1);
                if (!key.equals(correctAnswer)) {
                    Map<String, String> opt = new LinkedHashMap<>();
                    opt.put("optionKey", key);
                    opt.put("reasonVi", "Phương án " + key + " không đúng với ngữ cảnh.");
                    eliminatedOptions.add(opt);
                }
            }
            result.put("eliminatedOptions", eliminatedOptions);

            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            // Last resort fallback
            return buildMinimalFallback(question);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    private List<String> buildCorrectAnswers(String correctAnswer, String questionType) {
        if (correctAnswer.isBlank()) return List.of();
        // For ordering/matching questions the answer may be comma-separated
        if (correctAnswer.contains(",")) {
            List<String> parts = new ArrayList<>();
            for (String part : correctAnswer.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) parts.add(trimmed);
            }
            return parts;
        }
        return List.of(correctAnswer);
    }

    private Map<String, Object> buildKeywords(String prompt, String passageText) {
        Map<String, Object> keywords = new LinkedHashMap<>();
        // Extract short Korean segments from prompt as question keywords
        List<String> questionKw = extractKoreanKeywords(prompt, 3);
        // Extract short Korean segments from passage as passage keywords
        List<String> passageKw = extractKoreanKeywords(passageText, 3);
        keywords.put("question", questionKw);
        keywords.put("passage", passageKw);
        return keywords;
    }

    private List<Map<String, Object>> buildEvidence(String passageText, String correctAnswer, String prompt) {
        List<Map<String, Object>> evidenceList = new ArrayList<>();

        // Try to find a sentence in passageText that might contain the answer context
        if (passageText != null && !passageText.isBlank()) {
            String[] sentences = passageText.split("[.!?。\\n]+");
            String bestSentence = "";
            int bestScore = 0;
            for (int i = 0; i < sentences.length && i < 20; i++) {
                String s = sentences[i].trim();
                if (s.isBlank()) continue;
                int score = 0;
                // Prefer sentences that contain the correct answer text
                if (!correctAnswer.isBlank() && s.contains(correctAnswer)) score += 3;
                // Prefer longer sentences (more context)
                score += Math.min(s.length() / 20, 3);
                if (score > bestScore) {
                    bestScore = score;
                    bestSentence = s;
                }
            }
            if (!bestSentence.isBlank()) {
                Map<String, Object> ev = new LinkedHashMap<>();
                ev.put("source", "passage");
                ev.put("text", bestSentence.length() > 200 ? bestSentence.substring(0, 200) + "…" : bestSentence);
                ev.put("paragraphIndex", 0);
                ev.put("sentenceIndex", 0);
                evidenceList.add(ev);
            }
        }

        if (evidenceList.isEmpty()) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("source", "passage");
            ev.put("text", "Hệ thống chưa tìm thấy đoạn văn bản nguồn để trích dẫn.");
            ev.put("paragraphIndex", 0);
            ev.put("sentenceIndex", 0);
            evidenceList.add(ev);
        }

        return evidenceList;
    }

    private String buildReasonVi(PracticeQuestion question, List<String> options,
                                  String correctAnswer, String skillType, String reason) {
        // If the question already has a stored explanation, use it
        String storedExplanation = question.getExplanation();
        if (storedExplanation != null && !storedExplanation.isBlank()) {
            return "[Giải thích có sẵn] " + storedExplanation;
        }

        // Generate a meaningful mock explanation
        StringBuilder sb = new StringBuilder();
        sb.append("⚡ Giải thích tạm thời (AI đang bận: ").append(reason).append(")\n\n");

        String skillLabel = "LISTENING".equals(skillType) ? "bài nghe" : "bài đọc";
        String answerDisplay = correctAnswer.isBlank() ? "đáp án đã lưu" : "\"" + correctAnswer + "\"";

        sb.append("Đáp án đúng là ").append(answerDisplay).append(".\n");

        if (!options.isEmpty() && !correctAnswer.isBlank()) {
            // Try to figure out which option index matches
            for (int i = 0; i < options.size(); i++) {
                String opt = options.get(i).trim();
                String optLabel = String.valueOf(i + 1);
                boolean isCorrect = optLabel.equals(correctAnswer) || opt.equalsIgnoreCase(correctAnswer);
                if (isCorrect) {
                    sb.append("Lựa chọn ").append(optLabel).append(" (\"").append(opt)
                            .append("\") là đúng vì nó phù hợp với nội dung ").append(skillLabel).append(".\n");
                    break;
                }
            }
        }

        sb.append("\nNhấn làm lại sau ít phút để nhận giải thích đầy đủ từ AI.");
        return sb.toString();
    }

    private List<Map<String, Object>> buildWrongOptions(List<String> options, String correctAnswer,
                                                         String questionType) {
        if (options.isEmpty() || correctAnswer.isBlank()) return List.of();

        List<Map<String, Object>> wrongOptions = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            String optLabel = String.valueOf(i + 1);
            boolean isCorrect = optLabel.equals(correctAnswer) || options.get(i).equalsIgnoreCase(correctAnswer);
            if (!isCorrect) {
                Map<String, Object> wo = new LinkedHashMap<>();
                wo.put("option", optLabel);
                wo.put("reasonVi", "Lựa chọn " + optLabel + " (\"" + options.get(i) + "\") không phù hợp với nội dung bài. AI sẽ giải thích chi tiết hơn khi hạn ngạch được khôi phục.");
                wrongOptions.add(wo);
            }
        }
        return wrongOptions;
    }

    private String buildTranslationVi(String prompt, String skillType) {
        if (prompt == null || prompt.isBlank()) {
            return "LISTENING".equals(skillType)
                    ? "Nghe đoạn hội thoại và trả lời câu hỏi."
                    : "Đọc đoạn văn và trả lời câu hỏi.";
        }
        // Return prompt trimmed as approximate "translation" placeholder
        String trimmed = prompt.trim();
        return trimmed.length() > 150 ? trimmed.substring(0, 150) + "…" : trimmed;
    }

    /**
     * Extracts up to {@code maxCount} Korean token segments from text.
     * Heuristic: splits on whitespace, keeps tokens with Hangul characters.
     */
    private static List<String> extractKoreanKeywords(String text, int maxCount) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (result.size() >= maxCount) break;
            String clean = token.replaceAll("[^가-힣a-zA-Z0-9]", "").trim();
            if (clean.length() >= 2 && containsKorean(clean)) {
                result.add(clean);
            }
        }
        return result;
    }

    private static boolean containsKorean(String text) {
        for (char c : text.toCharArray()) {
            if (c >= '\uAC00' && c <= '\uD7A3') return true;
            if (c >= '\u1100' && c <= '\u11FF') return true;
        }
        return false;
    }

    private List<String> readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(optionsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String buildMinimalFallback(PracticeQuestion question) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("meaningVi", "Giải thích đang được tải.");
            m.put("evidenceQuote", "Không có dữ liệu.");
            m.put("correctReasonVi", question.getExplanation() != null ? question.getExplanation() : "Giải thích đang được tải.");
            m.put("relatedTranslationVi", "");
            m.put("eliminatedOptions", List.of());
            return objectMapper.writeValueAsString(m);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
