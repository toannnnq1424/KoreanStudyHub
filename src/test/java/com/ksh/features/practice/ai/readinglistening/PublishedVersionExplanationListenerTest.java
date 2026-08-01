package com.ksh.features.practice.ai.readinglistening;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PublishedVersionExplanationListenerTest {

    @Test
    void preparationIsBoundToAfterCommitRatherThanThePublishTransaction() throws Exception {
        TransactionalEventListener annotation = PublishedVersionExplanationListener.class
                .getDeclaredMethod("prepare", PublishedVersionExplanationEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void listenerPreparesPublishedVersionAndDoesNotUndoCommittedPublishOnFailure() {
        QuestionExplanationPreparationService preparation =
                mock(QuestionExplanationPreparationService.class);
        PublishedVersionExplanationListener listener =
                new PublishedVersionExplanationListener(preparation);
        when(preparation.preparePublishedVersion(77L)).thenReturn(
                new QuestionExplanationPreparationService.PreparationSummary(4, 3, 1, 0));

        listener.prepare(new PublishedVersionExplanationEvent(77L));

        verify(preparation).preparePublishedVersion(77L);

        when(preparation.preparePublishedVersion(78L))
                .thenThrow(new IllegalStateException("post-commit queue unavailable"));
        assertThatCode(() -> listener.prepare(new PublishedVersionExplanationEvent(78L)))
                .doesNotThrowAnyException();
    }

    @Test
    void listenerPromotesOnlyApprovedEditorialPayloadAfterPreparation() {
        QuestionExplanationPreparationService preparation =
                mock(QuestionExplanationPreparationService.class);
        ObjectiveExplanationEditorialService editorial =
                mock(ObjectiveExplanationEditorialService.class);
        PublishedVersionExplanationListener listener =
                new PublishedVersionExplanationListener(
                        preparation, editorial);
        when(preparation.preparePublishedVersion(79L)).thenReturn(
                new QuestionExplanationPreparationService.PreparationSummary(
                        1, 0, 1, 0));

        listener.prepare(new PublishedVersionExplanationEvent(
                79L, 7L, java.util.Map.of("question-1", 101L)));

        verify(preparation).preparePublishedVersion(79L);
        verify(editorial).promoteApproved(
                7L, java.util.Map.of("question-1", 101L));

        listener.prepare(new PublishedVersionExplanationEvent(80L));
        verify(editorial, never()).promoteApproved(
                org.mockito.ArgumentMatchers.eq(null),
                org.mockito.ArgumentMatchers.anyMap());
    }
}
