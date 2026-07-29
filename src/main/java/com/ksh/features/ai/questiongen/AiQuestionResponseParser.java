package com.ksh.features.ai.questiongen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftOption;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftQuestion;
import com.ksh.features.tests.entity.Question;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Recovers the JSON object from an AI reply and validates every grading-relevant field.
 */
@Component
public class AiQuestionResponseParser {

    static final int MIN_OPTIONS = 2;
    static final int MAX_OPTIONS = 6;
    static final int MAX_RESPONSE_CHARS = 200_000;
    static final int MAX_QUESTION_CHARS = 2_000;
    static final int MAX_OPTION_CHARS = 1_000;
    static final int MAX_EXPLANATION_CHARS = 4_000;

    private static final String MSG_INVALID =
            "AI trả về dữ liệu không hợp lệ, vui lòng thử lại";
    private static final String MSG_COUNT =
            "AI không sinh đúng số lượng câu hỏi đã yêu cầu, vui lòng thử lại";
    private static final String MSG_TYPE =
            "AI trả về loại câu hỏi không đúng yêu cầu, vui lòng thử lại";
    private static final String MSG_OPTIONS =
            "AI trả về câu hỏi có đáp án không hợp lệ, vui lòng thử lại";
    private static final String MSG_TOO_LONG =
            "AI trả về nội dung quá dài, vui lòng giảm phạm vi tài liệu và thử lại";

    private final ObjectMapper objectMapper;

    public AiQuestionResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses a model response and requires exactly the requested count and type.
     */
    public List<DraftQuestion> parse(String reply, int expectedCount, String expectedType) {
        String json = extractJsonObject(reply);
        if (json.length() > MAX_RESPONSE_CHARS) {
            throw new IllegalArgumentException(MSG_TOO_LONG);
        }

        JsonNode root = readJson(json);
        JsonNode questions = root.path("questions");
        if (!questions.isArray() || questions.size() != expectedCount) {
            throw new IllegalArgumentException(MSG_COUNT);
        }

        List<DraftQuestion> drafts = new ArrayList<>(questions.size());
        for (JsonNode node : questions) {
            drafts.add(toDraft(node, expectedType));
        }
        return List.copyOf(drafts);
    }

    private static String extractJsonObject(String reply) {
        if (reply == null || reply.length() > MAX_RESPONSE_CHARS + 20_000) {
            throw new IllegalArgumentException(MSG_INVALID);
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException(MSG_INVALID);
        }
        return reply.substring(start, end + 1);
    }

    private JsonNode readJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException(MSG_INVALID);
            }
            return root;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(MSG_INVALID);
        }
    }

    private static DraftQuestion toDraft(JsonNode node, String expectedType) {
        String type = text(node, "type");
        if (!Question.TYPE_MCQ.equals(type) && !Question.TYPE_MR.equals(type)) {
            throw new IllegalArgumentException(MSG_TYPE);
        }
        if (!type.equals(expectedType)) {
            throw new IllegalArgumentException(MSG_TYPE);
        }

        String content = requiredBoundedText(node, "content", MAX_QUESTION_CHARS);
        String explanation = optionalBoundedText(node, "explanation", MAX_EXPLANATION_CHARS);
        JsonNode options = node.path("options");
        if (!options.isArray() || options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException(MSG_OPTIONS);
        }

        List<DraftOption> result = new ArrayList<>(options.size());
        Set<String> uniqueOptions = new HashSet<>();
        int correctCount = 0;
        for (JsonNode option : options) {
            String optionContent = requiredBoundedText(option, "content", MAX_OPTION_CHARS);
            if (!option.path("correct").isBoolean()) {
                throw new IllegalArgumentException(MSG_OPTIONS);
            }
            String normalized = optionContent.toLowerCase(Locale.ROOT);
            if (!uniqueOptions.add(normalized)) {
                throw new IllegalArgumentException(MSG_OPTIONS);
            }
            boolean correct = option.path("correct").booleanValue();
            if (correct) {
                correctCount++;
            }
            result.add(new DraftOption(optionContent, correct));
        }

        if (Question.TYPE_MCQ.equals(type) && correctCount != 1) {
            throw new IllegalArgumentException(MSG_OPTIONS);
        }
        if (Question.TYPE_MR.equals(type)
                && (correctCount < 2 || correctCount >= result.size())) {
            throw new IllegalArgumentException(MSG_OPTIONS);
        }
        return new DraftQuestion(type, content, explanation, List.copyOf(result));
    }

    private static String requiredBoundedText(JsonNode node, String field, int maxChars) {
        String value = text(node, field);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(MSG_INVALID);
        }
        if (value.length() > maxChars) {
            throw new IllegalArgumentException(MSG_TOO_LONG);
        }
        rejectHtml(value);
        return value;
    }

    private static String optionalBoundedText(JsonNode node, String field, int maxChars) {
        String value = text(node, field);
        if (value.length() > maxChars) {
            throw new IllegalArgumentException(MSG_TOO_LONG);
        }
        rejectHtml(value);
        return value.isEmpty() ? null : value;
    }

    private static void rejectHtml(String value) {
        if (!value.isEmpty()
                && Jsoup.parseBodyFragment(value).body().childrenSize() > 0) {
            throw new IllegalArgumentException(MSG_INVALID);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.textValue().trim() : "";
    }
}
