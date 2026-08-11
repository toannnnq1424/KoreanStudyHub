package com.ksh.features.tests.service;

import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.tests.dto.TestDtos.ReviewView;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.repository.TestActivityRepository;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.tests.support.TestAccessResolver;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamMonitorServiceTest {

    private static final Long TEST_ID = 21L;
    private static final Long ATTEMPT_ID = 101L;
    private static final Long LECTURER_ID = 41L;
    private static final Long STUDENT_ID = 42L;

    @Mock private TestAttemptRepository attemptRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private TestAccessResolver accessResolver;
    @Mock private AttemptResultBuilder resultBuilder;
    @Mock private TestActivityRepository activityRepository;

    @InjectMocks private ExamMonitorService service;

    @org.junit.jupiter.api.Test
    void owning_lecturer_can_still_review_an_in_progress_attempt() {
        Test exam = new Test(LECTURER_ID, Test.TYPE_MOCK);
        ReflectionTestUtils.setField(exam, "id", TEST_ID);
        TestAttempt openAttempt = new TestAttempt(TEST_ID, STUDENT_ID);
        ReflectionTestUtils.setField(openAttempt, "id", ATTEMPT_ID);
        ReviewView expected = new ReviewView(
                TEST_ID, null, ATTEMPT_ID, "Bài test",
                0, 1, BigDecimal.ZERO, true, "?", List.of());

        when(accessResolver.requireManageable(TEST_ID, LECTURER_ID)).thenReturn(exam);
        when(accessResolver.requireAttemptForManageable(TEST_ID, ATTEMPT_ID, LECTURER_ID))
                .thenReturn(openAttempt);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());
        when(resultBuilder.buildReview(exam, openAttempt, true, "?")).thenReturn(expected);

        assertThat(service.lecturerReview(TEST_ID, ATTEMPT_ID, LECTURER_ID))
                .isSameAs(expected);

        verify(resultBuilder).buildReview(exam, openAttempt, true, "?");
    }
}
