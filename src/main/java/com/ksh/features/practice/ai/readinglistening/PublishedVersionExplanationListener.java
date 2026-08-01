package com.ksh.features.practice.ai.readinglistening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PublishedVersionExplanationListener {

    private static final Logger log = LoggerFactory.getLogger(PublishedVersionExplanationListener.class);

    private final QuestionExplanationPreparationService preparationService;
    private final ObjectiveExplanationEditorialService editorialService;

    @Autowired
    public PublishedVersionExplanationListener(
            QuestionExplanationPreparationService preparationService,
            ObjectiveExplanationEditorialService editorialService) {
        this.preparationService = preparationService;
        this.editorialService = editorialService;
    }

    PublishedVersionExplanationListener(
            QuestionExplanationPreparationService preparationService) {
        this(preparationService, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void prepare(PublishedVersionExplanationEvent event) {
        try {
            QuestionExplanationPreparationService.PreparationSummary summary =
                    preparationService.preparePublishedVersion(event.publishedVersionId());
            log.info("[Publisher] Prepared explanations publishedVersionId={} eligible={} reused={} queued={} failed={}",
                    event.publishedVersionId(), summary.eligible(), summary.reused(),
                    summary.queued(), summary.failed());
            if (editorialService != null
                    && event.draftId() != null
                    && !event.questionVersionIdsByClient().isEmpty()) {
                int promoted = editorialService.promoteApproved(
                        event.draftId(),
                        event.questionVersionIdsByClient());
                log.info(
                        "[Publisher] Promoted approved typed explanations publishedVersionId={} count={}",
                        event.publishedVersionId(), promoted);
            }
        } catch (Exception exception) {
            log.error("[Publisher] Explanation preparation failed publishedVersionId={} exception={}",
                    event.publishedVersionId(), exception.getClass().getSimpleName());
        }
    }
}
