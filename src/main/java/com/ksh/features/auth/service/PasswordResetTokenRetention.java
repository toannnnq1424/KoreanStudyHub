package com.ksh.features.auth.service;

import com.ksh.features.auth.repository.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** Deletes at most one bounded batch of old used or expired reset tokens per run. */
@Component
class PasswordResetTokenRetention {

    private static final int MAX_BATCH_SIZE = 1_000;
    private final PasswordResetTokenRepository repository;
    private final Clock clock;
    private final int batchSize;
    private final Duration retentionAge;

    PasswordResetTokenRetention(
            PasswordResetTokenRepository repository,
            @Value("${app.auth.password-reset.retention.batch-size:500}") int batchSize,
            @Value("${app.auth.password-reset.retention.age:P7D}") Duration retentionAge) {
        this(repository, Clock.systemUTC(), batchSize, retentionAge);
    }

    PasswordResetTokenRetention(PasswordResetTokenRepository repository, Clock clock,
                                int batchSize, Duration retentionAge) {
        this.repository = repository;
        this.clock = clock;
        this.batchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        this.retentionAge = retentionAge.isNegative() || retentionAge.isZero()
                ? Duration.ofDays(7) : retentionAge;
    }

    @Scheduled(
            initialDelayString = "${app.auth.password-reset.retention.initial-delay-ms:120000}",
            fixedDelayString = "${app.auth.password-reset.retention.fixed-delay-ms:3600000}")
    @Transactional(timeout = 10)
    public int cleanup() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(retentionAge);
        var ids = repository.findRetentionIds(cutoff, PageRequest.of(0, batchSize));
        if (ids.isEmpty()) {
            return 0;
        }
        repository.deleteAllByIdInBatch(ids);
        return ids.size();
    }
}
