package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeStorageMigrationJob;
import com.ksh.entities.PracticeStorageMigrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PracticeStorageMigrationJobRepository
        extends JpaRepository<PracticeStorageMigrationJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from PracticeStorageMigrationJob j where j.id = :id")
    Optional<PracticeStorageMigrationJob> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select j.id from PracticeStorageMigrationJob j
            where j.status in :statuses
              and j.nextAttemptAt <= :now
            order by j.nextAttemptAt asc, j.id asc
            """)
    List<Long> findDueIds(@Param("statuses") List<PracticeStorageMigrationStatus> statuses,
                          @Param("now") LocalDateTime now,
                          Pageable pageable);
}
