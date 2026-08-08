package com.ksh.features.practice.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;

/** Bounded purge for access events whose immutable per-row deadline has elapsed. */
@Service
public class DirectAudioReviewerAccessAuditRetention {
    private static final int MAX_BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public DirectAudioReviewerAccessAuditRetention(
            JdbcTemplate jdbc, ObjectProvider<Clock> clockProvider) {
        this(jdbc, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    DirectAudioReviewerAccessAuditRetention(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
    }

    public int purgeExpired(int requestedBatchSize) {
        if (requestedBatchSize <= 0) {
            return 0;
        }
        int batchSize = Math.min(requestedBatchSize, MAX_BATCH_SIZE);
        return jdbc.update("""
                DELETE FROM practice_speaking_audio_reviewer_access_events
                WHERE delete_after IS NOT NULL AND delete_after <= ?
                ORDER BY delete_after, id
                LIMIT ?
                """, Timestamp.from(clock.instant()), batchSize);
    }
}
