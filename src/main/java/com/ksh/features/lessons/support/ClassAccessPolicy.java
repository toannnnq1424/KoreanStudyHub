package com.ksh.features.lessons.support;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Enrollment;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.classes.service.ClassRoleAccessPolicy;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Shared class-access policy for student-facing lesson surfaces (lessons list,
 * lesson detail, comments). Centralizes the "who may view/moderate a class's
 * lesson thread" rule so callers no longer repeat it inline.
 *
 * <p>The rule: ADMIN bypasses enrollment globally, a LEADER bypasses enrollment
 * only inside their resolved department, the owning lecturer passes, and
 * otherwise an ACTIVE enrollment is required. Any other caller collapses to a
 * single {@link EntityNotFoundException} with the canonical message so class
 * existence is never leaked.
 *
 * <p>This is intentionally separate from {@link LessonAccessResolver}, which
 * owns only lesson resolution + the PUBLISHED gate; access policy stays here so
 * each caller can order the two gates as its no-leak contract requires.
 */
@Component
public class ClassAccessPolicy {

    private static final String NF_MSG = "Class not found or not accessible";

    private final EnrollmentRepository enrollmentRepository;
    private final ClassRoleAccessPolicy classRoleAccessPolicy;

    public ClassAccessPolicy(EnrollmentRepository enrollmentRepository,
                             ClassRoleAccessPolicy classRoleAccessPolicy) {
        this.enrollmentRepository = enrollmentRepository;
        this.classRoleAccessPolicy = classRoleAccessPolicy;
    }

    /** ADMIN is global; LEADER is privileged only inside their department. */
    public boolean isPrivileged(ClassEntity clazz, Long userId, Role role) {
        return role == Role.ADMIN
                || (role == Role.LEADER && classRoleAccessPolicy.canAccess(clazz, userId, role));
    }

    /** A moderator is the owning lecturer or an in-scope ADMIN/LEADER. */
    public boolean isModerator(ClassEntity clazz, Long userId, Role role) {
        return clazz.getLecturerId().equals(userId) || isPrivileged(clazz, userId, role);
    }

    /**
     * Admits the caller to the given live class or throws a no-leak 404.
     * ADMIN and department-scoped LEADER callers bypass enrollment so they can
     * moderate the thread; the owning lecturer passes, otherwise an ACTIVE
     * enrollment is required.
     *
     * @throws EntityNotFoundException when no rule admits the caller
     */
    public void requireModeratorOrEnrolled(ClassEntity clazz, Long userId, Role role) {
        // ADMIN is global; LEADER may open a live class only in their department.
        if (isPrivileged(clazz, userId, role)) {
            return;
        }
        boolean lecturer = clazz.getLecturerId().equals(userId);
        boolean enrolled = enrollmentRepository
                .findByUserIdAndClassId(userId, clazz.getId())
                .filter(e -> Enrollment.STATUS_ACTIVE.equals(e.getStatus()))
                .isPresent();
        if (!lecturer && !enrolled) {
            throw new EntityNotFoundException(NF_MSG);
        }
    }
}
