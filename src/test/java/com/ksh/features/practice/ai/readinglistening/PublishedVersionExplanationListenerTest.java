package com.ksh.features.practice.ai.readinglistening;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
}
