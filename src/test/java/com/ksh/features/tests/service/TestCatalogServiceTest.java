package com.ksh.features.tests.service;

import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.tests.dto.TestDtos.StudentTestDetail;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.tests.repository.TestRepository;
import com.ksh.features.tests.support.TestAccessQueries;
import com.ksh.features.tests.support.TestAccessResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCatalogServiceTest {

    private static final Long TEST_ID = 21L;
    private static final Long USER_ID = 42L;

    @Mock private TestAccessQueries accessQueries;
    @Mock private TestAttemptRepository attemptRepository;
    @Mock private ClassRepository classRepository;
    @Mock private TestRepository testRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private TestAccessResolver accessResolver;

    @InjectMocks private TestCatalogService service;

    private Test classExam;

    @BeforeEach
    void setUp() {
        classExam = new Test(7L, Test.TYPE_MOCK);
        ReflectionTestUtils.setField(classExam, "id", TEST_ID);
        classExam.setTitle("Bài kiểm tra một lượt");
        classExam.setStatus(Test.STATUS_PUBLISHED);
        classExam.setTimeMode(Test.TIME_MODE_INDIVIDUAL);
        classExam.setDurationMinutes(30);
        classExam.setTotalQuestions(10);
    }

    @org.junit.jupiter.api.Test
    void completed_attempt_wins_over_a_legacy_duplicate_open_attempt() {
        TestAttempt open = new TestAttempt(TEST_ID, USER_ID);
        ReflectionTestUtils.setField(open, "id", 101L);
        TestAttempt completed = new TestAttempt(TEST_ID, USER_ID);
        ReflectionTestUtils.setField(completed, "id", 100L);
        completed.finalizeGrade(BigDecimal.valueOf(8), BigDecimal.TEN,
                8, 10, 120, TestAttempt.STATUS_SUBMITTED);

        when(accessResolver.requireViewable(TEST_ID, USER_ID)).thenReturn(classExam);
        when(attemptRepository.findByTestIdAndUserIdOrderByStartedAtDesc(TEST_ID, USER_ID))
                .thenReturn(List.of(open, completed));

        StudentTestDetail detail = service.detailForStudent(TEST_ID, USER_ID);

        assertThat(detail.availability()).isEqualTo("COMPLETED");
        assertThat(detail.completed()).isTrue();
        assertThat(detail.canResume()).isFalse();
        assertThat(detail.canStart()).isFalse();
        assertThat(detail.attemptId()).isEqualTo(100L);
        assertThat(detail.scorePercent()).isEqualTo(80);
    }
}
