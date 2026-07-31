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
    void acceptsCommonFreeProviderShapesAndIgnoresSurroundingProse() {
        String reply = """
                Tôi đã tạo dữ liệu:
                [{"term":"문화","definition":"văn hóa"},
                 {"question":"교류","answer":"giao lưu"}]
                Chúc bạn học tốt.""";

        List<AiFlashcardGenDtos.GeneratedCardRow> rows = parser.parse(reply);

        assertEquals(2, rows.size());
        assertEquals("문화", rows.get(0).front());
        assertEquals("giao lưu", rows.get(1).back());
    }

    @Test
    void findsNestedCardsWithoutCombiningUnrelatedJsonObjects() {
        String reply = """
                metadata {"attempt":1}
                result {"data":{"flashcards":[{"word":"한글","meaning":"chữ Hàn"}]}}
                footer {"done":true}""";

        List<AiFlashcardGenDtos.GeneratedCardRow> rows = parser.parse(reply);

        assertEquals(1, rows.size());
        assertEquals("한글", rows.get(0).front());
    }

    @Test
    void salvagesValidRowsWhenOneProviderRowIsUnusable() {
        List<AiFlashcardGenDtos.GeneratedCardRow> rows = parser.parse("""
                {"cards":[{"front":"A","back":42},
                          {"front":"B","back":"Hợp lệ"}]}""");

        assertEquals(1, rows.size());
        assertEquals("B", rows.get(0).front());
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
