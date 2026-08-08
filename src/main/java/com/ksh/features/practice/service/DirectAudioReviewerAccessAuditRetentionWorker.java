package com.ksh.features.practice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Default-off operational worker; deletion is bounded by each event's deadline. */
@Service
@ConditionalOnProperty(
        prefix = "app.practice.speaking-direct-audio.reviewer-access-audit",
        name = "retention-worker-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class DirectAudioReviewerAccessAuditRetentionWorker {
    private final DirectAudioReviewerAccessAuditRetention retention;
    private final int batchSize;

    public DirectAudioReviewerAccessAuditRetentionWorker(
            DirectAudioReviewerAccessAuditRetention retention,
            @Value("${app.practice.speaking-direct-audio.reviewer-access-audit."
                    + "retention-worker-batch-size:100}")
            int batchSize) {
        this.retention = retention;
        this.batchSize = Math.max(1, Math.min(batchSize, 1_000));
    }

    @Scheduled(fixedDelayString = "${app.practice.speaking-direct-audio."
            + "reviewer-access-audit.retention-worker-fixed-delay:PT1H}")
    public int runOnce() {
        return retention.purgeExpired(batchSize);
    }
}
