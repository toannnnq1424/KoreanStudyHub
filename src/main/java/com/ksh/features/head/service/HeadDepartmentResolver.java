package com.ksh.features.head.service;

import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves the working department for a HEAD user.
 *
 * <p>Preference order: department where {@code head_user_id} matches the user,
 * otherwise the department referenced by {@code users.department_id} when it
 * exists (and preferably is active).
 */
@Service
public class HeadDepartmentResolver {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public HeadDepartmentResolver(DepartmentRepository departmentRepository,
                                  UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    /**
     * @param userId current authenticated HEAD user id
     * @return resolved department, or empty when neither rule matches
     */
    @Transactional(readOnly = true)
    public Optional<Department> resolve(Long userId) {
        Optional<Department> asHead = departmentRepository.findFirstByHeadUserId(userId);
        if (asHead.isPresent()) {
            return asHead;
        }
        return userRepository.findById(userId)
                .map(User::getDepartmentId)
                .filter(id -> id != null)
                .flatMap(departmentRepository::findById);
    }
}
