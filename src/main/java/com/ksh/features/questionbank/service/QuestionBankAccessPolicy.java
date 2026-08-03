package com.ksh.features.questionbank.service;

import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.leader.service.LeaderDepartmentResolver;
import com.ksh.security.Role;
import org.springframework.stereotype.Component;

/**
 * Resolves subject-scoped access for question bank actions.
 * Subject catalog rows are backed by the compatibility {@code departments} table.
 */
@Component
public class QuestionBankAccessPolicy {

    private final LeaderDepartmentResolver leaderDepartmentResolver;

    public QuestionBankAccessPolicy(LeaderDepartmentResolver leaderDepartmentResolver) {
        this.leaderDepartmentResolver = leaderDepartmentResolver;
    }

    /** Resolves the caller's working subject for question bank access. */
    public Long resolveSubjectId(User user) {
        if (user == null) {
            return null;
        }
        if (user.getRole() == Role.LEADER) {
            Department department = leaderDepartmentResolver.resolve(user.getId()).orElse(null);
            return department != null ? department.getId() : null;
        }
        if (user.getRole() == Role.LECTURER) {
            return user.getDepartmentId();
        }
        if (user.getRole() == Role.ADMIN) {
            return user.getDepartmentId();
        }
        return null;
    }

    /** True when the caller may view or contribute within the given subject. */
    public boolean canAccessSubject(User user, Long subjectId) {
        if (user == null || subjectId == null) {
            return false;
        }
        Role role = user.getRole();
        if (role != Role.LEADER && role != Role.LECTURER && role != Role.ADMIN) {
            return false;
        }
        Long resolvedSubjectId = resolveSubjectId(user);
        return subjectId.equals(resolvedSubjectId);
    }

    /** True when the caller may curate shared inventory for the given subject. */
    public boolean canCurateSubject(User user, Long subjectId) {
        if (user == null || subjectId == null) {
            return false;
        }
        Role role = user.getRole();
        if (role != Role.LEADER && role != Role.ADMIN) {
            return false;
        }
        Long resolvedSubjectId = resolveSubjectId(user);
        return subjectId.equals(resolvedSubjectId);
    }
}
