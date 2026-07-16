package com.ksh.features.practice.ai.readinglistening;

import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.practice.explanation-generation", name = "worker-enabled",
        havingValue = "true", matchIfMissing = true)
public class QuestionExplanationPreparationReconciler {

    private static final Logger log = LoggerFactory.getLogger(
            QuestionExplanationPreparationReconciler.class);
    private static final int BATCH_SIZE = 10;

    private final QuestionVersionExplanationBindingRepository bindingRepository;
    private final QuestionExplanationPreparationService preparationService;

    public QuestionExplanationPreparationReconciler(
            QuestionVersionExplanationBindingRepository bindingRepository,
            QuestionExplanationPreparationService preparationService) {
        this.bindingRepository = bindingRepository;
        this.preparationService = preparationService;
    }

    @Scheduled(
            initialDelayString = "${app.practice.explanation-generation.reconcile-initial-delay:PT45S}",
            fixedDelayString = "${app.practice.explanation-generation.reconcile-fixed-delay:PT2M}")
    public void reconcilePreparationGaps() {
        int prepared = 0;
        for (Long publishedVersionId : bindingRepository.findPublishedVersionIdsWithPreparationGaps(
                ReadingListeningExplanationClient.EXPLANATION_LANGUAGE,
                PageRequest.of(0, BATCH_SIZE))) {
            try {
                preparationService.preparePublishedVersion(publishedVersionId);
                prepared++;
            } catch (Exception exception) {
                log.error(
                        "[ReadingListeningAI] Could not reconcile explanation preparation "
                                + "publishedVersionId={} exception={}",
                        publishedVersionId, exception.getClass().getSimpleName());
            }
        }
        if (prepared > 0) {
            log.info("Reconciled explanation preparation for {} published versions", prepared);
        }
    }
}
