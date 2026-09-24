package com.ksh.features.tests.service;

import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.QuestionOptionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SharedQuestionDefinitionTest {
    private Test delivery() {
        Test test = new Test(7L, Test.TYPE_MOCK);
        ReflectionTestUtils.setField(test, "id", 22L);
        test.setClassId(43L);
        test.setSharedQuestionSourceId(11L);
        return test;
    }

    @org.junit.jupiter.api.Test void previewUsesSourceWithoutCopyingQuestions() {
        var questions = mock(QuestionRepository.class);
        var options = mock(QuestionOptionRepository.class);
        new TakeViewBuilder(questions, options).buildPreview(delivery());
        verify(questions).findByTestIdOrderBySortOrderAscIdAsc(11L);
        verifyNoMoreInteractions(questions);
    }

    @org.junit.jupiter.api.Test void historicalAttemptRetainsItsOriginalQuestionIds() {
        Test test = delivery();
        var attempt = new TestAttempt(22L, 9L);
        attempt.setQuestionDefinitionId(22L);
        assertThat(attempt.questionDefinitionId(test)).isEqualTo(22L);
        assertThat(test.questionDefinitionId()).isEqualTo(11L);
    }

    @org.junit.jupiter.api.Test void classIdentityIsIndependentFromQuestionIdentity() {
        Test test = delivery();
        var attempt = new TestAttempt(test.getId(), 9L);
        attempt.setQuestionDefinitionId(test.questionDefinitionId());
        assertThat(attempt.getTestId()).isEqualTo(22L);
        assertThat(attempt.questionDefinitionId(test)).isEqualTo(11L);
        assertThat(test.getClassId()).isEqualTo(43L);
    }

    @org.junit.jupiter.api.Test void oldReviewReadsPinnedBankNotNewSharedBank() {
        var questions = mock(QuestionRepository.class);
        var options = mock(QuestionOptionRepository.class);
        var responses = mock(com.ksh.features.tests.repository.TestResponseRepository.class);
        var attempt = new TestAttempt(22L, 9L);
        attempt.setQuestionDefinitionId(22L);
        new AttemptResultBuilder(questions, options, responses).buildReview(delivery(), attempt, false, null);
        verify(questions).findByTestIdOrderBySortOrderAscIdAsc(22L);
        verifyNoMoreInteractions(questions);
    }
}
