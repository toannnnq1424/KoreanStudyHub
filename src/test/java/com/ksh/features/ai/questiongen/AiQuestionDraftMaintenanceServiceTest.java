package com.ksh.features.ai.questiongen;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiQuestionDraftMaintenanceServiceTest {

    private final AiQuestionDraftCleanupBatch cleanupBatch =
            mock(AiQuestionDraftCleanupBatch.class);
    private final AiQuestionDraftSessionRepository repository =
            mock(AiQuestionDraftSessionRepository.class);
    private final AiQuestionDraftRetentionMetrics metrics =
            mock(AiQuestionDraftRetentionMetrics.class);
    private final AiQuestionDraftMaintenanceService service =
            new AiQuestionDraftMaintenanceService(cleanupBatch, repository, metrics);

    @Test
    void sweep_stops_after_a_partial_batch_and_records_non_pii_snapshot() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 29, 4, 0);
        when(cleanupBatch.deleteExpired(cutoff, 3)).thenReturn(3, 2);
        when(repository.countByExpiresAtLessThanEqual(cutoff)).thenReturn(4L);
        when(repository.findOldestExpiredAt(cutoff))
                .thenReturn(Optional.of(cutoff.minusMinutes(15)));

        var result = service.cleanupExpired(cutoff, 3, 5);

        assertThat(result).isEqualTo(
                new AiQuestionDraftMaintenanceService.CleanupResult(
                        2, 5, 4L, 900L, false));
        verify(cleanupBatch, times(2)).deleteExpired(cutoff, 3);
        verify(metrics).recordSuccess(5, 4L, 900L);
        verify(metrics, never()).recordCommittedDeletes(5);
    }

    @Test
    void sweep_honors_the_maximum_batch_cap_even_when_backlog_remains() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 29, 4, 0);
        when(cleanupBatch.deleteExpired(cutoff, 10)).thenReturn(10);
        when(repository.countByExpiresAtLessThanEqual(cutoff)).thenReturn(25L);
        when(repository.findOldestExpiredAt(cutoff))
                .thenReturn(Optional.of(cutoff.minusHours(2)));

        var result = service.cleanupExpired(cutoff, 10, 2);

        assertThat(result.batches()).isEqualTo(2);
        assertThat(result.deleted()).isEqualTo(20);
        assertThat(result.capped()).isTrue();
        verify(cleanupBatch, times(2)).deleteExpired(cutoff, 10);
    }

    @Test
    void observability_query_failure_does_not_undo_completed_cleanup() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 29, 4, 0);
        when(cleanupBatch.deleteExpired(cutoff, 50)).thenReturn(1);
        when(repository.countByExpiresAtLessThanEqual(cutoff))
                .thenThrow(new IllegalStateException("metrics backend unavailable"));

        var result = service.cleanupExpired(cutoff, 50, 2);

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.expiredRemaining()).isEqualTo(-1L);
        assertThat(result.oldestExpiredAgeSeconds()).isEqualTo(-1L);
        verify(metrics).recordSuccess(1, -1L, -1L);
    }

    @Test
    void later_batch_failure_records_prior_committed_deletes_once_then_rethrows() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 29, 4, 0);
        when(cleanupBatch.deleteExpired(cutoff, 5))
                .thenReturn(5)
                .thenThrow(new IllegalStateException("second transaction failed"));

        assertThatThrownBy(() -> service.cleanupExpired(cutoff, 5, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("second transaction failed");

        verify(metrics).recordCommittedDeletes(5);
        verify(metrics, never()).recordSuccess(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void invalid_bounds_fail_before_any_delete() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 29, 4, 0);

        assertThatThrownBy(() -> service.cleanupExpired(cutoff, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
        assertThatThrownBy(() -> service.cleanupExpired(cutoff, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBatches");
    }

    @Test
    void repository_contract_deletes_the_expiration_boundary_with_a_bound_limit()
            throws Exception {
        Method method = AiQuestionDraftSessionRepository.class.getDeclaredMethod(
                "deleteExpiredBatch", LocalDateTime.class, int.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("expires_at <= :cutoff")
                .contains("LIMIT :batchSize");
    }

    @Test
    void every_cleanup_batch_requires_its_own_transaction() throws Exception {
        Method method = AiQuestionDraftCleanupBatch.class.getDeclaredMethod(
                "deleteExpired", LocalDateTime.class, int.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.timeout()).isEqualTo(10);
    }
}
