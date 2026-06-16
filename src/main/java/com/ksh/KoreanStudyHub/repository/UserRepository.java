package com.ksh.KoreanStudyHub.repository;

import com.ksh.KoreanStudyHub.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);
    List<User> findByRoleRoleName(String roleName);
    long countByStatus(String status);
    long countByRoleRoleName(String roleName);
}
