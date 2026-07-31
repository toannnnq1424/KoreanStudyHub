package com.ksh.features.ai.flashcardgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiFlashcardResponseParserTest {

    private final AiFlashcardResponseParser parser =
            new AiFlashcardResponseParser(new ObjectMapper());

    @Test
    void parsesCleanAndFencedJson() {
        String json = """
                {"cards":[{"front":"Đệ quy","back":"Hàm tự gọi chính nó"},
                          {"front":"Vòng lặp","back":"Khối lệnh chạy lặp lại"}]}""";

        assertEquals(2, parser.parse(json).size());
        assertEquals("Đệ quy", parser.parse("```json\n" + json + "\n```").get(0).front());
    }

    @Test
    void trimsAndDeduplicatesFrontsIgnoringCase() {
        List<AiFlashcardGenDtos.GeneratedCardRow> rows = parser.parse("""
                {"cards":[{"front":"  Đệ quy ","back":" A "},
                          {"front":"đệ QUY","back":"B"},
                          {"front":"Vòng lặp","back":"C"}]}""");

        assertEquals(2, rows.size());
        assertEquals("A", rows.get(0).back());
    }

    @Test
    void rejectsMalformedOrUnusableCards() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("không có JSON"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("{\"cards\":[]}"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("{\"cards\":[{\"front\":\"A\",\"back\":\"\"}]}"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("{\"cards\":[{\"front\":\"A\",\"back\":42}]}"));
    }
}
