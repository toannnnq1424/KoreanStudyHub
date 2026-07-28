package com.ksh.features.mail.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
