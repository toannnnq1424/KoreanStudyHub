package com.ksh.features.practice.ai.readinglistening;

import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionExplanationPreparationReconcilerTest {

    @Test
    void missingPostCommitPreparationIsRetriedWithoutBlockingTheBatch() {
        QuestionVersionExplanationBindingRepository bindings =
                mock(QuestionVersionExplanationBindingRepository.class);
        QuestionExplanationPreparationService preparation =
                mock(QuestionExplanationPreparationService.class);
        QuestionExplanationPreparationReconciler reconciler =
                new QuestionExplanationPreparationReconciler(bindings, preparation);
        when(bindings.findPublishedVersionIdsWithPreparationGaps(
                "vi", PageRequest.of(0, 10))).thenReturn(List.of(71L, 72L));
        doThrow(new IllegalStateException("temporary failure"))
                .when(preparation).preparePublishedVersion(71L);

        assertThatCode(reconciler::reconcilePreparationGaps).doesNotThrowAnyException();

        verify(preparation).preparePublishedVersion(71L);
        verify(preparation).preparePublishedVersion(72L);
    }
}
