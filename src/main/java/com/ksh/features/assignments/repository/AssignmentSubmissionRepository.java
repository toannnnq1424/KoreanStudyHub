package com.ksh.features.assignments.repository;

import com.ksh.features.assignments.entity.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link AssignmentSubmission} entities.
 *
 * <p>The DB UNIQUE index on (assignment_id, user_id) is exploited by the
 * one-shot submission guard: find under lock before the only allowed insert.
 */
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {

    /**
     * Finds the single submission for a given (assignment, student) pair.
     * Used for the one-shot guard and "view own submission" screens.
     *
     * @param assignmentId the assignment id
     * @param userId       the student's user id
     * @return the submission, or empty if none yet
     */
    Optional<AssignmentSubmission> findByAssignmentIdAndUserId(Long assignmentId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AssignmentSubmission s WHERE s.assignmentId = :assignmentId AND s.userId = :userId")
    Optional<AssignmentSubmission> findByAssignmentIdAndUserIdForUpdate(
            @Param("assignmentId") Long assignmentId,
            @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AssignmentSubmission s WHERE s.id = :id")
    Optional<AssignmentSubmission> findByIdForUpdate(@Param("id") Long id);

    /**
     * Returns all submissions for a given assignment. Used by the lecturer's
     * submissions list.
     *
     * @param assignmentId the assignment id
     * @return all submissions for that assignment
     */
    @Query("SELECT s FROM AssignmentSubmission s WHERE s.assignmentId = :assignmentId ORDER BY s.submittedAt DESC")
    List<AssignmentSubmission> findAllByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Query("SELECT s FROM AssignmentSubmission s WHERE s.assignmentId IN :assignmentIds")
    List<AssignmentSubmission> findAllByAssignmentIds(@Param("assignmentIds") List<Long> assignmentIds);

    /** Counts submissions for an assignment (list row badge; avoids loading entities). */
    long countByAssignmentId(Long assignmentId);
}
