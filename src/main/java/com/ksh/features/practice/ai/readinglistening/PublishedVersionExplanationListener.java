package com.ksh.features.practice.ai.readinglistening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PublishedVersionExplanationListener {

    private static final Logger log = LoggerFactory.getLogger(PublishedVersionExplanationListener.class);

    private final QuestionExplanationPreparationService preparationService;

    public PublishedVersionExplanationListener(
            QuestionExplanationPreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void prepare(PublishedVersionExplanationEvent event) {
        try {
            QuestionExplanationPreparationService.PreparationSummary summary =
                    preparationService.preparePublishedVersion(event.publishedVersionId());
            log.info("[Publisher] Prepared explanations publishedVersionId={} eligible={} reused={} queued={} failed={}",
                    event.publishedVersionId(), summary.eligible(), summary.reused(),
                    summary.queued(), summary.failed());
        } catch (Exception exception) {
            log.error("[Publisher] Explanation preparation failed publishedVersionId={} exception={}",
                    event.publishedVersionId(), exception.getClass().getSimpleName());
        }
    }
}
