package com.ksh.features.ai.flashcardgen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AiFlashcardResponseParser {

    static final int MAX_SIDE_CHARS = 500;
    private static final String MSG_NOT_JSON =
            "AI trả về dữ liệu không hợp lệ, vui lòng thử lại";
    private static final String MSG_NO_CARDS =
            "AI không sinh được thẻ nào từ tài liệu này, vui lòng thử lại";

    private final ObjectMapper objectMapper;

    public AiFlashcardResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<AiFlashcardGenDtos.GeneratedCardRow> parse(String reply) {
        JsonNode cards = findCards(readJson(reply));
        if (!cards.isArray() || cards.isEmpty()) {
            throw new IllegalArgumentException(MSG_NO_CARDS);
        }

        List<AiFlashcardGenDtos.GeneratedCardRow> rows = new ArrayList<>();
        Set<String> seenFronts = new HashSet<>();
        for (JsonNode node : cards) {
            String front = firstText(node, "front", "term", "question", "word", "prompt");
            String back = firstText(node, "back", "definition", "answer", "meaning", "explanation");
            if (front.isEmpty() || back.isEmpty()
                    || front.length() > MAX_SIDE_CHARS || back.length() > MAX_SIDE_CHARS) {
                continue;
            }
            if (seenFronts.add(front.toLowerCase(Locale.ROOT))) {
                rows.add(new AiFlashcardGenDtos.GeneratedCardRow(front, back));
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(MSG_NO_CARDS);
        }
        return List.copyOf(rows);
    }

    private JsonNode readJson(String reply) {
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException(MSG_NOT_JSON);
        }

        JsonNode whole = tryRead(reply.trim());
        if (hasCards(whole)) {
            return whole;
        }

        for (int start = 0; start < reply.length(); start++) {
            char opening = reply.charAt(start);
            if (opening != '{' && opening != '[') {
                continue;
            }
            int end = matchingJsonEnd(reply, start);
            if (end < 0) {
                continue;
            }
            JsonNode candidate = tryRead(reply.substring(start, end + 1));
            if (hasCards(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(MSG_NOT_JSON);
    }

    private JsonNode tryRead(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static JsonNode findCards(JsonNode root) {
        if (root == null) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        if (root.isArray()) {
            return root;
        }
        for (String field : List.of("cards", "flashcards")) {
            JsonNode value = root.path(field);
            if (value.isArray()) {
                return value;
            }
        }
        JsonNode data = root.path("data");
        if (data.isObject()) {
            for (String field : List.of("cards", "flashcards")) {
                JsonNode value = data.path(field);
                if (value.isArray()) {
                    return value;
                }
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static boolean hasCards(JsonNode root) {
        return findCards(root).isArray();
    }

    private static String firstText(JsonNode node, String... fields) {
        return Arrays.stream(fields)
                .map(node::path)
                .filter(JsonNode::isTextual)
                .map(value -> value.asText().trim())
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse("");
    }

    private static int matchingJsonEnd(String value, int start) {
        char opening = value.charAt(start);
        char closing = opening == '{' ? '}' : ']';
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == opening) {
                depth++;
            } else if (current == closing && --depth == 0) {
                return index;
            }
        }
        return -1;
    }
}
