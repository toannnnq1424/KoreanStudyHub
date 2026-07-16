package com.ksh.features.practice.ai.readinglistening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.practice.explanation-generation", name = "worker-enabled",
        havingValue = "true", matchIfMissing = true)
public class QuestionExplanationGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(QuestionExplanationGenerationWorker.class);
    private final QuestionExplanationGenerationProcessor processor;

    public QuestionExplanationGenerationWorker(QuestionExplanationGenerationProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(
            initialDelayString = "${app.practice.explanation-generation.initial-delay:PT20S}",
            fixedDelayString = "${app.practice.explanation-generation.fixed-delay:PT30S}")
    public void processDue() {
        int processed = processor.processDue(20);
        if (processed > 0) {
            log.info("Processed {} Reading/Listening explanation tasks", processed);
        }
    }
}
