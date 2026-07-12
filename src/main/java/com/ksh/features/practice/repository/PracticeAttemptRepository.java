package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, Long> {

    Optional<PracticeAttempt> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PracticeAttempt a where a.id = :id and a.userId = :userId")
    Optional<PracticeAttempt> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    List<PracticeAttempt> findByTestIdAndUserIdAndSkillOrderByCreatedAtDesc(
            Long testId, Long userId, String skill);

    List<PracticeAttempt> findByTestIdAndUserIdOrderByCreatedAtDesc(
            Long testId, Long userId);

    List<PracticeAttempt> findBySetIdAndUserIdOrderByCreatedAtDesc(
            Long setId, Long userId);

    List<PracticeAttempt> findBySetIdAndUserIdOrderByCreatedAtDescIdDesc(
            Long setId, Long userId);

    List<PracticeAttempt> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    List<PracticeAttempt> findTop100ByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
            Long userId, String status);

    List<PracticeAttempt> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PracticeAttempt> findByUserIdAndStatusNotOrderByCreatedAtDesc(
            Long userId, String status);

    Optional<PracticeAttempt> findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(
            Long userId, Long testId, Long sectionId, String status);

    boolean existsBySetId(Long setId);

    boolean existsByPublishedVersionIdAndUserId(Long publishedVersionId, Long userId);

    @Query(value = "SELECT id FROM practice_attempts WHERE set_id = :setId ORDER BY id LIMIT 1 FOR SHARE",
            nativeQuery = true)
    Optional<Long> findFirstIdBySetIdForShare(@Param("setId") Long setId);

    @Query(value = "SELECT id FROM practice_attempts " +
            "WHERE set_id = :setId AND (published_version_id IS NULL " +
            "OR set_version_id IS NULL OR test_version_id IS NULL " +
            "OR section_version_id IS NULL) ORDER BY id LIMIT 1 FOR SHARE",
            nativeQuery = true)
    Optional<Long> findFirstUnversionedIdBySetIdForShare(@Param("setId") Long setId);
}
