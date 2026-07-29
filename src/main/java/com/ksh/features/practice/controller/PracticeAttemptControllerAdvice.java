package com.ksh.features.practice.controller;

import com.ksh.features.practice.service.PracticeAttemptConflictException;
import com.ksh.features.practice.service.PracticeAttemptDeadlineExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Preserves the learner-attempt HTTP error contract at its Practice owner.
 */
@ControllerAdvice(assignableTypes = PracticeController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PracticeAttemptControllerAdvice {

    private static final Logger log =
            LoggerFactory.getLogger(PracticeAttemptControllerAdvice.class);

    @ExceptionHandler(PracticeAttemptConflictException.class)
    public ResponseEntity<String> handleConflict(
            PracticeAttemptConflictException exception,
            HttpServletRequest request) {
        log.info("409 tai [{}]: {}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(exception.getMessage() != null ? exception.getMessage() : "");
    }

    @ExceptionHandler(PracticeAttemptDeadlineExpiredException.class)
    public ResponseEntity<String> handleDeadline(
            PracticeAttemptDeadlineExpiredException exception,
            HttpServletRequest request) {
        log.info("410 tai [{}]: {}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(exception.getMessage() != null ? exception.getMessage() : "");
    }
}
