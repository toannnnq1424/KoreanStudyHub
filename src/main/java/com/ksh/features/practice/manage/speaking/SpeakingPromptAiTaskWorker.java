package com.ksh.features.practice.manage.speaking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.practice.speaking-prompt-authoring",
        name = "worker-enabled",
        havingValue = "true")
public class SpeakingPromptAiTaskWorker {

    private static final Logger log =
            LoggerFactory.getLogger(SpeakingPromptAiTaskWorker.class);

    private final SpeakingPromptAiTaskProcessor processor;
    private final SpeakingPromptAuthoringAiProperties properties;

    public SpeakingPromptAiTaskWorker(
            SpeakingPromptAiTaskProcessor processor,
            SpeakingPromptAuthoringAiProperties properties) {
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString =
                    "${app.practice.speaking-prompt-authoring.worker-initial-delay:PT30S}",
            fixedDelayString =
                    "${app.practice.speaking-prompt-authoring.worker-fixed-delay:PT30S}")
    public void processDue() {
        int processed = processor.processDue(
                properties.taskBounds().workerBatchSize());
        if (processed > 0) {
            log.info("Processed {} Speaking prompt authoring tasks", processed);
        }
    }
}
