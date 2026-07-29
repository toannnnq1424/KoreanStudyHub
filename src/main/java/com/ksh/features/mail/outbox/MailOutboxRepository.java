package com.ksh.features.mail.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence and claim queries for the durable mail outbox.
 */
public interface MailOutboxRepository extends JpaRepository<MailOutboxJob, Long> {

    @Query("""
            select j.id from MailOutboxJob j
            where (
                (j.status in :readyStatuses and j.availableAt <= :now)
                or (j.status = :processingStatus
                    and j.leaseExpiresAt is not null
                    and j.leaseExpiresAt <= :now)
            )
            order by j.id asc
            """)
    List<Long> findClaimableIds(
            @Param("readyStatuses") Collection<MailOutboxStatus> readyStatuses,
            @Param("processingStatus") MailOutboxStatus processingStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from MailOutboxJob j where j.id = :id")
    Optional<MailOutboxJob> findByIdForUpdate(@Param("id") Long id);

    boolean existsByNotificationId(Long notificationId);

    long countByStatus(MailOutboxStatus status);

    @Query("""
            select count(j) from MailOutboxJob j
            where j.status in :readyStatuses
              and j.availableAt <= :now
            """)
    long countReadyClaimable(
            @Param("readyStatuses") Collection<MailOutboxStatus> readyStatuses,
            @Param("now") LocalDateTime now);

    @Query("""
            select count(j) from MailOutboxJob j
            where j.status = :processingStatus
              and j.leaseExpiresAt is not null
              and j.leaseExpiresAt <= :now
            """)
    long countExpiredProcessingLeases(
            @Param("processingStatus") MailOutboxStatus processingStatus,
            @Param("now") LocalDateTime now);

    @Query("""
            select min(j.availableAt) from MailOutboxJob j
            where j.status in :readyStatuses
              and j.availableAt <= :now
            """)
    Optional<LocalDateTime> findOldestReadyAvailableAt(
            @Param("readyStatuses") Collection<MailOutboxStatus> readyStatuses,
            @Param("now") LocalDateTime now);

    @Query("""
            select min(j.leaseExpiresAt) from MailOutboxJob j
            where j.status = :processingStatus
              and j.leaseExpiresAt is not null
              and j.leaseExpiresAt <= :now
            """)
    Optional<LocalDateTime> findOldestExpiredLeaseAt(
            @Param("processingStatus") MailOutboxStatus processingStatus,
            @Param("now") LocalDateTime now);

    /**
     * Deletes only SENT rows older than the configured retention boundary.
     *
     * <p>The literal state plus {@code available_at} ordering deliberately
     * matches the existing V59 {@code idx_mail_outbox_due} index. A strict
     * boundary retains a row whose timestamp is exactly equal to the cutoff.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM mail_outbox_jobs
            WHERE status = 'SENT'
              AND available_at < :cutoff
            ORDER BY available_at ASC, id ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    int deleteSentBefore(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize);

    /**
     * Deletes only FAILED rows older than the configured retention boundary.
     *
     * <p>PENDING, RETRY and PROCESSING are intentionally impossible to pass
     * into this query because the terminal state is a SQL literal.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM mail_outbox_jobs
            WHERE status = 'FAILED'
              AND available_at < :cutoff
            ORDER BY available_at ASC, id ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    int deleteFailedBefore(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize);
}
