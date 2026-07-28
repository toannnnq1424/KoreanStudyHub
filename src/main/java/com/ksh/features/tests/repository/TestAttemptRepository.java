package com.ksh.features.tests.repository;

import com.ksh.features.tests.entity.TestAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository for {@link TestAttempt} — one student's run at one test. */
public interface TestAttemptRepository extends JpaRepository<TestAttempt, Long> {

    /** The caller's open (IN_PROGRESS) attempt for a test, if any. */
    Optional<TestAttempt> findFirstByTestIdAndUserIdAndStatusOrderByStartedAtDesc(
            Long testId, Long userId, String status);

    /** All of the caller's attempts for a test, newest first. */
    List<TestAttempt> findByTestIdAndUserIdOrderByStartedAtDesc(Long testId, Long userId);

    /** The caller's attempts across several tests (readiness best-attempt scan). */
    List<TestAttempt> findByUserIdAndTestIdIn(Long userId, Collection<Long> testIds);

    /** Every attempt for a test (lecturer monitor / submissions). */
    List<TestAttempt> findByTestId(Long testId);

    /** Shape-changing author operations are forbidden after the first attempt starts. */
    boolean existsByTestId(Long testId);

    /** Per-user guard: an attempt owned by the caller. */
    Optional<TestAttempt> findByIdAndUserId(Long id, Long userId);

    /**
     * Locks an owned attempt while heartbeat or final submission changes its
     * lifecycle. This serializes concurrent submit/heartbeat requests so only
     * the first submit can observe {@code IN_PROGRESS}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TestAttempt a where a.id = :id and a.userId = :userId")
    Optional<TestAttempt> findByIdAndUserIdForUpdate(@Param("id") Long id,
                                                     @Param("userId") Long userId);
}
