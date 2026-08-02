package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeAssetLifecycleTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PracticeAssetLifecycleTaskRepository
        extends JpaRepository<PracticeAssetLifecycleTask, Long> {

    @Query("select t.id from PracticeAssetLifecycleTask t " +
            "where (t.status = 'PENDING' and (t.nextAttemptAt is null or t.nextAttemptAt <= :now)) " +
            "or (t.status = 'RUNNING' and t.nextAttemptAt <= :now) " +
            "order by t.nextAttemptAt asc, t.id asc")
    List<Long> findDueIds(
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PracticeAssetLifecycleTask t where t.id = :id")
    Optional<PracticeAssetLifecycleTask> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from PracticeAssetLifecycleTask t
            where t.storageProfileCode = :profileCode
              and t.sourceStorageKey = :storageKey
              and t.status in ('PENDING', 'RUNNING')
            order by t.id asc
            """)
    List<PracticeAssetLifecycleTask> findActiveByStorageProfileCodeAndSourceStorageKeyForUpdate(
            @Param("profileCode") String profileCode,
            @Param("storageKey") String storageKey);
}
