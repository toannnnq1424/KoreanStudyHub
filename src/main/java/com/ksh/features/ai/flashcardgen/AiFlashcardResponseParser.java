package com.ksh.features.ai.flashcardgen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private static final String MSG_EMPTY_SIDE =
            "AI trả về thẻ có mặt trước hoặc mặt sau rỗng, vui lòng thử lại";
    private static final String MSG_SIDE_TOO_LONG =
            "AI trả về thẻ có nội dung quá dài, vui lòng thử lại";

    private final ObjectMapper objectMapper;

    public AiFlashcardResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<AiFlashcardGenDtos.GeneratedCardRow> parse(String reply) {
        JsonNode cards = readJson(extractJsonObject(reply)).path("cards");
        if (!cards.isArray() || cards.isEmpty()) {
            throw new IllegalArgumentException(MSG_NO_CARDS);
        }

        List<AiFlashcardGenDtos.GeneratedCardRow> rows = new ArrayList<>();
        Set<String> seenFronts = new HashSet<>();
        for (JsonNode node : cards) {
            String front = requireSide(text(node, "front"));
            String back = requireSide(text(node, "back"));
            if (seenFronts.add(front.toLowerCase(Locale.ROOT))) {
                rows.add(new AiFlashcardGenDtos.GeneratedCardRow(front, back));
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(MSG_NO_CARDS);
        }
        return List.copyOf(rows);
    }

    private static String extractJsonObject(String reply) {
        if (reply == null) {
            throw new IllegalArgumentException(MSG_NOT_JSON);
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException(MSG_NOT_JSON);
        }
        return reply.substring(start, end + 1);
    }

    private JsonNode readJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException(MSG_NOT_JSON);
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(MSG_NOT_JSON);
        }
    }

    private static String requireSide(String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(MSG_EMPTY_SIDE);
        }
        if (value.length() > MAX_SIDE_CHARS) {
            throw new IllegalArgumentException(MSG_SIDE_TOO_LONG);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }
}
