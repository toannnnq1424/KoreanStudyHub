package com.ksh.features.tests.support;

import com.ksh.features.tests.dto.LecturerTestDtos.ExamForm;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ExamFormValidatorTest {

    @Test
    void empty_draft_can_be_saved_before_ai_generation() {
        assertThatNoException().isThrownBy(() ->
                ExamFormValidator.validate(form("DRAFT", List.of())));
    }

    @Test
    void published_exam_still_requires_a_question() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                ExamFormValidator.validate(form("PUBLISHED", List.of())))
                .withMessageContaining("ít nhất một câu hỏi");
    }

    @Test
    void published_fixed_window_requires_both_schedule_bounds() {
        ExamForm form = new ExamForm(null, "Bài test AI", null, 1L,
                "MOCK", "PUBLISHED", "FIXED_WINDOW", null,
                LocalDateTime.now(), null, BigDecimal.ONE, false, false,
                null, null, List.of(question()), false);

        assertThatIllegalArgumentException().isThrownBy(() ->
                ExamFormValidator.validate(form))
                .withMessageContaining("thời gian bắt đầu và kết thúc");
    }

    @Test
    void individual_duration_must_be_positive_and_bounded() {
        ExamForm form = new ExamForm(null, "Bài test AI", null, 1L,
                "MOCK", "PUBLISHED", "INDIVIDUAL", 0,
                null, null, BigDecimal.ONE, false, false,
                null, null, List.of(question()), false);

        assertThatIllegalArgumentException().isThrownBy(() ->
                ExamFormValidator.validate(form))
                .withMessageContaining("1 đến 600 phút");
    }

    @Test
    void end_time_must_follow_start_time_even_for_draft() {
        LocalDateTime start = LocalDateTime.now();
        ExamForm form = new ExamForm(null, "Bài test AI", null, 1L,
                "MOCK", "DRAFT", "FIXED_WINDOW", null,
                start, start.minusMinutes(1), BigDecimal.ONE, false, false,
                null, null, List.of(), false);

        assertThatIllegalArgumentException().isThrownBy(() ->
                ExamFormValidator.validate(form))
                .withMessageContaining("sau thời gian bắt đầu");
    }

    private static ExamForm form(String status,
                                 List<com.ksh.features.tests.dto.LecturerTestDtos.QuestionForm> questions) {
        return new ExamForm(null, "Bài test AI", null, 1L,
                "MOCK", status, "INDIVIDUAL", 30,
                null, null, BigDecimal.ONE, false, false,
                null, null, questions, false);
    }

    private static com.ksh.features.tests.dto.LecturerTestDtos.QuestionForm question() {
        return new com.ksh.features.tests.dto.LecturerTestDtos.QuestionForm(
                null, "MCQ", "한국의 수도는 어디입니까?", null, BigDecimal.ONE,
                List.of(
                        new com.ksh.features.tests.dto.LecturerTestDtos.OptionForm(
                                null, "서울", true),
                        new com.ksh.features.tests.dto.LecturerTestDtos.OptionForm(
                                null, "부산", false)));
    }
}
