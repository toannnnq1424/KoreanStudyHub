package com.ksh.features.tests.service;

import com.ksh.features.tests.dto.LecturerTestDtos.OptionForm;
import com.ksh.features.tests.dto.LecturerTestDtos.QuestionForm;
import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.repository.QuestionOptionRepository;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.tests.repository.TestResponseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExamQuestionBankWriterTest {

    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final QuestionOptionRepository options = mock(QuestionOptionRepository.class);
    private final TestResponseRepository responses = mock(TestResponseRepository.class);
    private final TestAttemptRepository attempts = mock(TestAttemptRepository.class);
    private final ExamQuestionBankWriter writer =
            new ExamQuestionBankWriter(questions, options, responses, attempts);

    @Test
    void sanitizesExplanationBeforePersistingQuestionSnapshot() {
        when(questions.findByTestIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of());
        when(questions.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
        QuestionForm form = new QuestionForm(
                null,
                Question.TYPE_MCQ,
                "<p>Câu hỏi</p>",
                "<p>Giải thích an toàn</p><img src=x onerror=alert(1)><script>alert(2)</script>",
                BigDecimal.ONE,
                List.of(
                        new OptionForm(null, "A", true),
                        new OptionForm(null, "B", false)));

        writer.appendQuestions(7L, List.of(form));

        var explanation = org.mockito.ArgumentCaptor.forClass(Question.class);
        org.mockito.Mockito.verify(questions).save(explanation.capture());
        assertThat(explanation.getValue().getExplanation())
                .contains("Giải thích an toàn")
                .doesNotContain("onerror", "<script", "alert(2)");
    }
}
