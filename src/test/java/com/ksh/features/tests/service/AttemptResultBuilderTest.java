package com.ksh.features.tests.service;

import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.entity.QuestionOption;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.repository.QuestionOptionRepository;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestResponseRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttemptResultBuilderTest {

    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final QuestionOptionRepository options = mock(QuestionOptionRepository.class);
    private final TestResponseRepository responses = mock(TestResponseRepository.class);
    private final AttemptResultBuilder builder =
            new AttemptResultBuilder(questions, options, responses);

    @org.junit.jupiter.api.Test
    void resultProjectsBoundedPercentagesAndReadableDuration() {
        Test test = test();
        TestAttempt attempt = attempt();
        attempt.finalizeGrade(new BigDecimal("7.50"), BigDecimal.TEN,
                3, 4, 125, TestAttempt.STATUS_SUBMITTED);

        var result = builder.buildResult(test, attempt);

        assertThat(result.scorePercent()).isEqualTo(75);
        assertThat(result.correctPercent()).isEqualTo(75);
        assertThat(result.timeSpentLabel()).isEqualTo("2 phút 5 giây");
    }

    @org.junit.jupiter.api.Test
    void reviewRepairsOneEncodedHtmlLayerThenSanitizesLegacyMarkup() {
        Test test = test();
        TestAttempt attempt = attempt();
        attempt.finalizeGrade(BigDecimal.ZERO, BigDecimal.ONE,
                0, 1, 2, TestAttempt.STATUS_SUBMITTED);
        Question question = new Question(21L, Question.TYPE_MCQ,
                "&lt;p&gt;Chọn lời chào đúng.&lt;/p&gt;",
                "&lt;p&gt;Đáp án đúng là &lt;strong&gt;안녕하세요&lt;/strong&gt;.&lt;/p&gt;"
                        + "&lt;img src=x onerror=alert(1)&gt;",
                BigDecimal.ONE, 0);
        ReflectionTestUtils.setField(question, "id", 31L);
        QuestionOption option = new QuestionOption(
                31L, "&lt;p&gt;안녕하세요&lt;/p&gt;", true, 0);
        ReflectionTestUtils.setField(option, "id", 41L);
        when(questions.findByTestIdOrderBySortOrderAscIdAsc(21L))
                .thenReturn(List.of(question));
        when(options.findByQuestionIdInOrderBySortOrderAscIdAsc(List.of(31L)))
                .thenReturn(List.of(option));
        when(responses.findByAttemptId(51L)).thenReturn(List.of());

        var review = builder.buildReview(test, attempt, false, null);
        var rendered = review.questions().get(0);

        assertThat(rendered.content()).isEqualTo("<p>Chọn lời chào đúng.</p>");
        assertThat(rendered.options().get(0).content()).isEqualTo("<p>안녕하세요</p>");
        assertThat(rendered.explanation())
                .contains("<p>Đáp án đúng là <strong>안녕하세요</strong>.</p>")
                .doesNotContain("onerror", "alert(1)");
        assertThat(rendered.answered()).isFalse();
        assertThat(review.unansweredCount()).isEqualTo(1);
    }

    private static Test test() {
        Test test = new Test(7L, Test.TYPE_MOCK);
        ReflectionTestUtils.setField(test, "id", 21L);
        test.setTitle("Bài kiểm tra khởi động");
        return test;
    }

    private static TestAttempt attempt() {
        TestAttempt attempt = new TestAttempt(21L, 11L);
        ReflectionTestUtils.setField(attempt, "id", 51L);
        return attempt;
    }
}
