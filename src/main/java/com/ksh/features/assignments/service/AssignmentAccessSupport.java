package com.ksh.features.assignments.service;

import com.ksh.features.assignments.dto.AssignmentDtos.AssignmentForm;
import com.ksh.features.assignments.dto.AssignmentDtos.AssignmentRow;
import com.ksh.features.assignments.entity.Assignment;
import com.ksh.features.assignments.repository.AssignmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.classes.service.ClassRoleAccessPolicy;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.ksh.common.IConstant.MSG_ASSIGNMENT_MAX_SCORE_NEGATIVE;
import static com.ksh.common.IConstant.MSG_ASSIGNMENT_MAX_SCORE_INVALID;
import static com.ksh.common.IConstant.MSG_ASSIGNMENT_NOT_FOUND;
import static com.ksh.common.IConstant.MSG_ASSIGNMENT_TITLE_BLANK;
import static com.ksh.common.IConstant.MSG_ASSIGNMENT_TITLE_TOO_LONG;
import static com.ksh.common.IConstant.MSG_GRADE_SCORE_FORMAT_INVALID;
import static com.ksh.common.IConstant.MSG_GRADE_SCORE_INVALID;
import static com.ksh.common.IConstant.MSG_NOT_ENROLLED;

/**
 * Shared assignment access, mapping, and form helpers used by lecturer and
 * student assignment services. Keeps ownership/enrollment checks in one place.
 */
@Component
public class AssignmentAccessSupport {

    private static final int ASSIGNMENT_TITLE_MAX_LENGTH = 300;
    private static final int SCORE_MAX_SCALE = 2;
    private static final BigDecimal SCORE_COLUMN_MAX = new BigDecimal("999.99");

    private final AssignmentRepository assignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final ClassRoleAccessPolicy classAccessPolicy;

    public AssignmentAccessSupport(AssignmentRepository assignmentRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   UserRepository userRepository,
                                   ClassRepository classRepository,
                                   ClassRoleAccessPolicy classAccessPolicy) {
        this.assignmentRepository = assignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.classRepository = classRepository;
        this.classAccessPolicy = classAccessPolicy;
    }

    /**
     * Verifies the caller owns (or has admin access to) the class. Non-owner → 404 no-leak.
     * Mirrors the ownership check in {@code ClassesService.getEditable}.
     */
    public void requireEditableClass(Long classId, Long userId, Role role) {
        classRepository.findById(classId)
                .filter(c -> !c.isDeleted())
                .filter(c -> classAccessPolicy.canAccess(c, userId, role))
                .orElseThrow(() -> new EntityNotFoundException(MSG_ASSIGNMENT_NOT_FOUND));
    }

    /** Validates that the student is ACTIVE-enrolled in the class. */
    public void requireActiveEnrollment(Long classId, Long userId) {
        classRepository.findById(classId)
                .filter(clazz -> !clazz.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException(MSG_NOT_ENROLLED));
        enrollmentRepository.findByUserIdAndClassId(userId, classId)
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .orElseThrow(() -> new EntityNotFoundException(MSG_NOT_ENROLLED));
    }

    /** Loads a non-deleted assignment scoped to a class or throws 404. */
    public Assignment requireAssignment(Long classId, Long assignmentId) {
        return assignmentRepository.findByIdAndClassIdNotDeleted(assignmentId, classId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_ASSIGNMENT_NOT_FOUND));
    }

    /** Maps Assignment entity + submission count to an AssignmentRow DTO. */
    public AssignmentRow toRow(Assignment a, long subCount) {
        return new AssignmentRow(a.getId(), a.getTitle(), a.getStatus(),
                a.getDueDate(), a.getMaxScore(), subCount, 0);
    }

    /** Applies form fields to an Assignment entity. */
    public void applyForm(Assignment a, AssignmentForm form) {
        a.setTitle(form.title().trim());
        a.setDescription(form.description() != null ? form.description() : "");
        a.setMaxScore(form.maxScore() != null ? form.maxScore() : BigDecimal.valueOf(100));
        a.setDueDate(form.dueDate());
        a.setAllowLateSubmission(form.allowLateSubmission());
    }

    /** Validates the assignment form fields. */
    public void validateForm(AssignmentForm form) {
        if (form.title() == null || form.title().isBlank()) {
            throw new IllegalArgumentException(MSG_ASSIGNMENT_TITLE_BLANK);
        }
        if (form.title().trim().length() > ASSIGNMENT_TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException(MSG_ASSIGNMENT_TITLE_TOO_LONG);
        }
        BigDecimal maxScore = form.maxScore() != null
                ? form.maxScore()
                : BigDecimal.valueOf(100);
        if (maxScore.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(MSG_ASSIGNMENT_MAX_SCORE_NEGATIVE);
        }
        if (!fitsScoreColumn(maxScore)) {
            throw new IllegalArgumentException(MSG_ASSIGNMENT_MAX_SCORE_INVALID);
        }
    }

    /** Validates a grade against both the assignment cap and DECIMAL(5,2). */
    public void validateGradeScore(BigDecimal score, BigDecimal assignmentMaxScore) {
        if (score == null
                || assignmentMaxScore == null
                || score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(assignmentMaxScore) > 0) {
            throw new IllegalArgumentException(MSG_GRADE_SCORE_INVALID);
        }
        if (!fitsScoreColumn(score)) {
            throw new IllegalArgumentException(MSG_GRADE_SCORE_FORMAT_INVALID);
        }
    }

    private static boolean fitsScoreColumn(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() <= SCORE_MAX_SCALE
                && value.compareTo(SCORE_COLUMN_MAX) <= 0;
    }

    /** Resolves a user's display name; falls back to "Sinh viên" when not found. */
    public String resolveUserName(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getFullName())
                .orElse("Sinh viên");
    }

    /** Resolves a user's email; falls back to empty string when not found. */
    public String resolveUserEmail(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getEmail())
                .orElse("");
    }

    public AssignmentRepository assignments() {
        return assignmentRepository;
    }

    public EnrollmentRepository enrollments() {
        return enrollmentRepository;
    }
}
