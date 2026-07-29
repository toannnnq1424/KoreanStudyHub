package com.ksh.features.practice.service;

import java.time.LocalDateTime;

/**
 * Typed server-authority rejection for learner mutations after the immutable
 * attempt deadline.
 */
public final class PracticeAttemptDeadlineExpiredException
        extends IllegalStateException {

    private final LocalDateTime deadlineAt;

    public PracticeAttemptDeadlineExpiredException(
            LocalDateTime deadlineAt) {
        super("Thời hạn máy chủ của lượt làm bài đã kết thúc.");
        this.deadlineAt = deadlineAt;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }
}
