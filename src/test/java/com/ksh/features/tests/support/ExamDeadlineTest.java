package com.ksh.features.tests.support;

import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExamDeadlineTest {

    @org.junit.jupiter.api.Test
    void individual_deadline_never_passes_exam_closing_time() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 31, 10, 0);
        Test test = individualTest(60, startedAt.plusMinutes(20));
        TestAttempt attempt = attemptStartedAt(startedAt);

        assertThat(ExamDeadline.deadline(test, attempt))
                .isEqualTo(startedAt.plusMinutes(20));
        assertThat(ExamDeadline.remainingSeconds(
                test, attempt, startedAt.plusMinutes(5)))
                .isEqualTo(15 * 60L);
    }

    @org.junit.jupiter.api.Test
    void elapsed_time_is_capped_at_authoritative_deadline() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 31, 10, 0);
        Test test = individualTest(30, null);
        TestAttempt attempt = attemptStartedAt(startedAt);

        assertThat(ExamDeadline.elapsedSeconds(
                test, attempt, startedAt.plusHours(2)))
                .isEqualTo(30 * 60);
    }

    @org.junit.jupiter.api.Test
    void absolute_deadline_is_exposed_for_drift_free_browser_countdown() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 31, 10, 0);
        Test test = individualTest(30, null);
        TestAttempt attempt = attemptStartedAt(startedAt);

        assertThat(ExamDeadline.deadlineEpochMillis(test, attempt)).isPositive();
    }

    private static Test individualTest(int durationMinutes, LocalDateTime endAt) {
        Test test = new Test(1L, Test.TYPE_MOCK);
        test.setTimeMode(Test.TIME_MODE_INDIVIDUAL);
        test.setDurationMinutes(durationMinutes);
        test.setEndAt(endAt);
        return test;
    }

    private static TestAttempt attemptStartedAt(LocalDateTime startedAt) {
        TestAttempt attempt = new TestAttempt(1L, 2L);
        ReflectionTestUtils.setField(attempt, "startedAt", startedAt);
        return attempt;
    }
}
