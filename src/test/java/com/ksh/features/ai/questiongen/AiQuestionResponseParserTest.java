package com.ksh.features.ai.questiongen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.tests.entity.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiQuestionResponseParserTest {

    private final AiQuestionResponseParser parser =
            new AiQuestionResponseParser(new ObjectMapper());

    @Test
    void parses_fenced_json_and_preserves_valid_mcq() {
        String reply = """
                Kết quả:
                ```json
                {"questions":[{
                  "type":"MCQ",
                  "content":"Thủ đô Hàn Quốc là gì?",
                  "explanation":"Seoul là thủ đô.",
                  "options":[
                    {"content":"Seoul","correct":true},
                    {"content":"Busan","correct":false}
                  ]
                }]}
                ```
                """;

        var questions = parser.parse(reply, 1, Question.TYPE_MCQ);

        assertThat(questions).singleElement().satisfies(question -> {
            assertThat(question.type()).isEqualTo(Question.TYPE_MCQ);
            assertThat(question.content()).isEqualTo("Thủ đô Hàn Quốc là gì?");
            assertThat(question.options()).hasSize(2);
        });
    }

    @Test
    void rejects_a_different_question_count() {
        assertThatThrownBy(() -> parser.parse(validMcq(), 2, Question.TYPE_MCQ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("số lượng");
    }

    @Test
    void rejects_a_different_question_type() {
        assertThatThrownBy(() -> parser.parse(validMcq(), 1, Question.TYPE_MR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loại câu hỏi");
    }

    @Test
    void rejects_unknown_type_instead_of_coercing_it() {
        String reply = validMcq().replace("\"MCQ\"", "\"TRUE_FALSE\"");
        assertThatThrownBy(() -> parser.parse(reply, 1, Question.TYPE_MCQ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loại câu hỏi");
    }

    @Test
    void rejects_mcq_without_exactly_one_correct_option() {
        String reply = validMcq().replace(
                "{\"content\":\"B\",\"correct\":false}",
                "{\"content\":\"B\",\"correct\":true}");
        assertThatThrownBy(() -> parser.parse(reply, 1, Question.TYPE_MCQ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đáp án");
    }

    @Test
    void rejects_mr_when_every_option_is_correct() {
        String reply = """
                {"questions":[{"type":"MR","content":"Chọn đáp án",
                "options":[{"content":"A","correct":true},{"content":"B","correct":true}]}]}
                """;
        assertThatThrownBy(() -> parser.parse(reply, 1, Question.TYPE_MR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đáp án");
    }

    @Test
    void rejects_duplicate_options_case_insensitively() {
        String reply = validMcq().replace("\"B\"", "\" a \"");
        assertThatThrownBy(() -> parser.parse(reply, 1, Question.TYPE_MCQ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đáp án");
    }

    @Test
    void rejects_oversized_question_content() {
        String reply = validMcq().replace("Câu hỏi",
                "x".repeat(AiQuestionResponseParser.MAX_QUESTION_CHARS + 1));
        assertThatThrownBy(() -> parser.parse(reply, 1, Question.TYPE_MCQ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quá dài");
    }

    @Test
    void rejects_html_in_generated_fields_before_preview_or_persistence() {
        String reply = validMcq().replace("Câu hỏi", "<img src=x onerror=alert(1)>");
        assertThatThrownBy(() -> parser.parse(reply, 1, Question.TYPE_MCQ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ");
    }

    private static String validMcq() {
        return """
                {"questions":[{"type":"MCQ","content":"Câu hỏi",
                "options":[{"content":"A","correct":true},{"content":"B","correct":false}]}]}
                """;
    }
}
