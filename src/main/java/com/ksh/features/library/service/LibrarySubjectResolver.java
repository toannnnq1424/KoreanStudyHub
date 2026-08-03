package com.ksh.features.library.service;

import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.leader.service.LeaderDepartmentResolver;
import com.ksh.security.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Resolves the active subject catalog row backing a lecturer's Library. */
@Component
public class LibrarySubjectResolver {

    private final UserRepository userRepository;
    private final DepartmentRepository subjectRepository;
    private final LeaderDepartmentResolver leaderResolver;

    public LibrarySubjectResolver(UserRepository userRepository,
                                  DepartmentRepository subjectRepository,
                                  LeaderDepartmentResolver leaderResolver) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.leaderResolver = leaderResolver;
    }

    public Department require(Long userId, Role role) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("Bạn chưa được gán mã môn"));
        if (actor.getRole() != role) {
            throw new AccessDeniedException("Bạn chưa được gán mã môn");
        }
        Long subjectId = role == Role.LEADER
                ? leaderResolver.resolve(userId).map(Department::getId).orElse(null)
                : actor.getDepartmentId();
        if (subjectId == null) {
            throw new AccessDeniedException("Bạn chưa được gán mã môn");
        }
        return subjectRepository.findById(subjectId)
                .filter(Department::isActive)
                .orElseThrow(() -> new AccessDeniedException("Mã môn không hoạt động"));
    }
}
