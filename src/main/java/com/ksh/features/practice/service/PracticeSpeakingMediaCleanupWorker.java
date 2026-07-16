package com.ksh.features.practice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(
        prefix = "app.practice.speaking-media",
        name = "cleanup-worker-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class PracticeSpeakingMediaCleanupWorker {

    private static final Logger log = LoggerFactory.getLogger(PracticeSpeakingMediaCleanupWorker.class);

    private final PracticeSpeakingMediaCleanupProcessor processor;
    private final Clock clock;
    private final int batchSize;

    @Autowired
    public PracticeSpeakingMediaCleanupWorker(
            PracticeSpeakingMediaCleanupProcessor processor,
            @Value("${app.practice.speaking-media.cleanup-worker-batch-size:100}") int batchSize) {
        this(processor, Clock.systemUTC(), batchSize);
    }

    PracticeSpeakingMediaCleanupWorker(
            PracticeSpeakingMediaCleanupProcessor processor, Clock clock, int batchSize) {
        this.processor = processor;
        this.clock = clock;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
    }

    @Scheduled(fixedDelayString = "${app.practice.speaking-media.cleanup-worker-fixed-delay:PT5M}")
    public void processDueTasks() {
        var result = processor.processDueTasks(
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                batchSize);
        if (result.processed() > 0) {
            log.info(
                    "Speaking media cleanup batch processed={} completed={} retried={} terminal={} skipped={} failed={}",
                    result.processed(),
                    result.completed(),
                    result.retried(),
                    result.terminal(),
                    result.skipped(),
                    result.failed());
        }
    }
}
