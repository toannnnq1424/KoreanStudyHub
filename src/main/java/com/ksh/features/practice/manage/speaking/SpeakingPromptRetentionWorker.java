package com.ksh.features.practice.manage.speaking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.practice.speaking-prompt-authoring",
        name = "cleanup-worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SpeakingPromptRetentionWorker {

    private static final Logger log =
            LoggerFactory.getLogger(SpeakingPromptRetentionWorker.class);
    private final SpeakingPromptRetentionService retentionService;

    public SpeakingPromptRetentionWorker(
            SpeakingPromptRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(
            initialDelayString = "${app.practice.speaking-prompt-authoring.cleanup-initial-delay:PT2M}",
            fixedDelayString = "${app.practice.speaking-prompt-authoring.cleanup-fixed-delay:PT15M}")
    public void reconcileExpired() {
        int processed = retentionService.reconcileExpired(50);
        if (processed > 0) {
            log.info("Reconciled {} expired Speaking prompt artifacts", processed);
        }
    }
}
