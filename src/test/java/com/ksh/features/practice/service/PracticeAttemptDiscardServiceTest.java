package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAttemptDiscardServiceTest {

    @Test
    void entityDiscardIsIdempotentClearsContentAndCannotTransitionOut() {
        PracticeAttempt attempt = new PracticeAttempt(7L, 11L, 12L, "WRITING", 13L);
        attempt.setAnswersJson("{\"answer\":\"secret\"}");
        attempt.setAiFeedbackJson("{\"feedback\":\"secret\"}");
        attempt.setScore(BigDecimal.TEN);
        attempt.setTotalPoints(BigDecimal.TEN);
        LocalDateTime firstDiscardedAt = LocalDateTime.of(2026, 7, 5, 12, 0);

        attempt.discard(firstDiscardedAt);
        attempt.discard(firstDiscardedAt.plusHours(1));

        assertThat(attempt.getStatus()).isEqualTo(PracticeAttempt.STATUS_DISCARDED);
        assertThat(attempt.getDiscardedAt()).isEqualTo(firstDiscardedAt);
        assertThat(attempt.getAnswersJson()).isNull();
        assertThat(attempt.getAiFeedbackJson()).isNull();
        assertThat(attempt.getScore()).isNull();
        assertThat(attempt.getTotalPoints()).isNull();
        assertThatThrownBy(() -> attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> attempt.markSubmitted(BigDecimal.ONE, BigDecimal.TEN, "{}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void processorInfrastructureFailureDoesNotChangeLogicalSuccessOrStopRemainingTasks() {
        PracticeAttemptDiscardTransactionService transactionService =
                mock(PracticeAttemptDiscardTransactionService.class);
        PracticeSpeakingMediaCleanupProcessor processor =
                mock(PracticeSpeakingMediaCleanupProcessor.class);
        LocalDateTime discardedAt = LocalDateTime.of(2026, 7, 5, 12, 0);
        PracticeAttemptDiscardService service =
                new PracticeAttemptDiscardService(transactionService, processor);
        List<Long> taskIds = LongStream.rangeClosed(1, 100).boxed().toList();
        PracticeAttemptDiscardResult expected = new PracticeAttemptDiscardResult(
                42L,
                "DISCARDED",
                discardedAt,
                101,
                taskIds);
        when(transactionService.discardForOwner(42L, 7L)).thenReturn(expected);
        doThrow(new IllegalStateException("LEARNER_AUDIO_PATH_SECRET_B3A2"))
                .when(processor).processTaskNow(1L);

        PracticeAttemptDiscardResult actual = service.discardForOwner(42L, 7L);

        assertThat(actual).isSameAs(expected);
        verify(processor, times(100)).processTaskNow(org.mockito.ArgumentMatchers.anyLong());
    }
}
