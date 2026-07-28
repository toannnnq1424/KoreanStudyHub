package com.ksh.features.classes.service;

import com.ksh.entities.ClassEntity;
import com.ksh.features.leader.service.LeaderDepartmentResolver;
import com.ksh.security.Role;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Canonical role-to-class scope policy. ADMIN is global, LECTURER is
 * owner-scoped, and LEADER is restricted to the department resolved for that
 * leader account.
 */
@Component
public class ClassRoleAccessPolicy {

    private final LeaderDepartmentResolver leaderDepartmentResolver;

    public ClassRoleAccessPolicy(LeaderDepartmentResolver leaderDepartmentResolver) {
        this.leaderDepartmentResolver = leaderDepartmentResolver;
    }

    public boolean canAccess(ClassEntity clazz, Long userId, Role role) {
        if (clazz == null || userId == null || role == null) {
            return false;
        }
        if (role == Role.ADMIN) {
            return true;
        }
        if (role == Role.LECTURER) {
            return userId.equals(clazz.getLecturerId());
        }
        if (role == Role.LEADER) {
            return leaderDepartmentId(userId)
                    .filter(id -> id.equals(clazz.getDepartmentId()))
                    .isPresent();
        }
        return false;
    }

    public Optional<Long> leaderDepartmentId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return leaderDepartmentResolver.resolve(userId).map(department -> department.getId());
    }
}
