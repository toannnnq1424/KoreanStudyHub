package com.ksh.features.tests.service;

import com.ksh.features.tests.dto.TestDtos.TakeView;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.repository.QuestionOptionRepository;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.tests.repository.TestRepository;
import com.ksh.features.tests.repository.TestResponseRepository;
import com.ksh.features.tests.support.TestAccessResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAttemptServiceTest {

    private static final Long TEST_ID = 21L;
    private static final Long USER_ID = 42L;

    @Mock private TestRepository testRepository;
    @Mock private TestAttemptRepository attemptRepository;
    @Mock private TestResponseRepository responseRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private QuestionOptionRepository optionRepository;
    @Mock private TestAccessResolver accessResolver;
    @Mock private GradingService gradingService;
    @Mock private TakeViewBuilder takeViewBuilder;
    @Mock private AttemptResultBuilder resultBuilder;

    @InjectMocks private TestAttemptService service;

    private Test classExam;
    private TestAttempt legacyOpen;
    private TestAttempt completed;

    @BeforeEach
    void setUp() {
        classExam = new Test(7L, Test.TYPE_MOCK);
        classExam.setStatus(Test.STATUS_PUBLISHED);
        classExam.setTimeMode(Test.TIME_MODE_INDIVIDUAL);
        classExam.setDurationMinutes(30);

        legacyOpen = new TestAttempt(TEST_ID, USER_ID);
        completed = new TestAttempt(TEST_ID, USER_ID);
        completed.finalizeGrade(BigDecimal.ONE, BigDecimal.TEN,
                1, 10, 120, TestAttempt.STATUS_SUBMITTED);
    }

    @org.junit.jupiter.api.Test
    void start_rejects_a_legacy_open_attempt_when_a_completed_attempt_exists() {
        when(accessResolver.requireAttemptableForUpdate(TEST_ID, USER_ID))
                .thenReturn(classExam);
        when(attemptRepository.findByTestIdAndUserIdOrderByStartedAtDesc(TEST_ID, USER_ID))
                .thenReturn(List.of(legacyOpen, completed));

        assertThatThrownBy(() -> service.startOrResume(TEST_ID, USER_ID))
                .isInstanceOf(TestAttemptUnavailableException.class)
                .hasMessageContaining("chỉ được làm một lần");

        verify(attemptRepository, never()).save(any(TestAttempt.class));
        verify(takeViewBuilder, never()).build(any(Test.class), any(TestAttempt.class));
    }

    @org.junit.jupiter.api.Test
    void direct_take_also_rejects_a_legacy_duplicate_after_completion() {
        when(accessResolver.requireAttemptableForUpdate(TEST_ID, USER_ID))
                .thenReturn(classExam);
        when(attemptRepository.findByTestIdAndUserIdOrderByStartedAtDesc(TEST_ID, USER_ID))
                .thenReturn(List.of(legacyOpen, completed));

        assertThatThrownBy(() -> service.resumeForTake(TEST_ID, USER_ID))
                .isInstanceOf(TestAttemptUnavailableException.class)
                .hasMessageContaining("chỉ được làm một lần");

        verify(takeViewBuilder, never()).build(any(Test.class), any(TestAttempt.class));
    }

    @org.junit.jupiter.api.Test
    void practice_keeps_its_repeatable_attempt_behaviour() {
        Test practice = new Test(USER_ID, Test.TYPE_PRACTICE);
        TakeView expected = new TakeView(99L, TEST_ID, "Luyện tập", null,
                Test.TIME_MODE_INDIVIDUAL, -1, -1,
                null, null, null, List.of());
        when(accessResolver.requireAttemptableForUpdate(TEST_ID, USER_ID))
                .thenReturn(practice);
        when(attemptRepository.findByTestIdAndUserIdOrderByStartedAtDesc(TEST_ID, USER_ID))
                .thenReturn(List.of(completed));
        when(attemptRepository.save(any(TestAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(takeViewBuilder.build(eq(practice), any(TestAttempt.class))).thenReturn(expected);

        service.resumeForTake(TEST_ID, USER_ID);

        verify(attemptRepository).save(any(TestAttempt.class));
        verify(takeViewBuilder).build(eq(practice), any(TestAttempt.class));
    }
}
