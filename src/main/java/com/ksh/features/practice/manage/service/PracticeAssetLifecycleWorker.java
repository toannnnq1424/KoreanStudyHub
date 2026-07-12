package com.ksh.features.practice.manage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.practice.asset-lifecycle", name = "worker-enabled",
        havingValue = "true", matchIfMissing = true)
public class PracticeAssetLifecycleWorker {

    private static final Logger log = LoggerFactory.getLogger(PracticeAssetLifecycleWorker.class);

    private final PracticeAssetLifecycleProcessor processor;

    public PracticeAssetLifecycleWorker(PracticeAssetLifecycleProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(initialDelayString = "${app.practice.asset-lifecycle.initial-delay:PT1M}",
            fixedDelayString = "${app.practice.asset-lifecycle.fixed-delay:PT5M}")
    public void processDue() {
        int processed = processor.processDue(50);
        if (processed > 0) {
            log.info("Processed {} practice asset lifecycle tasks", processed);
        }
    }
}
