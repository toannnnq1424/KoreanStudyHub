package com.ksh.features.practice.controller;

import com.ksh.features.practice.service.PracticeAttemptConflictException;
import com.ksh.features.practice.service.PracticeAttemptDeadlineExpiredException;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAttemptControllerAdviceTest {

    private final PracticeAttemptControllerAdvice advice =
            new PracticeAttemptControllerAdvice();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/practice/attempts/7/submit");

    @Test
    void isScopedExactlyToThePracticeController() {
        ControllerAdvice annotation = PracticeAttemptControllerAdvice.class
                .getAnnotation(ControllerAdvice.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.assignableTypes())
                .containsExactly(PracticeController.class);
        assertThat(annotation.basePackages()).isEmpty();
        assertThat(annotation.basePackageClasses()).isEmpty();
        assertThat(annotation.annotations()).isEmpty();
        assertThat(PracticeAttemptControllerAdvice.class.getAnnotation(Order.class).value())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void preservesConflictStatusAndBodyIncludingEmptyFallback() {
        var response = advice.handleConflict(
                new PracticeAttemptConflictException("attempt changed"), request);
        var emptyResponse = advice.handleConflict(
                new PracticeAttemptConflictException(null), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo("attempt changed");
        assertThat(emptyResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(emptyResponse.getBody()).isEmpty();
    }

    @Test
    void preservesDeadlineStatusAndBody() {
        PracticeAttemptDeadlineExpiredException exception =
                new PracticeAttemptDeadlineExpiredException(
                        LocalDateTime.of(2026, 7, 29, 10, 30));

        var response = advice.handleDeadline(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isEqualTo(exception.getMessage());
    }
}
