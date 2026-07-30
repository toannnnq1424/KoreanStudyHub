package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, Long> {

    interface ProgressAllTimeProjection {
        Long getActivityCount();
        Long getCompletedCount();
        Long getInProgressCount();
        Long getOtherCount();
        Long getValidDurationCount();
        Long getExcludedDurationCount();
        Long getTotalValidMinutes();
        LocalDateTime getObservedFrom();
        LocalDateTime getObservedTo();
        LocalDateTime getAsOf();
    }

    interface ProgressSkillProjection {
        String getSkill();
        Long getActivityCount();
        Long getCompletedCount();
        Long getInProgressCount();
        Long getOtherCount();
        Long getEligibleScoreCount();
        Long getExcludedScoreCount();
        BigDecimal getEarnedPoints();
        BigDecimal getPossiblePoints();
        LocalDateTime getObservedFrom();
        LocalDateTime getObservedTo();
        LocalDateTime getAsOf();
    }

    interface GlobalResumeProjection {
        Long getAttemptId();
        Long getSetId();
        Long getTestId();
        Long getSectionId();
        String getSetTitle();
        String getTestTitle();
        String getSkill();
        LocalDateTime getActivityAt();
    }

    interface CatalogCompletedSectionProjection {
        Long getSetId();
        Long getSectionId();
    }

    interface CatalogAttemptStateProjection {
        Long getAttemptId();
        Long getSetId();
        Integer getStatePriority();
    }

    Optional<PracticeAttempt> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PracticeAttempt a where a.id = :id and a.userId = :userId")
    Optional<PracticeAttempt> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PracticeAttempt a where a.id = :id")
    Optional<PracticeAttempt> findByIdForUpdate(@Param("id") Long id);

    List<PracticeAttempt> findByTestIdAndUserIdAndSkillOrderByCreatedAtDesc(
            Long testId, Long userId, String skill);

    List<PracticeAttempt> findByTestIdAndUserIdOrderByCreatedAtDesc(
            Long testId, Long userId);

    List<PracticeAttempt> findBySetIdAndUserIdOrderByCreatedAtDesc(
            Long setId, Long userId);

    List<PracticeAttempt> findBySetIdAndUserIdOrderByCreatedAtDescIdDesc(
            Long setId, Long userId);

    /**
     * Canonical all-time progress counts and duration coverage. Duration is
     * eligible only for a completed attempt with a positive value below four
     * hours; invalid evidence is counted as excluded and is never substituted.
     */
    @Query(value = """
            SELECT
                COUNT(*) AS activityCount,
                COALESCE(SUM(CASE WHEN a.status IN ('SUBMITTED', 'GRADED') THEN 1 ELSE 0 END), 0)
                    AS completedCount,
                COALESCE(SUM(CASE
                    WHEN a.status = 'IN_PROGRESS'
                     AND ppv.id IS NOT NULL
                     AND psv.id IS NOT NULL
                     AND ptv.id IS NOT NULL
                     AND pscv.id IS NOT NULL
                     AND (
                         a.version_compatibility_status IS NULL
                         OR TRIM(a.version_compatibility_status) = ''
                         OR UPPER(TRIM(a.version_compatibility_status)) = 'COMPATIBLE'
                     )
                    THEN 1 ELSE 0 END), 0)
                    AS inProgressCount,
                COALESCE(SUM(CASE
                    WHEN a.status NOT IN ('IN_PROGRESS', 'SUBMITTED', 'GRADED')
                      OR (
                          a.status = 'IN_PROGRESS'
                          AND (
                              ppv.id IS NULL
                              OR psv.id IS NULL
                              OR ptv.id IS NULL
                              OR pscv.id IS NULL
                              OR (
                                  a.version_compatibility_status IS NOT NULL
                                  AND TRIM(a.version_compatibility_status) <> ''
                                  AND UPPER(TRIM(a.version_compatibility_status)) <> 'COMPATIBLE'
                              )
                          )
                      )
                    THEN 1 ELSE 0 END), 0)
                    AS otherCount,
                COALESCE(SUM(CASE
                    WHEN a.status IN ('SUBMITTED', 'GRADED')
                     AND a.started_at IS NOT NULL
                     AND COALESCE(a.submitted_at, a.updated_at, a.created_at) > a.started_at
                     AND TIMESTAMPDIFF(
                         MINUTE, a.started_at, COALESCE(a.submitted_at, a.updated_at, a.created_at)
                     ) BETWEEN 1 AND 239
                    THEN 1 ELSE 0 END), 0) AS validDurationCount,
                COALESCE(SUM(CASE
                    WHEN a.status IN ('SUBMITTED', 'GRADED')
                     AND NOT (
                         a.started_at IS NOT NULL
                         AND COALESCE(a.submitted_at, a.updated_at, a.created_at) > a.started_at
                         AND TIMESTAMPDIFF(
                             MINUTE, a.started_at, COALESCE(a.submitted_at, a.updated_at, a.created_at)
                         ) BETWEEN 1 AND 239
                     )
                    THEN 1 ELSE 0 END), 0) AS excludedDurationCount,
                COALESCE(SUM(CASE
                    WHEN a.status IN ('SUBMITTED', 'GRADED')
                     AND a.started_at IS NOT NULL
                     AND COALESCE(a.submitted_at, a.updated_at, a.created_at) > a.started_at
                     AND TIMESTAMPDIFF(
                         MINUTE, a.started_at, COALESCE(a.submitted_at, a.updated_at, a.created_at)
                     ) BETWEEN 1 AND 239
                    THEN TIMESTAMPDIFF(
                         MINUTE, a.started_at, COALESCE(a.submitted_at, a.updated_at, a.created_at)
                     )
                    ELSE 0 END), 0) AS totalValidMinutes,
                MIN(COALESCE(a.submitted_at, a.updated_at, a.created_at)) AS observedFrom,
                MAX(COALESCE(a.submitted_at, a.updated_at, a.created_at)) AS observedTo,
                CURRENT_TIMESTAMP AS asOf
            FROM practice_attempts a
            LEFT JOIN practice_published_versions ppv
              ON ppv.id = a.published_version_id
             AND ppv.set_id = a.set_id
            LEFT JOIN practice_set_versions psv
              ON psv.id = a.set_version_id
             AND psv.published_version_id = ppv.id
             AND psv.set_id = a.set_id
            LEFT JOIN practice_test_versions ptv
              ON ptv.id = a.test_version_id
             AND ptv.published_version_id = ppv.id
             AND ptv.set_version_id = psv.id
             AND ptv.test_id = a.test_id
            LEFT JOIN practice_section_versions pscv
              ON pscv.id = a.section_version_id
             AND pscv.published_version_id = ppv.id
             AND pscv.test_version_id = ptv.id
             AND pscv.section_id = a.section_id
             AND pscv.skill = a.skill
            WHERE a.user_id = :userId
              AND a.status <> :discardedStatus
            """, nativeQuery = true)
    ProgressAllTimeProjection findProgressAllTime(
            @Param("userId") Long userId,
            @Param("discardedStatus") String discardedStatus);

    /**
     * All-time per-skill activity plus Objective earned/possible evidence.
     * Writing task cohorts are assembled from immutable question-version
     * evidence by PracticeProgressService; Speaking numeric evidence is
     * intentionally ineligible.
     */
    @Query(value = """
            SELECT
                a.skill AS skill,
                COUNT(*) AS activityCount,
                COALESCE(SUM(CASE
                    WHEN a.status IN ('SUBMITTED', 'GRADED') THEN 1 ELSE 0 END), 0)
                    AS completedCount,
                COALESCE(SUM(CASE
                    WHEN a.status = 'IN_PROGRESS'
                     AND ppv.id IS NOT NULL
                     AND psv.id IS NOT NULL
                     AND ptv.id IS NOT NULL
                     AND pscv.id IS NOT NULL
                     AND (
                         a.version_compatibility_status IS NULL
                         OR TRIM(a.version_compatibility_status) = ''
                         OR UPPER(TRIM(a.version_compatibility_status)) = 'COMPATIBLE'
                     )
                    THEN 1 ELSE 0 END), 0)
                    AS inProgressCount,
                COALESCE(SUM(CASE
                    WHEN a.status NOT IN ('IN_PROGRESS', 'SUBMITTED', 'GRADED')
                      OR (
                          a.status = 'IN_PROGRESS'
                          AND (
                              ppv.id IS NULL
                              OR psv.id IS NULL
                              OR ptv.id IS NULL
                              OR pscv.id IS NULL
                              OR (
                                  a.version_compatibility_status IS NOT NULL
                                  AND TRIM(a.version_compatibility_status) <> ''
                                  AND UPPER(TRIM(a.version_compatibility_status)) <> 'COMPATIBLE'
                              )
                          )
                      )
                    THEN 1 ELSE 0 END), 0)
                    AS otherCount,
                COALESCE(SUM(CASE
                    WHEN a.skill IN ('READING', 'LISTENING')
                     AND a.status IN ('SUBMITTED', 'GRADED')
                     AND ppv.id IS NOT NULL
                     AND psv.id IS NOT NULL
                     AND ptv.id IS NOT NULL
                     AND pscv.id IS NOT NULL
                     AND a.score_unit = 'EARNED_POINTS'
                     AND a.earned_points IS NOT NULL
                     AND a.total_points IS NOT NULL
                     AND a.total_points > 0
                    THEN 1 ELSE 0 END), 0) AS eligibleScoreCount,
                COALESCE(SUM(CASE
                    WHEN a.skill IN ('READING', 'LISTENING')
                     AND a.status IN ('SUBMITTED', 'GRADED')
                     AND (
                         ppv.id IS NULL
                         OR psv.id IS NULL
                         OR ptv.id IS NULL
                         OR pscv.id IS NULL
                         OR a.score_unit IS NULL
                         OR a.score_unit <> 'EARNED_POINTS'
                         OR a.earned_points IS NULL
                         OR a.total_points IS NULL
                         OR a.total_points <= 0
                     )
                    THEN 1 ELSE 0 END), 0) AS excludedScoreCount,
                SUM(CASE
                    WHEN a.skill IN ('READING', 'LISTENING')
                     AND a.status IN ('SUBMITTED', 'GRADED')
                     AND ppv.id IS NOT NULL
                     AND psv.id IS NOT NULL
                     AND ptv.id IS NOT NULL
                     AND pscv.id IS NOT NULL
                     AND a.score_unit = 'EARNED_POINTS'
                     AND a.earned_points IS NOT NULL
                     AND a.total_points IS NOT NULL
                     AND a.total_points > 0
                    THEN a.earned_points ELSE NULL END) AS earnedPoints,
                SUM(CASE
                    WHEN a.skill IN ('READING', 'LISTENING')
                     AND a.status IN ('SUBMITTED', 'GRADED')
                     AND ppv.id IS NOT NULL
                     AND psv.id IS NOT NULL
                     AND ptv.id IS NOT NULL
                     AND pscv.id IS NOT NULL
                     AND a.score_unit = 'EARNED_POINTS'
                     AND a.earned_points IS NOT NULL
                     AND a.total_points IS NOT NULL
                     AND a.total_points > 0
                    THEN a.total_points ELSE NULL END) AS possiblePoints,
                MIN(COALESCE(a.submitted_at, a.updated_at, a.created_at)) AS observedFrom,
                MAX(COALESCE(a.submitted_at, a.updated_at, a.created_at)) AS observedTo,
                CURRENT_TIMESTAMP AS asOf
            FROM practice_attempts a
            LEFT JOIN practice_published_versions ppv
              ON ppv.id = a.published_version_id
             AND ppv.set_id = a.set_id
            LEFT JOIN practice_set_versions psv
              ON psv.id = a.set_version_id
             AND psv.published_version_id = ppv.id
             AND psv.set_id = a.set_id
            LEFT JOIN practice_test_versions ptv
              ON ptv.id = a.test_version_id
             AND ptv.published_version_id = ppv.id
             AND ptv.set_version_id = psv.id
             AND ptv.test_id = a.test_id
            LEFT JOIN practice_section_versions pscv
              ON pscv.id = a.section_version_id
             AND pscv.published_version_id = ppv.id
             AND pscv.test_version_id = ptv.id
             AND pscv.section_id = a.section_id
             AND pscv.skill = a.skill
            WHERE a.user_id = :userId
              AND a.status <> :discardedStatus
            GROUP BY a.skill
            """, nativeQuery = true)
    List<ProgressSkillProjection> findProgressAllTimeBySkill(
            @Param("userId") Long userId,
            @Param("discardedStatus") String discardedStatus);

    @Query("""
            select a
            from PracticeAttempt a
            where a.userId = :userId
              and a.status <> :discardedStatus
            order by coalesce(a.submittedAt, a.updatedAt, a.createdAt) desc, a.id desc
            """)
    List<PracticeAttempt> findRecentProgressAttempts(
            @Param("userId") Long userId,
            @Param("discardedStatus") String discardedStatus,
            Pageable pageable);

    @Query(value = """
            SELECT a.*
            FROM practice_attempts a
            WHERE a.user_id = :userId
              AND a.skill = 'WRITING'
              AND a.status <> :discardedStatus
            ORDER BY a.activity_at DESC, a.id DESC
            """, nativeQuery = true)
    List<PracticeAttempt> findProgressWritingAttempts(
            @Param("userId") Long userId,
            @Param("discardedStatus") String discardedStatus,
            Pageable pageable);

    /**
     * Completed-section evidence for the current catalog page. Passing only
     * current-page section ids bounds the result cardinality to that page
     * regardless of the learner's retained attempt history.
     */
    @Query(value = """
            SELECT DISTINCT
                a.set_id AS setId,
                a.section_id AS sectionId
            FROM practice_attempts a
            WHERE a.user_id = :userId
              AND a.section_id IN (:sectionIds)
              AND a.status IN ('SUBMITTED', 'GRADED')
            """, nativeQuery = true)
    List<CatalogCompletedSectionProjection> findCatalogCompletedSections(
            @Param("userId") Long userId,
            @Param("sectionIds") List<Long> sectionIds);

    /**
     * One lifecycle candidate per current-page set. Priority preserves the
     * catalog contract: canonical resumable first, then restart-required
     * in-progress, then the newest terminal/other non-discarded attempt.
     * The windowed projection returns at most one id for each requested set.
     */
    @Query(value = """
            WITH catalog_candidates AS (
                SELECT
                    a.id AS attempt_id,
                    a.set_id,
                    CASE
                        WHEN a.status = 'IN_PROGRESS'
                         AND ppv.id IS NOT NULL
                         AND psv.id IS NOT NULL
                         AND ptv.id IS NOT NULL
                         AND pscv.id IS NOT NULL
                         AND (
                             a.version_compatibility_status IS NULL
                             OR TRIM(a.version_compatibility_status) = ''
                             OR UPPER(TRIM(a.version_compatibility_status)) = 'COMPATIBLE'
                         )
                        THEN 0
                        WHEN a.status = 'IN_PROGRESS' THEN 1
                        ELSE 2
                    END AS state_priority,
                    a.activity_at
                FROM practice_attempts a
                LEFT JOIN practice_published_versions ppv
                  ON ppv.id = a.published_version_id
                 AND ppv.set_id = a.set_id
                LEFT JOIN practice_set_versions psv
                  ON psv.id = a.set_version_id
                 AND psv.published_version_id = ppv.id
                 AND psv.set_id = a.set_id
                LEFT JOIN practice_test_versions ptv
                  ON ptv.id = a.test_version_id
                 AND ptv.published_version_id = ppv.id
                 AND ptv.set_version_id = psv.id
                 AND ptv.test_id = a.test_id
                LEFT JOIN practice_section_versions pscv
                  ON pscv.id = a.section_version_id
                 AND pscv.published_version_id = ppv.id
                 AND pscv.test_version_id = ptv.id
                 AND pscv.section_id = a.section_id
                 AND pscv.skill = a.skill
                WHERE a.user_id = :userId
                  AND a.set_id IN (:setIds)
                  AND a.status <> :discardedStatus
            ),
            catalog_ranked AS (
                SELECT
                    candidate.*,
                    ROW_NUMBER() OVER (
                        PARTITION BY candidate.set_id
                        ORDER BY
                            candidate.state_priority ASC,
                            candidate.activity_at DESC,
                            candidate.attempt_id DESC
                    ) AS candidate_row
                FROM catalog_candidates candidate
            )
            SELECT
                ranked.attempt_id AS attemptId,
                ranked.set_id AS setId,
                ranked.state_priority AS statePriority
            FROM catalog_ranked ranked
            WHERE ranked.candidate_row = 1
            """, nativeQuery = true)
    List<CatalogAttemptStateProjection> findCatalogAttemptStateCandidates(
            @Param("userId") Long userId,
            @Param("setIds") List<Long> setIds,
            @Param("discardedStatus") String discardedStatus);

    /**
     * Bounded set-page/catalog identity batch for both resume and result-link
     * policy. It returns only active attempts whose four immutable levels and
     * source set/test/section/skill identity are coherent and compatible.
     */
    @Query(value = """
            SELECT a.id
            FROM practice_attempts a
            JOIN practice_sets s
              ON s.id = a.set_id
             AND s.status = 'PUBLISHED'
             AND s.is_deleted = 0
            JOIN practice_published_versions ppv
              ON ppv.id = a.published_version_id
             AND ppv.set_id = a.set_id
            JOIN practice_set_versions psv
              ON psv.id = a.set_version_id
             AND psv.published_version_id = ppv.id
             AND psv.set_id = a.set_id
            JOIN practice_test_versions ptv
              ON ptv.id = a.test_version_id
             AND ptv.published_version_id = ppv.id
             AND ptv.set_version_id = psv.id
             AND ptv.test_id = a.test_id
            JOIN practice_section_versions pscv
              ON pscv.id = a.section_version_id
             AND pscv.published_version_id = ppv.id
             AND pscv.test_version_id = ptv.id
             AND pscv.section_id = a.section_id
             AND pscv.skill = a.skill
            WHERE a.user_id = :userId
              AND a.set_id IN (:setIds)
              AND a.status <> 'DISCARDED'
              AND (
                    a.version_compatibility_status IS NULL
                    OR TRIM(a.version_compatibility_status) = ''
                    OR UPPER(TRIM(a.version_compatibility_status)) = 'COMPATIBLE'
                  )
            """, nativeQuery = true)
    List<Long> findCoherentAttemptIdentityIds(
            @Param("userId") Long userId,
            @Param("setIds") List<Long> setIds);

    /**
     * Resume candidate for the standalone public Practice catalogue. Classroom
     * membership is deliberately absent from this authorization boundary.
     */
    @Query(value = """
            SELECT
                a.id AS attemptId,
                a.set_id AS setId,
                a.test_id AS testId,
                a.section_id AS sectionId,
                psv.title AS setTitle,
                ptv.title AS testTitle,
                pscv.skill AS skill,
                COALESCE(a.submitted_at, a.updated_at, a.created_at) AS activityAt
            FROM practice_attempts a
            JOIN practice_sets s
              ON s.id = a.set_id
             AND s.status = 'PUBLISHED'
             AND s.is_deleted = 0
             AND s.scope = 'GLOBAL'
            JOIN practice_published_versions ppv
              ON ppv.id = a.published_version_id
             AND ppv.set_id = a.set_id
            JOIN practice_set_versions psv
              ON psv.id = a.set_version_id
             AND psv.published_version_id = ppv.id
             AND psv.set_id = a.set_id
            JOIN practice_test_versions ptv
              ON ptv.id = a.test_version_id
             AND ptv.published_version_id = ppv.id
             AND ptv.set_version_id = psv.id
             AND ptv.test_id = a.test_id
            JOIN practice_section_versions pscv
              ON pscv.id = a.section_version_id
             AND pscv.published_version_id = ppv.id
             AND pscv.test_version_id = ptv.id
             AND pscv.section_id = a.section_id
             AND pscv.skill = a.skill
            WHERE a.user_id = :userId
              AND a.status = 'IN_PROGRESS'
              AND a.deadline_at IS NOT NULL
              AND a.deadline_at > :now
              AND (
                    a.version_compatibility_status IS NULL
                    OR TRIM(a.version_compatibility_status) = ''
                    OR UPPER(TRIM(a.version_compatibility_status)) = 'COMPATIBLE'
                  )
            ORDER BY
                COALESCE(a.submitted_at, a.updated_at, a.created_at) DESC,
                a.id DESC
            """, nativeQuery = true)
    List<GlobalResumeProjection> findGlobalCatalogResumeCandidates(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /**
     * Legacy class-aware resume lookup retained for compatibility outside the
     * standalone catalogue.
     */
    @Query(value = """
            SELECT
                a.id AS attemptId,
                a.set_id AS setId,
                a.test_id AS testId,
                a.section_id AS sectionId,
                psv.title AS setTitle,
                ptv.title AS testTitle,
                pscv.skill AS skill,
                COALESCE(a.submitted_at, a.updated_at, a.created_at) AS activityAt
            FROM practice_attempts a
            JOIN practice_sets s
              ON s.id = a.set_id
             AND s.status = 'PUBLISHED'
             AND s.is_deleted = 0
            JOIN practice_published_versions ppv
              ON ppv.id = a.published_version_id
             AND ppv.set_id = a.set_id
            JOIN practice_set_versions psv
              ON psv.id = a.set_version_id
             AND psv.published_version_id = ppv.id
             AND psv.set_id = a.set_id
            JOIN practice_test_versions ptv
              ON ptv.id = a.test_version_id
             AND ptv.published_version_id = ppv.id
             AND ptv.set_version_id = psv.id
             AND ptv.test_id = a.test_id
            JOIN practice_section_versions pscv
              ON pscv.id = a.section_version_id
             AND pscv.published_version_id = ppv.id
             AND pscv.test_version_id = ptv.id
             AND pscv.section_id = a.section_id
             AND pscv.skill = a.skill
            WHERE a.user_id = :userId
              AND a.status = 'IN_PROGRESS'
              AND a.deadline_at IS NOT NULL
              AND a.deadline_at > :now
              AND (
                    a.version_compatibility_status IS NULL
                    OR TRIM(a.version_compatibility_status) = ''
                    OR UPPER(TRIM(a.version_compatibility_status)) = 'COMPATIBLE'
                  )
              AND (
                    s.scope = 'GLOBAL'
                    OR s.created_by = :userId
                    OR (s.scope = 'CLASS' AND s.class_id IN (:activeClassIds))
                  )
            ORDER BY
                COALESCE(a.submitted_at, a.updated_at, a.created_at) DESC,
                a.id DESC
            """, nativeQuery = true)
    List<GlobalResumeProjection> findGlobalResumeCandidates(
            @Param("userId") Long userId,
            @Param("activeClassIds") List<Long> activeClassIds,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query(value = """
            SELECT a.id
            FROM practice_attempts a
            WHERE a.status = 'IN_PROGRESS'
              AND a.deadline_at IS NOT NULL
              AND a.deadline_at <= :now
              AND a.deadline_reconcile_quarantined_at IS NULL
              AND (
                    a.deadline_reconcile_next_at IS NULL
                    OR a.deadline_reconcile_next_at <= :now
                  )
            ORDER BY a.deadline_at ASC, a.id ASC
            """, nativeQuery = true)
    List<Long> findExpiredInProgressAttemptIds(
            @Param("now") LocalDateTime now,
            Pageable pageable);

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
