package com.ksh.features.tests.service;

/**
 * Expected student-flow rejection: the exam has not opened, has closed, is
 * misconfigured, or the student already used the single allowed attempt.
 */
public class TestAttemptUnavailableException extends RuntimeException {

    public TestAttemptUnavailableException(String message) {
        super(message);
    }
}
