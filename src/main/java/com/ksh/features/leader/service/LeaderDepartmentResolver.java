package com.ksh.features.leader.service;

import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

/**
 * Resolves the working department for a LEADER user.
 *
 * <p>Preference order: department where {@code leader_user_id} matches the user,
 * otherwise the department referenced by {@code users.subject_id} when it
 * exists (and preferably is active).
 */
@Service
public class LeaderDepartmentResolver {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public LeaderDepartmentResolver(DepartmentRepository departmentRepository,
                                  UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    /**
     * @param userId current authenticated LEADER user id
     * @return resolved department, or empty when neither rule matches
     */
    @Transactional(readOnly = true)
    public Optional<Department> resolve(Long userId) {
        return resolveAll(userId).stream().findFirst();
    }

    /** All active subject catalog rows curated by this leader account. */
    @Transactional(readOnly = true)
    public List<Department> resolveAll(Long userId) {
        List<Department> assigned = departmentRepository
                .findByLeaderUserIdOrderByCodeAsc(userId).stream()
                .filter(Department::isActive)
                .toList();
        if (!assigned.isEmpty()) return assigned;
        return userRepository.findById(userId)
                .map(User::getSubjectId)
                .filter(id -> id != null)
                .flatMap(departmentRepository::findById)
                .filter(Department::isActive)
                .map(List::of)
                .orElseGet(List::of);
    }
}
