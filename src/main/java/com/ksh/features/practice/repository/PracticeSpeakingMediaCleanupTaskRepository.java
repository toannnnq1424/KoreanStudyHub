package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSpeakingMediaCleanupTask;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PracticeSpeakingMediaCleanupTaskRepository
        extends JpaRepository<PracticeSpeakingMediaCleanupTask, Long> {

    Optional<PracticeSpeakingMediaCleanupTask> findByStorageProviderAndStorageKey(
            PracticeSpeakingStorageProvider storageProvider, String storageKey);

    @Query("""
            select t.id
            from PracticeSpeakingMediaCleanupTask t
            where t.status in (com.ksh.entities.PracticeSpeakingMediaCleanupStatus.PENDING,
                               com.ksh.entities.PracticeSpeakingMediaCleanupStatus.RETRY)
              and t.nextAttemptAt <= :now
            order by t.nextAttemptAt asc, t.id asc
            """)
    List<Long> findDueTaskIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO practice_speaking_media_cleanup_tasks
                (cleanup_reason, storage_provider, storage_key, due_at, next_attempt_at, status, attempt_count)
            VALUES
                (:reason, :provider, :storageKey, :dueAt, :nextAttemptAt, 'PENDING', 0)
            ON DUPLICATE KEY UPDATE
                due_at = CASE
                    WHEN status IN ('PENDING','RETRY') AND due_at > VALUES(due_at)
                    THEN VALUES(due_at)
                    ELSE due_at
                END,
                next_attempt_at = CASE
                    WHEN status IN ('PENDING','RETRY')
                         AND cleanup_reason = 'SUPERSEDED_RETENTION'
                         AND VALUES(cleanup_reason) IN ('LOGICAL_DELETE','DISCARD_ATTEMPT','ACTIVATION_COMPENSATION')
                         AND next_attempt_at > VALUES(next_attempt_at)
                    THEN VALUES(next_attempt_at)
                    ELSE next_attempt_at
                END,
                cleanup_reason = CASE
                    WHEN status IN ('PENDING','RETRY')
                         AND cleanup_reason = 'SUPERSEDED_RETENTION'
                         AND VALUES(cleanup_reason) IN ('LOGICAL_DELETE','DISCARD_ATTEMPT','ACTIVATION_COMPENSATION')
                    THEN VALUES(cleanup_reason)
                    WHEN status IN ('PENDING','RETRY')
                         AND cleanup_reason IN ('LOGICAL_DELETE','DISCARD_ATTEMPT')
                         AND VALUES(cleanup_reason) = 'ACTIVATION_COMPENSATION'
                    THEN VALUES(cleanup_reason)
                    ELSE cleanup_reason
                END
            """, nativeQuery = true)
    int insertOrKeepExisting(
            @Param("reason") String reason,
            @Param("provider") String provider,
            @Param("storageKey") String storageKey,
            @Param("dueAt") LocalDateTime dueAt,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt);
}
